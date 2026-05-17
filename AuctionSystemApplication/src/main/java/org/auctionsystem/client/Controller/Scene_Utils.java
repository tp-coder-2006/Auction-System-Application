package org.auctionsystem.client.Controller;

import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import org.auctionsystem.client.Connectivity.ServerConnection;
import org.auctionsystem.client.session.UserSession;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Scene_Utils {

    // [SỬA] Thêm primaryStage để dùng trong startSessionChecker mà không cần truyền tham số
    private static Stage primaryStage;

    // [SỬA] Thêm lastPingTime và checkPingScheduler cho cơ chế ping + session checker
    private static long lastPingTime = 0;
    private static ScheduledExecutorService checkPingScheduler;

    // [MỚI] Gọi 1 lần duy nhất trong Main.java khi khởi động app
    public static void setPrimaryStage(Stage stage) {
        primaryStage = stage;
    }

    @FXML
    public static void Change_Scene(ActionEvent event, String fxml_path) throws IOException {
        Parent root = FXMLLoader.load(Objects.requireNonNull(Scene_Utils.class.getResource(fxml_path)));
        Stage stage;

        if (event.getSource() instanceof Node) {
            stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        } else if (event.getSource() instanceof MenuItem) {
            stage = (Stage) ((MenuItem) event.getSource()).getParentPopup().getOwnerWindow();
        } else {
            throw new IllegalArgumentException();
        }

        Scene scene = new Scene(root);

        // [SỬA] Gắn event listener cho mọi scene mới để track hoạt động
        attachActivityListener(scene);
        Apply_Default_CSS_Style(scene);

        stage.setScene(scene);
        stage.show();
    }

    @FXML
    public static void set_up_search_logic(TextField search_bar, ListView<String> item_list, ObservableList<String> data) {
        FilteredList<String> filtered_data = new FilteredList<>(data, p -> true);
        search_bar.textProperty().addListener((observable, old_value, new_value) -> {
            filtered_data.setPredicate(item -> {
                if (new_value == null || new_value.isBlank()) {
                    return true;
                }
                return item.toLowerCase().contains(new_value.toLowerCase());
            });
            if (new_value == null || new_value.isBlank()) {
                item_list.setVisible(false);
                item_list.setManaged(false);
            } else {
                item_list.setVisible(true);
                item_list.setManaged(true);
            }
        });
        item_list.setItems(filtered_data);
    }

    public static void Apply_Default_CSS_Style(Scene scene) {
        if (scene == null) return;
        String default_css_path = "/org/auctionsystem/CSS/style.css";
        java.net.URL css_resource = Scene_Utils.class.getResource(default_css_path);
        if (css_resource != null) {
            String css_url_string = css_resource.toExternalForm();
            if (!scene.getStylesheets().contains(css_url_string)) {
                scene.getStylesheets().add(css_url_string);
            }
        } else {
            System.err.println("⚠️ Không tìm thấy file CSS tại: " + default_css_path);
        }
    }

    // [MỚI] Gắn event listener vào scene để track mọi hoạt động chuột/bàn phím
    private static void attachActivityListener(Scene scene) {
        scene.addEventFilter(MouseEvent.MOUSE_MOVED,   e -> ping());
        scene.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> ping());
        scene.addEventFilter(KeyEvent.KEY_PRESSED,     e -> ping());
    }

    // [MỚI] Gửi PING lên server mỗi 1 phút khi có hoạt động — giữ session sống
    private static void ping() {
        if (UserSession.getInstance().getSessionId() == null) return;
        long now = System.currentTimeMillis();
        if (now - lastPingTime > 60 * 1000) {
            lastPingTime = now;
            JsonObject request = new JsonObject();
            request.addProperty("action", "PING");
            ServerConnection.sendAuthRequest(request);
        }
    }

    // [MỚI] Reset lastPingTime — gọi ngay sau khi login thành công
    public static void resetLastPingTime() {
        lastPingTime = System.currentTimeMillis();
    }

    // [MỚI] Bắt đầu kiểm tra session định kỳ — gọi 1 lần sau khi login thành công
    // Không cần truyền Stage vì dùng primaryStage đã được lưu sẵn
    public static void startSessionChecker() {
        stopSessionChecker(); // dừng scheduler cũ nếu có

        checkPingScheduler = Executors.newSingleThreadScheduledExecutor();
        checkPingScheduler.scheduleAtFixedRate(() -> {

            // Chưa login → bỏ qua
            if (UserSession.getInstance().getSessionId() == null) return;

            JsonObject request = new JsonObject();
            request.addProperty("action", "CHECK_PING");
            JsonObject response = ServerConnection.sendAuthRequest(request);

            if (response != null) {
                String status = response.get("status").getAsString();

                // "expired" = session hết hạn, "error" = không có session_id
                if ("expired".equals(status) || "error".equals(status)) {
                    Platform.runLater(() -> {
                        // Hiện popup thông báo trước khi chuyển màn hình
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle("Phiên đăng nhập hết hạn");
                        alert.setHeaderText(null);
                        alert.setContentText("Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại!");
                        alert.showAndWait();

                        // Clear session và dừng checker
                        UserSession.getInstance().clear();
                        stopSessionChecker();

                        // Chuyển về màn hình login
                        try {
                            Parent root = FXMLLoader.load(
                                    Scene_Utils.class.getResource(
                                            "/org/auctionsystem/client/View/Login_scene.fxml"
                                    )
                            );
                            Scene scene = new Scene(root);
                            attachActivityListener(scene);
                            Apply_Default_CSS_Style(scene);
                            primaryStage.setScene(scene);
                            primaryStage.show();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                }
            }

        }, 2, 2, TimeUnit.MINUTES); // kiểm tra mỗi 2 phút
    }

    // [MỚI] Dừng session checker — gọi khi logout chủ động
    public static void stopSessionChecker() {
        if (checkPingScheduler != null && !checkPingScheduler.isShutdown()) {
            checkPingScheduler.shutdown();
        }
    }
}