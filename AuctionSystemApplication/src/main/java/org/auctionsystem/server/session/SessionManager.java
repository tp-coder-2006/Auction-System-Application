package org.auctionsystem.server.session;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SessionManager {
    private static final Map<String, UserSession> sessions = new ConcurrentHashMap<>();

    // ╔══════════════════════════════════════════════════════════════════════╗
    // ║  [MỚI] Scheduler chạy ngầm — tự động quét và xóa session hết hạn  ║
    // ╚══════════════════════════════════════════════════════════════════════╝
    //
    // - newSingleThreadScheduledExecutor(): tạo 1 thread chạy ngầm duy nhất,
    //   đủ dùng vì cleanExpiredSessions() rất nhanh (chỉ duyệt map).
    //
    // - isDaemon = true: khi JVM tắt (AuctionServer dừng), thread này tự
    //   tắt theo mà không cần gọi shutdown() thủ công.
    private static final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread t = new Thread(runnable, "session-cleanup-thread");
                t.setDaemon(true); // [MỚI] tự tắt khi server tắt
                return t;
            });

    /**
     * [MỚI] Khởi động vòng quét session hết hạn tự động.
     *
     * Gọi method này 1 lần duy nhất khi server khởi động (trong AuctionServer.main).
     *
     * @param intervalMinutes  Chu kỳ quét (phút). Khuyên dùng = 5.
     *                         Không nên đặt quá thấp (lãng phí CPU)
     *                         hay quá cao (session hết hạn lâu mới bị xóa khỏi map).
     */
    public static void startSessionCleanup(int intervalMinutes) {
        scheduler.scheduleAtFixedRate(
                () -> {
                    int before = sessions.size();
                    cleanExpiredSessions();               // quét và xóa
                    int removed = before - sessions.size();
                    if (removed > 0) {
                        System.out.println("🧹 [SessionCleanup] Đã xóa " + removed
                                + " session hết hạn. Còn lại: " + sessions.size());
                    }
                },
                intervalMinutes,   // delay trước lần chạy đầu tiên
                intervalMinutes,   // chu kỳ lặp lại
                TimeUnit.MINUTES
        );
        System.out.println("✅ [SessionCleanup] Đã bắt đầu quét session hết hạn mỗi "
                + intervalMinutes + " phút.");
    }
    // ══════════════════════════════════════════════════════════════════════

    public static void addSession(UserSession userSession) {
        sessions.put(userSession.getSessionId(), userSession);
    }

    public static void removeSession(String sessionId) {
        sessions.remove(sessionId);
    }

    public static UserSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public static void cleanExpiredSessions() {
        sessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    public static Collection<UserSession> getAllSessions() {
        return sessions.values()
                .stream()
                .filter(s -> !s.isExpired())
                .collect(Collectors.toList());
    }
}