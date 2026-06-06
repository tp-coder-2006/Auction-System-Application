package org.auctionsystem.server;

import org.junit.jupiter.api.*;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho AuctionScheduler.
 *
 * Không cần DB — kiểm tra:
 *  1. Lifecycle: start() / stop() / idempotent start
 *  2. stop() dừng đúng cả 2 thread
 *  3. Không throw khi gọi stop() trước start()
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuctionSchedulerTest {

    // ─── Helpers — reflection để đọc state nội bộ ────────────────────────────

    private static boolean getRunning() throws Exception {
        Field f = AuctionScheduler.class.getDeclaredField("running");
        f.setAccessible(true);
        return (boolean) f.get(null);
    }

    private static Thread getActivateThread() throws Exception {
        Field f = AuctionScheduler.class.getDeclaredField("activateThread");
        f.setAccessible(true);
        return (Thread) f.get(null);
    }

    private static Thread getSettleThread() throws Exception {
        Field f = AuctionScheduler.class.getDeclaredField("settleThread");
        f.setAccessible(true);
        return (Thread) f.get(null);
    }

    @AfterEach
    void tearDown() {
        // Đảm bảo scheduler luôn được dừng sau mỗi test
        AuctionScheduler.stop();
    }

    // ═══════════════════════════════════════════════════════
    // 1. Lifecycle
    // ═══════════════════════════════════════════════════════

    @Test @Order(1)
    void stop_beforeStart_doesNotThrow() {
        assertDoesNotThrow(AuctionScheduler::stop,
                "stop() trước start() không được throw exception");
    }

    @Test @Order(2)
    void start_setsRunningTrue() throws Exception {
        AuctionScheduler.start();
        assertTrue(getRunning(), "running phải là true sau start()");
    }

    @Test @Order(3)
    void start_spawnsActivateAndSettleThreads() throws Exception {
        AuctionScheduler.start();

        Thread activate = getActivateThread();
        Thread settle   = getSettleThread();

        assertNotNull(activate, "activateThread phải được khởi tạo");
        assertNotNull(settle,   "settleThread phải được khởi tạo");
        assertTrue(activate.isAlive(), "activateThread phải đang chạy");
        assertTrue(settle.isAlive(),   "settleThread phải đang chạy");
    }

    @Test @Order(4)
    void start_threadsAreDaemon() throws Exception {
        AuctionScheduler.start();
        assertTrue(getActivateThread().isDaemon(), "activateThread phải là daemon");
        assertTrue(getSettleThread().isDaemon(),   "settleThread phải là daemon");
    }

    @Test @Order(5)
    void start_threadNames_correct() throws Exception {
        AuctionScheduler.start();
        assertEquals("Scheduler-Activate", getActivateThread().getName());
        assertEquals("Scheduler-Settle",   getSettleThread().getName());
    }

    @Test @Order(6)
    void start_idempotent_doesNotSpawnExtraThreads() throws Exception {
        AuctionScheduler.start();
        Thread firstActivate = getActivateThread();

        AuctionScheduler.start(); // gọi lần 2
        Thread secondActivate = getActivateThread();

        assertSame(firstActivate, secondActivate,
                "start() lần 2 không được tạo thread mới");
    }

    @Test @Order(7)
    void stop_setsRunningFalse() throws Exception {
        AuctionScheduler.start();
        AuctionScheduler.stop();
        assertFalse(getRunning(), "running phải là false sau stop()");
    }

    @Test @Order(8)
    void stop_terminatesThreadsEventually() throws Exception {
        AuctionScheduler.start();
        Thread activate = getActivateThread();
        Thread settle   = getSettleThread();

        AuctionScheduler.stop();

        // Cho thread tối đa 3 giây để kết thúc
        activate.join(3_000);
        settle.join(3_000);

        assertFalse(activate.isAlive(), "activateThread phải đã kết thúc sau stop()");
        assertFalse(settle.isAlive(),   "settleThread phải đã kết thúc sau stop()");
    }

    @Test @Order(21)
    void stop_calledTwice_doesNotThrow() throws Exception {
        AuctionScheduler.start();
        AuctionScheduler.stop();
        assertDoesNotThrow(AuctionScheduler::stop, "stop() lần 2 không được throw");
    }

    // ═══════════════════════════════════════════════════════
    // 4. start → stop → start lại (restart cycle)
    // ═══════════════════════════════════════════════════════

    @Test @Order(30)
    void restartCycle_startsNewThreadsAfterStop() throws Exception {
        AuctionScheduler.start();
        Thread firstActivate = getActivateThread();
        Thread firstSettle   = getSettleThread();

        AuctionScheduler.stop();
        // [SỬA] Verify thread cũ thực sự đã chết trước khi start lại
        firstActivate.join(3_000);
        firstSettle.join(3_000);
        assertFalse(firstActivate.isAlive(),
                "Thread activate cũ phải đã kết thúc trước khi restart");
        assertFalse(firstSettle.isAlive(),
                "Thread settle cũ phải đã kết thúc trước khi restart");

        // Start lại
        AuctionScheduler.start();
        Thread secondActivate = getActivateThread();
        Thread secondSettle   = getSettleThread();

        assertNotSame(firstActivate, secondActivate,
                "Sau restart phải có thread activate mới");
        assertNotSame(firstSettle, secondSettle,
                "Sau restart phải có thread settle mới");
        assertTrue(secondActivate.isAlive(), "Thread activate mới phải đang chạy");
        assertTrue(secondSettle.isAlive(),   "Thread settle mới phải đang chạy");
    }

    // ═══════════════════════════════════════════════════════
    // 5. settleThread — lifecycle riêng biệt
    // ═══════════════════════════════════════════════════════

    @Test @Order(40)
    void settleThread_isAliveAfterStart() throws Exception {
        AuctionScheduler.start();
        Thread settle = getSettleThread();
        assertNotNull(settle, "settleThread phải được khởi tạo");
        assertTrue(settle.isAlive(), "settleThread phải đang chạy sau start()");
    }

    @Test @Order(41)
    void settleThread_isDaemon() throws Exception {
        AuctionScheduler.start();
        assertTrue(getSettleThread().isDaemon(),
                "settleThread phải là daemon thread");
    }

    @Test @Order(42)
    void settleThread_hasCorrectName() throws Exception {
        AuctionScheduler.start();
        assertEquals("Scheduler-Settle", getSettleThread().getName());
    }

    @Test @Order(43)
    void settleThread_terminatesAfterStop() throws Exception {
        AuctionScheduler.start();
        Thread settle = getSettleThread();
        AuctionScheduler.stop();
        settle.join(3_000);
        assertFalse(settle.isAlive(),
                "settleThread phải đã kết thúc sau stop()");
    }

    @Test @Order(44)
    void settleThread_notSameInstanceAfterRestart() throws Exception {
        AuctionScheduler.start();
        Thread first = getSettleThread();

        AuctionScheduler.stop();
        first.join(3_000);

        AuctionScheduler.start();
        Thread second = getSettleThread();

        assertNotSame(first, second,
                "Sau restart phải có settleThread mới, không tái dùng thread cũ");
        assertTrue(second.isAlive(), "settleThread mới phải đang chạy");
    }

    // ═══════════════════════════════════════════════════════
    // 6. activateThread và settleThread chạy song song
    // ═══════════════════════════════════════════════════════

    @Test @Order(50)
    void bothThreads_aliveSimultaneously() throws Exception {
        AuctionScheduler.start();
        Thread activate = getActivateThread();
        Thread settle   = getSettleThread();

        // 2 thread khác nhau, đều alive, đều daemon
        assertNotSame(activate, settle, "activateThread và settleThread phải là 2 thread khác nhau");
        assertTrue(activate.isAlive());
        assertTrue(settle.isAlive());
    }

    @Test @Order(51)
    void bothThreads_terminateTogetherOnStop() throws Exception {
        AuctionScheduler.start();
        Thread activate = getActivateThread();
        Thread settle   = getSettleThread();

        AuctionScheduler.stop();

        activate.join(3_000);
        settle.join(3_000);

        assertFalse(activate.isAlive(), "activateThread phải dừng sau stop()");
        assertFalse(settle.isAlive(),   "settleThread phải dừng sau stop()");
    }

    @Test @Order(70)
    void stop_idempotent_calledManyTimes_doesNotThrow() {
        AuctionScheduler.start();
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 5; i++) AuctionScheduler.stop();
        }, "Gọi stop() nhiều lần không được throw");
    }

    @Test @Order(71)
    void stop_withoutStart_doesNotThrow() {
        // Gọi stop khi chưa có thread nào
        assertDoesNotThrow(AuctionScheduler::stop);
    }
}
