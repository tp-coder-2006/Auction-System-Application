package org.auctionsystem.server;

import org.auctionsystem.server.session.SessionManager;
import org.auctionsystem.server.session.UserSession;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ConnectedClientRegistry — Registry quản lý tất cả ClientHandler đang kết nối.
 *
 * Sửa so với phiên bản cũ:
 *
 * [FIX #5] broadcastAll() và sendTo() bắt IOException từng handler riêng:
 *   Cũ: handler.sendEvent() ném exception → cả broadcast bị ngắt giữa chừng,
 *       những client phía sau không nhận được event.
 *       Handler disconnect không được tự unregister → registry tích lũy zombie.
 *   Mới: try-catch từng handler riêng. Nếu sendEvent() thất bại (IOException,
 *       socket closed) → unregister handler đó ngay lập tức.
 *       Các handler còn lại vẫn nhận được event bình thường.
 *
 * [FIX #6] sendEvent() trong ClientHandler đã có null-check (writer != null).
 *   Registry không cần biết về writer — chỉ cần catch exception từ sendEvent().
 *
 * [NEW] broadcastToAdmins(event):
 *   Gửi event chỉ đến những client đang đăng nhập với role ADMIN.
 *   Dùng cho ADMIN_STATS_UPDATE để tránh rò rỉ thông tin nhạy cảm.
 */
public class ConnectedClientRegistry {

    private static final Map<String, ClientHandler> registry = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    //  Đăng ký / Hủy
    // ─────────────────────────────────────────────────────────────────────────

    public static void register(String sessionId, ClientHandler handler) {
        registry.put(sessionId, handler);
        System.out.println("[Registry] Đã đăng ký session: " + sessionId
                + " | Tổng kết nối: " + registry.size());
    }

    public static void unregister(String sessionId) {
        registry.remove(sessionId);
        System.out.println("[Registry] Đã xóa session: " + sessionId
                + " | Còn lại: " + registry.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Tìm kiếm
    // ─────────────────────────────────────────────────────────────────────────

    public static ClientHandler get(String sessionId) {
        return registry.get(sessionId);
    }

    public static Collection<ClientHandler> getAll() {
        return registry.values();
    }

    public static int size() {
        return registry.size();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Broadcast helpers — [FIX #5] catch exception từng handler riêng
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Gửi event đến TẤT CẢ client đang kết nối.
     *
     * [FIX #5] Mỗi handler được try-catch riêng:
     *   - Nếu handler disconnect giữa chừng → sendEvent() trả về false
     *     (ClientHandler.sendEvent đã check socket.isClosed()) hoặc ném exception
     *     → unregister ngay, tiếp tục broadcast cho handler kế tiếp.
     *   - snapshot registry.entrySet() trước khi duyệt để tránh
     *     ConcurrentModificationException khi unregister trong vòng lặp.
     */
    public static void broadcastAll(com.google.gson.JsonObject event) {
        String eventStr = event.toString();
        int sent = 0;
        int failed = 0;

        // Snapshot để tránh ConcurrentModificationException khi remove trong loop
        for (Map.Entry<String, ClientHandler> entry : registry.entrySet()) {
            try {
                entry.getValue().sendEvent(eventStr);
                sent++;
            } catch (Exception e) {
                // Handler lỗi → unregister khỏi registry
                System.err.println("[Registry] Handler " + entry.getKey()
                        + " lỗi khi broadcast, unregister: " + e.getMessage());
                registry.remove(entry.getKey());
                failed++;
            }
        }

        System.out.println("[Registry] Broadcast all → " + sent + " sent, "
                + failed + " failed | event: " + event.get("event").getAsString());
    }

    /**
     * Gửi event đến một client cụ thể theo sessionId.
     *
     * [FIX #5] Nếu handler không còn kết nối → unregister ngay.
     */
    public static void sendTo(String sessionId, com.google.gson.JsonObject event) {
        ClientHandler handler = registry.get(sessionId);
        if (handler == null) return;
        try {
            handler.sendEvent(event.toString());
        } catch (Exception e) {
            System.err.println("[Registry] sendTo " + sessionId + " lỗi, unregister: " + e.getMessage());
            registry.remove(sessionId);
        }
    }

    /**
     * Gửi event đến tất cả client NGOẠI TRỪ client có sessionId chỉ định.
     *
     * [FIX #5] Tương tự broadcastAll — catch từng handler riêng.
     */
    public static void broadcastExcept(String excludeSessionId, com.google.gson.JsonObject event) {
        String eventStr = event.toString();
        int sent = 0;
        int failed = 0;

        for (Map.Entry<String, ClientHandler> entry : registry.entrySet()) {
            if (entry.getKey().equals(excludeSessionId)) continue;
            try {
                entry.getValue().sendEvent(eventStr);
                sent++;
            } catch (Exception e) {
                System.err.println("[Registry] Handler " + entry.getKey()
                        + " lỗi khi broadcastExcept, unregister: " + e.getMessage());
                registry.remove(entry.getKey());
                failed++;
            }
        }

        System.out.println("[Registry] BroadcastExcept → " + sent + " sent, "
                + failed + " failed | event: " + event.get("event").getAsString());
    }

    /**
     * [NEW] Gửi event CHỈ đến những client đang đăng nhập với role ADMIN.
     *
     * Dùng SessionManager để tra cứu role của từng sessionId trong registry.
     * Session không tồn tại (đã logout nhưng socket chưa đóng) → bỏ qua.
     * Handler lỗi → unregister ngay, không làm gián đoạn các admin còn lại.
     */
    public static void broadcastToAdmins(com.google.gson.JsonObject event) {
        String eventStr = event.toString();
        int sent = 0;
        int skipped = 0;
        int failed = 0;

        for (Map.Entry<String, ClientHandler> entry : registry.entrySet()) {
            String sessionId = entry.getKey();

            // Tra cứu role từ SessionManager
            org.auctionsystem.server.session.UserSession session =
                    SessionManager.getSession(sessionId);

            if (session == null || !"ADMIN".equalsIgnoreCase(session.getRole())) {
                skipped++;
                continue;
            }

            try {
                entry.getValue().sendEvent(eventStr);
                sent++;
            } catch (Exception e) {
                System.err.println("[Registry] Admin handler " + sessionId
                        + " lỗi khi broadcastToAdmins, unregister: " + e.getMessage());
                registry.remove(sessionId);
                failed++;
            }
        }

        System.out.println("[Registry] BroadcastToAdmins → " + sent + " sent, "
                + skipped + " skipped (non-admin), "
                + failed + " failed | event: " + event.get("event").getAsString());
    }
}