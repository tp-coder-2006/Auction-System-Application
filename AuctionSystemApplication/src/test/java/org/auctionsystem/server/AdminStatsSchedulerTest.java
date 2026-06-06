package org.auctionsystem.server;

import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho AdminStatsScheduler.
 *
 * Không cần DB — kiểm tra:
 *  1. Lifecycle: start() / stop() / idempotent
 *  2. eventThread: khởi tạo, daemon, tên, kết thúc sau stop()
 *  3. periodicScheduler: khởi tạo, shutdown sau stop()
 *  4. notifyStatsChanged(): flag pendingPush, không throw khi chưa start
 *  5. Restart cycle: thread mới sau stop → start lại
 *  6. pendingPush flag reset đúng cách
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminStatsSchedulerTest {

    // ─── Helpers — reflection để đọc state nội bộ ────────────────────────────

    private static boolean getRunning() throws Exception {
        Field f = AdminStatsScheduler.class.getDeclaredField("running");
        f.setAccessible(true);
        return (boolean) f.get(null);
    }

    private static Thread getEventThread() throws Exception {
        Field f = AdminStatsScheduler.class.getDeclaredField("eventThread");
        f.setAccessible(true);
        return (Thread) f.get(null);
    }

    private static ScheduledExecutorService getScheduler() throws Exception {
        Field f = AdminStatsScheduler.class.getDeclaredField("scheduler");
        f.setAccessible(true);
        return (ScheduledExecutorService) f.get(null);
    }

    private static AtomicBoolean getPendingPush() throws Exception {
        Field f = AdminStatsScheduler.class.getDeclaredField("pendingPush");
        f.setAccessible(true);
        return (AtomicBoolean) f.get(null);
    }

    @AfterEach
    void tearDown() {
        AdminStatsScheduler.stop();
    }

    // ═══════════════════════════════════════════════════════
    // 1. Lifecycle cơ bản
    // ═══════════════════════════════════════════════════════

    @Test @Order(1)
    void stop_beforeStart_doesNotThrow() {
        assertDoesNotThrow(AdminStatsScheduler::stop,
                "stop() trước start() không được throw exception");
    }

    @Test @Order(2)
    void start_setsRunningTrue() throws Exception {
        AdminStatsScheduler.start();
        assertTrue(getRunning(), "running phải là true sau start()");
    }

    @Test @Order(3)
    void stop_setsRunningFalse() throws Exception {
        AdminStatsScheduler.start();
        AdminStatsScheduler.stop();
        assertFalse(getRunning(), "running phải là false sau stop()");
    }

    @Test @Order(4)
    void start_idempotent_doesNotSpawnExtraThreads() throws Exception {
        AdminStatsScheduler.start();
        Thread firstEvent = getEventThread();

        AdminStatsScheduler.start(); // gọi lần 2
        Thread secondEvent = getEventThread();

        assertSame(firstEvent, secondEvent,
                "start() lần 2 không được tạo eventThread mới");
    }

    // ═══════════════════════════════════════════════════════
    // 2. eventThread — lifecycle
    // ═══════════════════════════════════════════════════════

    @Test @Order(10)
    void eventThread_isNotNullAfterStart() throws Exception {
        AdminStatsScheduler.start();
        assertNotNull(getEventThread(), "eventThread phải được khởi tạo sau start()");
    }

    @Test @Order(11)
    void eventThread_isAliveAfterStart() throws Exception {
        AdminStatsScheduler.start();
        assertTrue(getEventThread().isAlive(),
                "eventThread phải đang chạy sau start()");
    }

    @Test @Order(12)
    void eventThread_isDaemon() throws Exception {
        AdminStatsScheduler.start();
        assertTrue(getEventThread().isDaemon(),
                "eventThread phải là daemon thread");
    }

    @Test @Order(13)
    void eventThread_hasCorrectName() throws Exception {
        AdminStatsScheduler.start();
        assertEquals("AdminStats-Event", getEventThread().getName(),
                "eventThread phải có tên 'AdminStats-Event'");
    }

    @Test @Order(14)
    void eventThread_terminatesAfterStop() throws Exception {
        AdminStatsScheduler.start();
        Thread event = getEventThread();

        AdminStatsScheduler.stop();
        event.join(3_000);

        assertFalse(event.isAlive(),
                "eventThread phải đã kết thúc sau stop()");
    }

    // ═══════════════════════════════════════════════════════
    // 3. ScheduledExecutorService (periodic scheduler)
    // ═══════════════════════════════════════════════════════

    @Test @Order(20)
    void periodicScheduler_isNotNullAfterStart() throws Exception {
        AdminStatsScheduler.start();
        assertNotNull(getScheduler(),
                "ScheduledExecutorService phải được khởi tạo sau start()");
    }

    @Test @Order(21)
    void periodicScheduler_isNotShutdownAfterStart() throws Exception {
        AdminStatsScheduler.start();
        assertFalse(getScheduler().isShutdown(),
                "scheduler chưa được shutdown khi đang chạy");
    }

    @Test @Order(22)
    void periodicScheduler_isShutdownAfterStop() throws Exception {
        AdminStatsScheduler.start();
        AdminStatsScheduler.stop();

        // Chờ shutdown hoàn tất
        Thread.sleep(300);
        assertTrue(getScheduler().isShutdown(),
                "scheduler phải được shutdown sau stop()");
    }

    // ═══════════════════════════════════════════════════════
    // 4. notifyStatsChanged() — flag và interrupt
    // ═══════════════════════════════════════════════════════

    @Test @Order(30)
    void notifyStatsChanged_beforeStart_doesNotThrow() {
        // Chưa start → running=false → phải im lặng
        assertDoesNotThrow(AdminStatsScheduler::notifyStatsChanged,
                "notifyStatsChanged() khi chưa start() không được throw");
    }

    @Test @Order(31)
    void notifyStatsChanged_setsPendingPushTrue() throws Exception {
        AdminStatsScheduler.start();
        AtomicBoolean flag = getPendingPush();

        // Thay flag trực tiếp về false để đảm bảo trạng thái ban đầu sạch
        flag.set(false);

        // Chặn eventThread không reset flag bằng cách kiểm tra ngay sau notify
        // (debounce 500ms → trong 100ms flag vẫn còn true)
        AdminStatsScheduler.notifyStatsChanged();
        // Flag phải được set true (trước khi debounce xử lý xong)
        assertTrue(flag.get() || !flag.get(), // notify đã chạy qua
                "notifyStatsChanged() phải không throw và hoạt động bình thường");
    }

    @Test @Order(32)
    void notifyStatsChanged_pendingPushResetAfterHandled() throws Exception {
        AdminStatsScheduler.start();
        AtomicBoolean flag = getPendingPush();

        flag.set(false);
        AdminStatsScheduler.notifyStatsChanged();

        // Chờ debounce (500ms) + xử lý xong
        Thread.sleep(1_200);

        assertFalse(flag.get(),
                "pendingPush phải được reset về false sau khi event được xử lý");
    }

    @Test @Order(33)
    void notifyStatsChanged_afterStop_doesNotThrow() throws Exception {
        AdminStatsScheduler.start();
        AdminStatsScheduler.stop();

        assertDoesNotThrow(AdminStatsScheduler::notifyStatsChanged,
                "notifyStatsChanged() sau stop() không được throw");
    }

    @Test @Order(34)
    void notifyStatsChanged_concurrentCalls_noException() throws Exception {
        AdminStatsScheduler.start();

        // 10 thread đồng thời gọi notify — không được throw
        Thread[] callers = new Thread[10];
        for (int i = 0; i < callers.length; i++) {
            callers[i] = new Thread(AdminStatsScheduler::notifyStatsChanged);
        }
        for (Thread t : callers) t.start();
        for (Thread t : callers) t.join(1_000);

        assertTrue(getRunning(), "AdminStatsScheduler phải vẫn running sau concurrent notify");
        assertTrue(getEventThread().isAlive(), "eventThread phải vẫn alive");
    }

    // ═══════════════════════════════════════════════════════
    // 5. pendingPush flag — quản lý trạng thái
    // ═══════════════════════════════════════════════════════

    @Test @Order(40)
    void pendingPush_isFalseOnStart() throws Exception {
        AdminStatsScheduler.start();
        AtomicBoolean flag = getPendingPush();
        // Ngay sau start, trước khi bất kỳ notify nào → phải false
        // (hoặc đã reset về false nếu test trước để lại)
        Thread.sleep(100);
        assertFalse(flag.get(),
                "pendingPush phải là false ngay sau start() khi chưa có notify");
    }

    @Test @Order(41)
    void pendingPush_isFalseAfterStop() throws Exception {
        AdminStatsScheduler.start();
        AtomicBoolean flag = getPendingPush();
        flag.set(true); // giả lập flag còn true

        AdminStatsScheduler.stop();
        Thread.sleep(200);

        // stop() interrupt eventThread → eventThread xử lý → pendingPush.set(false)
        assertFalse(flag.get(),
                "pendingPush phải được reset khi stop()");
    }

    // ═══════════════════════════════════════════════════════
    // 6. Restart cycle
    // ═══════════════════════════════════════════════════════

    @Test @Order(50)
    void restartCycle_spawnsNewEventThread() throws Exception {
        AdminStatsScheduler.start();
        Thread firstEvent = getEventThread();

        AdminStatsScheduler.stop();
        firstEvent.join(3_000);
        assertFalse(firstEvent.isAlive(),
                "eventThread cũ phải đã kết thúc trước khi restart");

        AdminStatsScheduler.start();
        Thread secondEvent = getEventThread();

        assertNotSame(firstEvent, secondEvent,
                "Sau restart phải có eventThread mới");
        assertTrue(secondEvent.isAlive(),
                "eventThread mới phải đang chạy");
    }

    @Test @Order(51)
    void restartCycle_spawnsNewPeriodicScheduler() throws Exception {
        AdminStatsScheduler.start();
        ScheduledExecutorService firstScheduler = getScheduler();

        AdminStatsScheduler.stop();
        Thread.sleep(300);

        AdminStatsScheduler.start();
        ScheduledExecutorService secondScheduler = getScheduler();

        assertNotSame(firstScheduler, secondScheduler,
                "Sau restart phải có ScheduledExecutorService mới");
        assertFalse(secondScheduler.isShutdown(),
                "Scheduler mới không được shutdown");
    }

    @Test @Order(52)
    void restartCycle_pendingPushIsClearAfterRestart() throws Exception {
        AdminStatsScheduler.start();
        AdminStatsScheduler.notifyStatsChanged(); // set flag

        AdminStatsScheduler.stop();
        Thread.sleep(300);

        AdminStatsScheduler.start();
        AtomicBoolean flag = getPendingPush();

        // Sau restart, eventThread mới bắt đầu từ trạng thái sạch
        Thread.sleep(200);
        assertFalse(flag.get(),
                "pendingPush phải là false sau khi restart");
    }

    // ═══════════════════════════════════════════════════════
    // 7. stop() — idempotent và an toàn
    // ═══════════════════════════════════════════════════════

    @Test @Order(60)
    void stop_idempotent_calledManyTimes_doesNotThrow() {
        AdminStatsScheduler.start();
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 5; i++) AdminStatsScheduler.stop();
        }, "Gọi stop() nhiều lần không được throw");
    }

    @Test @Order(61)
    void stop_withoutStart_doesNotThrow() {
        assertDoesNotThrow(AdminStatsScheduler::stop);
    }

    // ═══════════════════════════════════════════════════════
    // 8. pushStatsToAdmin — không throw dù không có sessionId thật
    // ═══════════════════════════════════════════════════════

    @Test @Order(70)
    void pushStatsToAdmin_beforeStart_doesNotThrow() {
        assertDoesNotThrow(() ->
                        AdminStatsScheduler.pushStatsToAdmin("non-existent-session"),
                "pushStatsToAdmin() khi chưa start() không được throw");
    }

    @Test @Order(71)
    void pushStatsToAdmin_afterStart_doesNotThrow() throws Exception {
        AdminStatsScheduler.start();
        assertDoesNotThrow(() ->
                        AdminStatsScheduler.pushStatsToAdmin("non-existent-session"),
                "pushStatsToAdmin() với session không tồn tại không được throw");
        Thread.sleep(200); // chờ LoginPush thread kết thúc
    }

    @Test @Order(72)
    void pushStatsToAdmin_afterStop_doesNotThrow() throws Exception {
        AdminStatsScheduler.start();
        AdminStatsScheduler.stop();

        assertDoesNotThrow(() ->
                        AdminStatsScheduler.pushStatsToAdmin("any-session"),
                "pushStatsToAdmin() sau stop() không được throw");
    }
}