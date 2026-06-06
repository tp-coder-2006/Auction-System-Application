package org.auctionsystem.client.event;

import com.google.gson.JsonObject;
import org.auctionsystem.client.session.UserSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * BalanceWatcher — Global singleton đồng bộ số dư real-time cho toàn bộ app.
 *
 * <p>Lắng nghe 3 loại event mang field {@code "balance"} từ server:
 * <ul>
 *   <li>{@link EventType#BALANCE_UPDATED} — sau DEPOSIT / WITHDRAW</li>
 *   <li>{@link EventType#BID_DEDUCT}      — sau khi thanh toán thắng đấu giá (bidder)</li>
 *   <li>{@link EventType#BID_CREDIT}      — sau khi nhận tiền bán (seller)</li>
 * </ul>
 *
 * <p>Khi nhận event, nó:
 * <ol>
 *   <li>Cập nhật {@link UserSession#setBalance(double)} ngay lập tức.</li>
 *   <li>Gọi tất cả UI-listener đang đăng ký để các screen chủ động refresh label.</li>
 * </ol>
 *
 * <p>Các screen hiển thị balance (Dashboard, Wallet, BiddingRoom, v.v.) chỉ cần:
 * <pre>{@code
 *   // Trong initialize():
 *   BalanceWatcher.registerListener("MyScreen", balance -> {
 *       Platform.runLater(() -> lbl_balance.setText(formatVnd(balance) + " ₫"));
 *   });
 *
 *   // Khi rời màn hình (navigate away / cleanup):
 *   BalanceWatcher.unregisterListener("MyScreen");
 * }</pre>
 *
 * <p>Không còn cần tự {@code EventDispatcher.register(BALANCE_UPDATED, ...)} trong từng screen.
 *
 * <p><b>Lifecycle:</b>
 * <ul>
 *   <li>Gọi {@link #activate()} một lần sau khi login thành công.</li>
 *   <li>Gọi {@link #deactivate()} khi logout để dọn dẹp toàn bộ.</li>
 * </ul>
 */
public final class BalanceWatcher {

    private BalanceWatcher() {}

    private static volatile boolean active = false;
    private static final String HANDLER_KEY = java.util.UUID.randomUUID().toString();

    /**
     * Map các UI-listener: key = tên screen định danh, value = callback nhận balance mới.
     * Dùng ConcurrentHashMap vì listener có thể được thêm/xóa từ FX thread trong khi
     * dispatch chạy từ background thread.
     */
    private static final Map<String, Consumer<Double>> listeners = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    //  Activate / Deactivate
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Kích hoạt BalanceWatcher — đăng ký 3 global event handler.
     * Gọi 1 lần ngay sau khi login thành công, sau BanWatcher.activate() và NotificationManager.activate().
     */
    public static synchronized void activate() {
        if (active) return;
        active = true;

        EventDispatcher.registerGlobal(EventType.BALANCE_UPDATED, HANDLER_KEY, BalanceWatcher::onBalanceEvent);
        EventDispatcher.registerGlobal(EventType.BID_DEDUCT,      HANDLER_KEY, BalanceWatcher::onBalanceEvent);
        EventDispatcher.registerGlobal(EventType.BID_CREDIT,      HANDLER_KEY, BalanceWatcher::onBalanceEvent);

        System.out.println("[BalanceWatcher] Đã kích hoạt — lắng nghe BALANCE_UPDATED, BID_DEDUCT, BID_CREDIT.");
    }

    /**
     * Hủy kích hoạt BalanceWatcher — gọi khi logout.
     * Tự động xóa toàn bộ UI-listener đã đăng ký.
     */
    public static synchronized void deactivate() {
        if (!active) return;
        active = false;

        EventDispatcher.unregisterGlobal(EventType.BALANCE_UPDATED, HANDLER_KEY);
        EventDispatcher.unregisterGlobal(EventType.BID_DEDUCT,      HANDLER_KEY);
        EventDispatcher.unregisterGlobal(EventType.BID_CREDIT,      HANDLER_KEY);

        listeners.clear();

        System.out.println("[BalanceWatcher] Đã hủy kích hoạt.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UI Listener API — dành cho các Controller
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Đăng ký callback UI để nhận thông báo khi balance thay đổi.
     *
     * <p>Callback chạy trên JavaFX Application Thread (do EventDispatcher bọc Platform.runLater).
     * Không cần gọi Platform.runLater thêm bên trong callback NẾU bạn chỉ cập nhật UI.
     * Nhưng nếu dùng nested lambda phức tạp, vẫn có thể bọc thêm cho an toàn.
     *
     * @param screenKey  Tên định danh duy nhất cho screen này (vd: "BidderDashboard", "BiddingRoom").
     *                   Dùng để unregister đúng listener khi rời màn hình.
     * @param onChanged  Callback nhận {@code double newBalance} khi balance thay đổi.
     */
    public static void registerListener(String screenKey, Consumer<Double> onChanged) {
        listeners.put(screenKey, onChanged);
        System.out.println("[BalanceWatcher] Screen '" + screenKey + "' đã đăng ký nhận balance updates.");
    }

    /**
     * Hủy đăng ký callback UI cho screen này.
     * Gọi khi rời màn hình (trong cleanup / navigate away) để tránh memory leak.
     *
     * @param screenKey Tên định danh đã dùng lúc {@link #registerListener}.
     */
    public static void unregisterListener(String screenKey) {
        listeners.remove(screenKey);
        System.out.println("[BalanceWatcher] Screen '" + screenKey + "' đã hủy đăng ký balance listener.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Internal event handler
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Xử lý chung cho cả 3 event: BALANCE_UPDATED, BID_DEDUCT, BID_CREDIT.
     * Tất cả đều mang field {@code "balance"} là số dư mới của user.
     *
     * <p>Chạy trên JavaFX Application Thread (Platform.runLater do EventDispatcher bọc).
     */
    private static void onBalanceEvent(JsonObject payload) {
        if (payload == null || !payload.has("balance") || payload.get("balance").isJsonNull()) {
            System.err.println("[BalanceWatcher] Nhận event nhưng thiếu field 'balance' — bỏ qua.");
            return;
        }

        try {
            double newBalance = payload.get("balance").getAsDouble();

            // 1. Luôn cập nhật UserSession — source of truth cho toàn app
            UserSession.getInstance().setBalance(newBalance);
            System.out.printf("[BalanceWatcher] Balance cập nhật: %,.0f ₫%n", newBalance);

            // 2. Notify tất cả UI-listener đang active
            for (Map.Entry<String, Consumer<Double>> entry : listeners.entrySet()) {
                try {
                    entry.getValue().accept(newBalance);
                } catch (Exception e) {
                    System.err.println("[BalanceWatcher] Lỗi listener '" + entry.getKey() + "': " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("[BalanceWatcher] Lỗi parse balance từ event: " + e.getMessage());
        }
    }
}