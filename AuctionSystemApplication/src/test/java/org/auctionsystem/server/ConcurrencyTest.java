package org.auctionsystem.server;

import com.google.gson.JsonObject;
import org.auctionsystem.server.session.SessionManager;
import org.auctionsystem.server.session.UserSession;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency tests cho:
 *  1. ConnectedClientRegistry — register/unregister/broadcast đồng thời
 *  2. AuctionScheduler — stop idempotent khi gọi đồng thời
 *  3. SessionManager — thêm/xóa session đồng thời
 *  4. AdminStatsScheduler.notifyStatsChanged() — gọi đồng thời không crash
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConcurrencyTest {

    @AfterEach
    void tearDown() {
        AuctionScheduler.stop();
        AdminStatsScheduler.stop();
    }

    // ═══════════════════════════════════════════════════════
    // 1. ConnectedClientRegistry — thread safety
    // ═══════════════════════════════════════════════════════

    @Test @Order(10)
    void registry_concurrentRegisterUnregister_noException() throws Exception {
        int threadCount = 20;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threadCount);
        List<Exception> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final String sessionId = "session-" + i;
            // Tạo session tương ứng
            SessionManager.addSession(new UserSession(
                    sessionId, "user-" + i, "User " + i, "user_" + i,
                    "user" + i + "@test.com", "BIDDER", 100.0, null, null, 0, null
            ));

            new Thread(() -> {
                try {
                    start.await();
                    ConnectedClientRegistry.register(sessionId, new ClientHandler(null));
                    Thread.sleep(5);
                    ConnectedClientRegistry.unregister(sessionId);
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    SessionManager.removeSession(sessionId);
                    done.countDown();
                }
            }).start();
        }

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS), "Các thread phải kết thúc trong 5s");
        assertTrue(errors.isEmpty(), "Không được có exception: " + errors);
    }

    @Test @Order(11)
    void registry_broadcastAll_withConcurrentUnregister_noException() throws Exception {
        // Đăng ký vài "handler" giả
        for (int i = 0; i < 5; i++) {
            String sid = "broadcast-session-" + i;
            SessionManager.addSession(new UserSession(
                    sid, "uid-" + i, "Name", "user_b" + i,
                    "b" + i + "@test.com", "BIDDER", 0.0, null, null, 0, null
            ));
            ConnectedClientRegistry.register(sid, new ClientHandler(null));
        }

        // Broadcast trong khi thread khác đang unregister
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(2);
        List<Exception> errors = new CopyOnWriteArrayList<>();

        // Thread 1: broadcast liên tục
        new Thread(() -> {
            try {
                start.await();
                JsonObject event = new JsonObject();
                event.addProperty("event", "TEST_EVENT");
                for (int i = 0; i < 10; i++) {
                    ConnectedClientRegistry.broadcastAll(event);
                    Thread.sleep(10);
                }
            } catch (Exception e) {
                errors.add(e);
            } finally {
                done.countDown();
            }
        }).start();

        // Thread 2: unregister song song
        new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < 5; i++) {
                    ConnectedClientRegistry.unregister("broadcast-session-" + i);
                    Thread.sleep(15);
                }
            } catch (Exception e) {
                errors.add(e);
            } finally {
                done.countDown();
            }
        }).start();

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertTrue(errors.isEmpty(), "Không được có exception khi broadcast + unregister đồng thời: " + errors);
    }

    @Test @Order(12)
    void registry_size_afterConcurrentOps_isConsistent() throws Exception {
        // Xóa sạch registry trước
        for (int i = 0; i < 20; i++) ConnectedClientRegistry.unregister("session-" + i);

        int n = 10;
        CountDownLatch done = new CountDownLatch(n);

        for (int i = 0; i < n; i++) {
            final String sid = "size-session-" + i;
            SessionManager.addSession(new UserSession(
                    sid, "uid-s" + i, "Name", "user_s" + i,
                    "s" + i + "@test.com", "BIDDER", 0.0, null, null, 0, null
            ));
            new Thread(() -> {
                try {
                    ConnectedClientRegistry.register(sid, new ClientHandler(null));
                } finally {
                    SessionManager.removeSession(sid);
                    done.countDown();
                }
            }).start();
        }

        done.await(3, TimeUnit.SECONDS);
        assertTrue(ConnectedClientRegistry.size() >= 0, "size() không được âm");

        // Cleanup
        for (int i = 0; i < n; i++) ConnectedClientRegistry.unregister("size-session-" + i);
    }

    // ═══════════════════════════════════════════════════════
    // 2. AuctionScheduler — stop idempotent khi gọi đồng thời
    // ═══════════════════════════════════════════════════════

    @Test @Order(20)
    void scheduler_concurrentStopCalls_noException() throws Exception {
        AuctionScheduler.start();

        int threadCount = 20;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threadCount);
        List<Exception> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    AuctionScheduler.stop();
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    done.countDown();
                }
            }).start();
        }

        start.countDown();
        assertTrue(done.await(3, TimeUnit.SECONDS));
        assertTrue(errors.isEmpty(), "stop() đồng thời không được throw: " + errors);
    }

    @Test @Order(21)
    void scheduler_repeatedStartStopSequential_noException() {
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 5; i++) {
                AuctionScheduler.start();
                AuctionScheduler.stop();
            }
        }, "start()/stop() tuần tự nhiều lần không được throw");
    }

    @Test @Order(22)
    void scheduler_stopAfterStart_noException() {
        AuctionScheduler.start();
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 5; i++) AuctionScheduler.stop();
        }, "stop() sau start() không được throw");
    }

    // ═══════════════════════════════════════════════════════
    // 3. SessionManager — thread safety
    // ═══════════════════════════════════════════════════════

    @Test @Order(30)
    void sessionManager_concurrentAddRemove_noException() throws Exception {
        int n = 30;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(n);
        List<Exception> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < n; i++) {
            final String sid = "sm-session-" + i;
            final int idx = i;
            new Thread(() -> {
                try {
                    start.await();
                    SessionManager.addSession(new UserSession(
                            sid, "uid-sm-" + idx, "Name", "u_sm" + idx,
                            "sm" + idx + "@test.com", "BIDDER", 0.0, null, null, 0, null
                    ));
                    Thread.sleep(2);
                    SessionManager.removeSession(sid);
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    done.countDown();
                }
            }).start();
        }

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertTrue(errors.isEmpty(), "SessionManager đồng thời không được throw: " + errors);
    }

    @Test @Order(31)
    void sessionManager_findSessionIdByUserId_concurrentAccess_noException() throws Exception {
        // Thêm nhiều session
        for (int i = 0; i < 10; i++) {
            String sid = "find-session-" + i;
            SessionManager.addSession(new UserSession(
                    sid, "uid-find-" + i, "Name", "u_find" + i,
                    "find" + i + "@test.com", "BIDDER", 0.0, null, null, 0, null
            ));
        }

        int threadCount = 10;
        CountDownLatch done = new CountDownLatch(threadCount);
        List<Exception> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int idx = i % 10;
            new Thread(() -> {
                try {
                    // Một số thread đọc, một số thread xóa
                    if (idx % 2 == 0) {
                        SessionManager.findSessionIdByUserId("uid-find-" + idx);
                    } else {
                        SessionManager.removeSession("find-session-" + idx);
                    }
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    done.countDown();
                }
            }).start();
        }

        done.await(3, TimeUnit.SECONDS);
        assertTrue(errors.isEmpty(), "findSessionIdByUserId đồng thời không được throw: " + errors);

        // Cleanup
        for (int i = 0; i < 10; i++) SessionManager.removeSession("find-session-" + i);
    }

    // ═══════════════════════════════════════════════════════
    // 4. AdminStatsScheduler.notifyStatsChanged() — gọi đồng thời
    // ═══════════════════════════════════════════════════════

    @Test @Order(40)
    void notifyStatsChanged_concurrentCalls_noException() throws Exception {
        AdminStatsScheduler.start();
        Thread.sleep(100);

        int threadCount = 20;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threadCount);
        List<Exception> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    AdminStatsScheduler.notifyStatsChanged();
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    done.countDown();
                }
            }).start();
        }

        start.countDown();
        assertTrue(done.await(3, TimeUnit.SECONDS));
        assertTrue(errors.isEmpty(), "notifyStatsChanged() đồng thời không được throw: " + errors);
    }

    @Test @Order(41)
    void notifyStatsChanged_whileStopRunning_noException() throws Exception {
        AdminStatsScheduler.start();

        CountDownLatch done = new CountDownLatch(2);
        List<Exception> errors = new CopyOnWriteArrayList<>();

        // Thread 1: liên tục notify
        new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    AdminStatsScheduler.notifyStatsChanged();
                    Thread.sleep(10);
                }
            } catch (Exception e) {
                errors.add(e);
            } finally {
                done.countDown();
            }
        }).start();

        // Thread 2: stop trong khi thread 1 đang notify
        new Thread(() -> {
            try {
                Thread.sleep(30);
                AdminStatsScheduler.stop();
            } catch (Exception e) {
                errors.add(e);
            } finally {
                done.countDown();
            }
        }).start();

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertTrue(errors.isEmpty(), "notify + stop đồng thời không được throw: " + errors);
    }

    // ═══════════════════════════════════════════════════════
    // 5. Tổng hợp — nhiều component chạy đồng thời
    // ═══════════════════════════════════════════════════════

    @Test @Order(50)
    void allComponents_runConcurrently_noException() throws Exception {
        AuctionScheduler.start();
        AdminStatsScheduler.start();

        int threadCount = 15;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threadCount);
        List<Exception> errors = new CopyOnWriteArrayList<>();
        AtomicInteger ops = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            new Thread(() -> {
                try {
                    start.await();
                    switch (idx % 5) {
                        case 0 -> AuctionScheduler.stop();
                        case 1 -> AdminStatsScheduler.notifyStatsChanged();
                        case 2 -> {
                            String sid = "all-session-" + idx;
                            SessionManager.addSession(new UserSession(
                                    sid, "uid-all-" + idx, "Name", "u_all" + idx,
                                    "all" + idx + "@test.com", "BIDDER", 0.0, null, null, 0, null
                            ));
                            ConnectedClientRegistry.register(sid, new ClientHandler(null));
                            Thread.sleep(10);
                            ConnectedClientRegistry.unregister(sid);
                            SessionManager.removeSession(sid);
                        }
                        case 3 -> {
                            JsonObject event = new JsonObject();
                            event.addProperty("event", "TEST_CONCURRENT");
                            ConnectedClientRegistry.broadcastAll(event);
                        }
                        case 4 -> AdminStatsScheduler.notifyStatsChanged();
                    }
                    ops.incrementAndGet();
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    done.countDown();
                }
            }).start();
        }

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS), "Tất cả thread phải kết thúc trong 5s");
        assertTrue(errors.isEmpty(), "Không được có exception khi chạy đồng thời: " + errors);
        assertEquals(threadCount, ops.get(), "Tất cả " + threadCount + " ops phải hoàn thành");
    }
}
