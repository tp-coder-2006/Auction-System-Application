package org.auctionsystem.client.event;

import com.google.gson.JsonObject;
import javafx.application.Platform;
import org.auctionsystem.client.event.BalanceWatcher;
import org.auctionsystem.client.event.NotificationManager;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.auctionsystem.client.Connectivity.ServerConnection;
import org.auctionsystem.client.Controller.Scene_Utils;
import org.auctionsystem.client.session.UserSession;

/**
 * BanWatcher — Lắng nghe sự kiện BANNED từ server và xử lý đăng xuất tức thì.
 *
 * <p>Luồng hoạt động khi bị ban:
 * <pre>
 *   Server gửi JSON {"event":"BANNED","message":"..."}
 *       ↓
 *   ServerConnection.readerThread nhận → EventDispatcher.dispatch("BANNED", payload)
 *       ↓
 *   Platform.runLater → BanWatcher.handleBanned(payload)
 *       ↓
 *   Hiện Alert → user bấm OK
 *       ↓
 *   Clear UserSession → ServerConnection.disconnect() → chuyển về Login
 * </pre>
 *
 * <p>Stage được lấy từ {@link Scene_Utils#getPrimaryStage()} — đã được set
 * 1 lần duy nhất trong {@code Main.start()}. Không cần truyền Stage qua tham số.
 *
 * <p>Cách dùng trong {@code Controller_Login} sau khi login thành công:
 * <pre>{@code
 *   ServerConnection.connect();
 *   BanWatcher.activate();
 * }</pre>
 */
public final class BanWatcher {

    private BanWatcher() {}

    /** Trạng thái: đã kích hoạt chưa (tránh đăng ký trùng). */
    private static volatile boolean active = false;
    private static final String HANDLER_KEY = java.util.UUID.randomUUID().toString();

    // ─────────────────────────────────────────────────────────────────────────
    //  API công khai
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Kích hoạt lắng nghe sự kiện BANNED.
     * Gọi 1 lần ngay sau khi login thành công và {@code ServerConnection.connect()} đã được gọi.
     * Stage lấy tự động từ {@link Scene_Utils#getPrimaryStage()}.
     */
    public static synchronized void activate() {
        if (active) return; // Đã đăng ký rồi, không đăng ký lại
        active = true;
        EventDispatcher.registerGlobal(EventType.BANNED, HANDLER_KEY, BanWatcher::handleBanned);
        System.out.println("[BanWatcher] Đã kích hoạt — đang lắng nghe sự kiện BANNED.");
    }

    /**
     * Hủy kích hoạt (gọi khi user tự logout để dọn dẹp handler).
     * Không cần gọi khi bị ban vì {@code handleBanned} tự hủy đăng ký.
     */
    public static synchronized void deactivate() {
        if (!active) return;
        active = false;
        EventDispatcher.unregisterGlobal(EventType.BANNED, HANDLER_KEY);
        System.out.println("[BanWatcher] Đã hủy kích hoạt.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Xử lý nội bộ
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Xử lý sự kiện BANNED.
     * Chạy trên JavaFX Application Thread (do EventDispatcher bọc trong Platform.runLater).
     */
    private static void handleBanned(JsonObject payload) {
        // Hủy handler ngay lập tức để tránh kích hoạt nhiều lần
        active = false;
        EventDispatcher.unregisterGlobal(EventType.BANNED, HANDLER_KEY);

        // Trích xuất lý do ban từ payload (nếu có)
        String reason = "Tài khoản của bạn đã bị khóa bởi quản trị viên.";
        if (payload != null && payload.has("message") && !payload.get("message").isJsonNull()) {
            reason = payload.get("message").getAsString();
        }

        System.out.println("[BanWatcher] Tài khoản bị ban. Lý do: " + reason);

        // Lấy Stage từ Scene_Utils (đã set sẵn từ Main.start())
        Stage primaryStage = Scene_Utils.getPrimaryStage();

        // Hiện hộp thoại thông báo
        Alert alert = new Alert(Alert.AlertType.ERROR, reason, ButtonType.OK);
        alert.setTitle("Tài khoản bị khóa");
        alert.setHeaderText("Bạn đã bị khóa khỏi hệ thống!");
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.initStyle(StageStyle.DECORATED);
        if (primaryStage != null) {
            alert.initOwner(primaryStage);
        }

        alert.showAndWait(); // Block cho đến khi user bấm OK

        performForcedLogout(primaryStage);
    }

    /**
     * Thực hiện logout cưỡng bức:
     * xóa toàn bộ dữ liệu phiên và chuyển về màn hình Login.
     */
    private static void performForcedLogout(Stage primaryStage) {
        // 1. Hủy tất cả event handlers còn lại
        NotificationManager.deactivate();
        BalanceWatcher.deactivate();
        EventDispatcher.unregisterAllGlobal();

        // 2. Clear dữ liệu phiên phía client
        UserSession.getInstance().clear();

        // 3. Ngắt kết nối socket (không gửi LOGOUT vì server đã vô hiệu hóa session rồi)
        ServerConnection.disconnect();

        // 4. Chuyển về màn hình Login
        if (primaryStage == null) {
            System.err.println("[BanWatcher] Không có primaryStage — không thể chuyển màn hình.");
            return;
        }

        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    BanWatcher.class.getResource("/org/auctionsystem/client/View/Login_scene.fxml")
            );
            javafx.scene.Parent loginRoot = loader.load();
            javafx.scene.Scene loginScene = new javafx.scene.Scene(loginRoot);
            Scene_Utils.Apply_Default_CSS_Style(loginScene);

            primaryStage.setScene(loginScene);
            primaryStage.setMaximized(false);
            primaryStage.show();

            System.out.println("[BanWatcher] Đã chuyển về màn hình Login.");

        } catch (Exception e) {
            System.err.println("[BanWatcher] Lỗi khi chuyển về Login: " + e.getMessage());
            e.printStackTrace();
            Platform.exit();
        }
    }
}