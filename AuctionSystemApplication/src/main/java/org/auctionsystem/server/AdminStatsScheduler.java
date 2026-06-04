package org.auctionsystem.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.auctionsystem.client.event.EventType;
import org.auctionsystem.server.DAO.AdminDAO;
import org.auctionsystem.server.session.SessionManager;
import org.auctionsystem.server.util.GsonConfig;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AdminStatsScheduler — Luồng nền cập nhật toàn bộ thống kê admin theo 2 cơ chế:
 *
 * 1. PERIODIC (30 giây): Tự động tính lại và push đầy đủ stats xuống tất cả
 *    admin đang online, bao gồm:
 *      - system_stats : user_stats, item_stats, transaction_stats,
 *                       top_sellers, top_bidders
 *      - item_trend   : số item đăng mới theo từng tháng (6 tháng gần nhất)
 *      - revenue_trend: doanh thu theo từng tháng (6 tháng gần nhất)
 *
 * 2. EVENT-DRIVEN: Khi có sự kiện làm thay đổi stats (bid mới, item đổi
 *    trạng thái, user ban/unban, giao dịch mới, item thêm/sửa/xóa...)
 *    → gọi notifyStatsChanged() → push ngay sau debounce 500ms.
 *
 * Chỉ broadcast đến client đang đăng nhập với role ADMIN.
 */
public class AdminStatsScheduler {

    /** Chu kỳ push stats định kỳ (giây). */
    private static final int  PERIODIC_INTERVAL_SECONDS = 30;

    /** Số tháng lấy cho trend (item_trend + revenue_trend). */
    private static final int  TREND_MONTHS = 6;

    /** Debounce: gom nhiều event liên tiếp thành 1 lần push (ms). */
    private static final long DEBOUNCE_MS  = 500;

    private static final Gson     gson     = GsonConfig.create();
    private static final AdminDAO adminDAO = new AdminDAO();

    private static volatile boolean running = false;

    private static ScheduledExecutorService scheduler;
    private static ScheduledFuture<?>        periodicFuture;

    /** Flag debounce cho event-driven push. */
    private static final AtomicBoolean pendingPush = new AtomicBoolean(false);

    /** Thread xử lý event-driven push với debounce. */
    private static Thread eventThread;

    // ─────────────────────────────────────────────────────────────────────────
    //  Khởi động / Dừng
    // ─────────────────────────────────────────────────────────────────────────

    public static synchronized void start() {
        if (running) return;
        running = true;

        // Periodic scheduler — single-thread tránh overlap
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AdminStats-Periodic");
            t.setDaemon(true);
            return t;
        });

        periodicFuture = scheduler.scheduleAtFixedRate(
                AdminStatsScheduler::doPushStats,
                PERIODIC_INTERVAL_SECONDS,
                PERIODIC_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );

        // Event-driven thread
        eventThread = new Thread(AdminStatsScheduler::runEventLoop, "AdminStats-Event");
        eventThread.setDaemon(true);
        eventThread.start();

        System.out.println("[AdminStatsScheduler] Đã khởi động"
                + " (periodic=" + PERIODIC_INTERVAL_SECONDS + "s"
                + ", trend=" + TREND_MONTHS + " tháng).");
    }

    public static synchronized void stop() {
        if (!running) return;
        running = false;

        if (periodicFuture != null) periodicFuture.cancel(false);
        if (scheduler      != null) scheduler.shutdownNow();
        if (eventThread    != null) eventThread.interrupt();

        pendingPush.set(false); // reset flag khi dừng

        System.out.println("[AdminStatsScheduler] Đã dừng.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  API công khai
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Gọi khi có bất kỳ sự kiện nào làm thay đổi stats.
     * Dùng debounce — nhiều event dồn vào chỉ gây 1 lần push.
     */
    public static void notifyStatsChanged() {
        if (!running) return;
        if (pendingPush.compareAndSet(false, true) && eventThread != null) {
            eventThread.interrupt();
        }
    }

    /**
     * Gọi ngay sau khi admin login để push stats tức thì,
     * không phải chờ đến chu kỳ 30 giây tiếp theo.
     */
    public static void pushStatsToAdmin(String sessionId) {
        if (!running) return;
        new Thread(() -> {
            try {
                JsonObject event = buildStatsEvent();
                ConnectedClientRegistry.sendTo(sessionId, event);
                System.out.println("[AdminStatsScheduler] Pushed stats ngay sau login: " + sessionId);
            } catch (Exception e) {
                System.err.println("[AdminStatsScheduler] pushStatsToAdmin lỗi: " + e.getMessage());
            }
        }, "AdminStats-LoginPush").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Event loop — debounce
    // ─────────────────────────────────────────────────────────────────────────

    private static void runEventLoop() {
        System.out.println("[AdminStats-Event] Thread bắt đầu chạy.");

        while (running) {
            // [FIX] Reset flag đầu mỗi vòng: nếu interrupt bị nuốt trước khi
            // thread kịp vào sleep(Long.MAX_VALUE), flag không treo ở true mãi.
            // Vòng lặp mới = đã "nhận" tín hiệu, tính lại từ đầu.
            pendingPush.set(false);
            try {
                Thread.sleep(Long.MAX_VALUE);

            } catch (InterruptedException e) {
                if (!running) break;

                if (pendingPush.compareAndSet(true, false)) {
                    // Debounce: gom các notify liên tiếp thành 1 lần push
                    try {
                        Thread.sleep(DEBOUNCE_MS);
                    } catch (InterruptedException debounceIe) {
                        // [FIX] Có thêm notify trong lúc debounce → vẫn push,
                        // không bỏ qua. Reset flag rồi tiếp tục doPushStats().
                        pendingPush.set(false);
                        if (!running) break;
                        // Không return sớm — vẫn chạy doPushStats() bên dưới
                    }
                    doPushStats();
                }

                Thread.interrupted(); // reset interrupted flag của thread
            }
        }

        System.out.println("[AdminStats-Event] Thread kết thúc.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Core: tính toàn bộ stats và broadcast đến admin
    // ─────────────────────────────────────────────────────────────────────────

    private static void doPushStats() {
        // Bỏ qua nếu không có admin nào online — tránh query DB vô ích
        boolean hasAdmin = SessionManager.getAllSessions().stream()
                .anyMatch(s -> "ADMIN".equalsIgnoreCase(s.getRole()));

        if (!hasAdmin) {
            System.out.println("[AdminStatsScheduler] Không có admin online, bỏ qua push.");
            return;
        }

        try {
            JsonObject event = buildStatsEvent();
            ConnectedClientRegistry.broadcastToAdmins(event);
            System.out.println("[AdminStatsScheduler] Đã push ADMIN_STATS_UPDATE.");
        } catch (Exception e) {
            System.err.println("[AdminStatsScheduler] Lỗi khi tính stats: " + e.getMessage());
        }
    }

    /**
     * Tạo JsonObject event ADMIN_STATS_UPDATE đầy đủ:
     *   - system_stats : user_stats, item_stats, transaction_stats,
     *                    top_sellers(5), top_bidders(5)
     *   - item_trend   : số item đăng mới theo tháng (TREND_MONTHS tháng)
     *   - revenue_trend: doanh thu theo tháng (TREND_MONTHS tháng)
     */
    private static JsonObject buildStatsEvent() {
        JsonObject systemStats = new JsonObject();
        systemStats.add("user_stats",        gson.toJsonTree(adminDAO.getUserStats()));
        systemStats.add("item_stats",        gson.toJsonTree(adminDAO.getItemStats()));
        systemStats.add("transaction_stats", gson.toJsonTree(adminDAO.getTransactionStats()));
        systemStats.add("top_sellers",       gson.toJsonTree(adminDAO.getTopSellers(5)));
        systemStats.add("top_bidders",       gson.toJsonTree(adminDAO.getTopBidders(5)));

        JsonObject data = new JsonObject();
        data.add("system_stats",  systemStats);
        data.add("item_trend",    gson.toJsonTree(adminDAO.getItemCountByMonth(TREND_MONTHS)));
        data.add("revenue_trend", gson.toJsonTree(adminDAO.getRevenueByMonth(TREND_MONTHS)));

        JsonObject event = new JsonObject();
        event.addProperty("event", EventType.ADMIN_STATS_UPDATE);
        event.add("data", data);

        return event;
    }
}