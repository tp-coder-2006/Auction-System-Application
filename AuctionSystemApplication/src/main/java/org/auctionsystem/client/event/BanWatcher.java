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
 * BanWatcher — Lắng nghe 2 sự kiện cưỡng bức đăng xuất từ server:
 *
 * <ul>
 *   <li>{@link EventType#BANNED}  — Admin khóa tài khoản: hiện lỗi "Tài khoản bị khóa".</li>
 *   <li>{@link EventType#KICKED}  — Đăng nhập ở nơi khác: hiện cảnh báo "Phiên bị kết thúc".</li>
 * </ul>
 *
 * <p>Luồng hoạt động:
 * <pre>
 *   Server gửi JSON {"event":"BANNED"|"KICKED","message":"..."}
 *       ↓
 *   ServerConnection.readerThread → EventDispatcher.dispatch(eventType, payload)
 *       ↓
 *   Platform.runLater → BanWatcher.handleBanned / handleKicked
 *       ↓
 *   Hiện Alert → user bấm OK → performForcedLogout()
 * </pre>
 */
public final class BanWatcher {

    private BanWatcher() {}

    private static volatile boolean active = false;
    private static final String BANNED_KEY = java.util.UUID.randomUUID().toString();
    private static final String KICKED_KEY = java.util.UUID.randomUUID().toString();

    // ─────────────────────────────────────────────────────────────────────────
    //  API công khai
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Kích hoạt lắng nghe cả BANNED và KICKED.
     * Gọi 1 lần ngay sau khi login thành công.
     */
    public static synchronized void activate() {
        if (active) return;
        active = true;
        EventDispatcher.registerGlobal(EventType.BANNED, BANNED_KEY, BanWatcher::handleBanned);
        EventDispatcher.registerGlobal(EventType.KICKED, KICKED_KEY, BanWatcher::handleKicked);
        System.out.println("[BanWatcher] Đã kích hoạt — lắng nghe BANNED và KICKED.");
    }

    /**
     * Hủy kích hoạt (gọi khi user tự logout).
     */
    public static synchronized void deactivate() {
        if (!active) return;
        active = false;
        EventDispatcher.unregisterGlobal(EventType.BANNED, BANNED_KEY);
        EventDispatcher.unregisterGlobal(EventType.KICKED, KICKED_KEY);
        System.out.println("[BanWatcher] Đã hủy kích hoạt.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Xử lý BANNED — admin khóa tài khoản
    // ─────────────────────────────────────────────────────────────────────────

    private static void handleBanned(JsonObject payload) {
        unregisterAll();

        String message = extractMessage(payload, "Tài khoản của bạn đã bị khóa bởi quản trị viên.");
        System.out.println("[BanWatcher] Tài khoản bị ban. Lý do: " + message);

        Stage primaryStage = Scene_Utils.getPrimaryStage();

        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle("Tài khoản bị khóa");
        alert.setHeaderText("Tài khoản của bạn đã bị khóa!");
        configureAlert(alert, primaryStage);
        alert.showAndWait();

        performForcedLogout(primaryStage);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Xử lý KICKED — đăng nhập ở nơi khác
    // ─────────────────────────────────────────────────────────────────────────

    private static void handleKicked(JsonObject payload) {
        unregisterAll();

        String message = extractMessage(payload, "Tài khoản của bạn vừa đăng nhập ở nơi khác. Phiên này đã bị kết thúc.");
        System.out.println("[BanWatcher] Bị kick do đăng nhập trùng. Thông báo: " + message);

        Stage primaryStage = Scene_Utils.getPrimaryStage();

        Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
        alert.setTitle("Phiên đăng nhập bị kết thúc");
        alert.setHeaderText("Bạn đã đăng nhập ở thiết bị khác!");
        configureAlert(alert, primaryStage);
        alert.showAndWait();

        performForcedLogout(primaryStage);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Dùng chung
    // ─────────────────────────────────────────────────────────────────────────

    private static synchronized void unregisterAll() {
        if (!active) return;
        active = false;
        EventDispatcher.unregisterGlobal(EventType.BANNED, BANNED_KEY);
        EventDispatcher.unregisterGlobal(EventType.KICKED, KICKED_KEY);
    }

    private static String extractMessage(JsonObject payload, String defaultMsg) {
        if (payload != null && payload.has("message") && !payload.get("message").isJsonNull()) {
            return payload.get("message").getAsString();
        }
        return defaultMsg;
    }

    private static void configureAlert(Alert alert, Stage primaryStage) {
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.initStyle(StageStyle.DECORATED);
        if (primaryStage != null) {
            alert.initOwner(primaryStage);
        }
    }

    private static void performForcedLogout(Stage primaryStage) {
        NotificationManager.deactivate();
        BalanceWatcher.deactivate();
        EventDispatcher.unregisterAllGlobal();

        UserSession.getInstance().clear();
        ServerConnection.disconnect();

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