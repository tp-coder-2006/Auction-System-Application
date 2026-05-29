package org.auctionsystem.client.Controller;

import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.animation.FadeTransition;
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
import javafx.util.Duration;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Scene_Utils {

    // [MỚI] Lưu primaryStage — dùng cho startSessionChecker() chuyển về login
    private static Stage primaryStage;

    // [MỚI] Track thời gian ping gần nhất — tránh ping quá nhiều
    private static long lastPingTime = 0;

    // [MỚI] Scheduler kiểm tra session định kỳ
    private static ScheduledExecutorService checkPingScheduler;

    // [MỚI] Gọi 1 lần duy nhất trong Main.start()
    public static void setPrimaryStage(Stage stage) {
        primaryStage = stage;
    }

    // Lưu kích thước stage gốc 1 lần duy nhất khi app khởi động
    private static double initialWidth = -1;
    private static double initialHeight = -1;

    // Gọi hàm này 1 lần trong Main.java sau stage.show()
    public static void Init_Stage_Size(Stage stage) {
        initialWidth = stage.getWidth();
        initialHeight = stage.getHeight();
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
        //Lưu trạng thái trước khi đổi scene
        boolean wasMaximized = stage.isMaximized();
        // Nếu không maximized thì lấy kích thước stage hiện tại
        // Nếu chưa từng resize thủ công thì dùng initialWidth/Height
        double targetWidth = stage.getWidth();
        double targetHeight = stage.getHeight();

        // Truyền kích thước vào Scene constructor — JavaFX tự tính stage size đúng
        Scene scene = new Scene(root);

        // [MỚI] Gắn activity listener cho mỗi scene mới
        attachActivityListener(scene);
        Apply_Default_CSS_Style(scene);

        // Làm trong suốt để transition diễn ra âm thầm, không flicker, không ẩn cửa sổ
        stage.setMaximized(false);
        stage.setOpacity(0);
        stage.setScene(scene);

        // Fade in sau khi scene đã sẵn sàng
        Platform.runLater(() -> {
            if (wasMaximized) {
                stage.setMaximized(true);
            } else {
                stage.setWidth(targetWidth);
                stage.setHeight(targetHeight);
            }

            stage.setOpacity(1);
            FadeTransition fade = new FadeTransition(Duration.millis(250), root);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.play();
        });
        stage.show();
    }

    @FXML
    public static void set_up_search_logic(TextField search_bar, ListView<String> item_list, ObservableList<String> data) {
        FilteredList<String> filtered_data = new FilteredList<>(data, p -> true);
        search_bar.textProperty().addListener((observable,old_value,new_value) -> {
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
        if (scene == null) {
            return;
        }
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

    // [MỚI] Gắn event listener — track hoạt động chuột/bàn phím
    private static void attachActivityListener(Scene scene) {
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, e -> ping());
        scene.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> ping());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> ping());
    }

    // [MỚI] Gửi PING lên server mỗi 1 phút khi có hoạt động
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

    // [MỚI] Bắt đầu kiểm tra session định kỳ mỗi 2 phút
    public static void startSessionChecker() {
        stopSessionChecker();

        // [MỚI] setDaemon(true) — khi JavaFX tắt, thread này tự tắt theo
        // Không setDaemon → JVM không tắt được dù user đã đóng cửa sổ
        checkPingScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread t = new Thread(runnable, "session-checker-thread");
            t.setDaemon(true);
            return t;
        });
        checkPingScheduler.scheduleAtFixedRate(() -> {

            if (UserSession.getInstance().getSessionId() == null) return;

            JsonObject request = new JsonObject();
            request.addProperty("action", "CHECK_PING");
            JsonObject response = ServerConnection.sendAuthRequest(request);

            if (response != null) {
                String status = response.get("status").getAsString();
                if ("expired".equals(status) || "error".equals(status)) {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle("Phiên đăng nhập hết hạn");
                        alert.setHeaderText(null);
                        alert.setContentText("Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại!");
                        alert.showAndWait();

                        UserSession.getInstance().clear();
                        stopSessionChecker();

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

        }, 2, 2, TimeUnit.MINUTES);
    }

    // [MỚI] Dừng session checker — gọi khi logout hoặc app đóng
    public static void stopSessionChecker() {
        if (checkPingScheduler != null && !checkPingScheduler.isShutdown()) {
            checkPingScheduler.shutdown();
        }
    }
    /*
    @FXML
private void openHelp(ActionEvent event) throws IOException {
    Parent root = FXMLLoader.load(getClass().getResource("/org/auctionsystem/client/View/Help_scene.fxml"));
    Stage helpStage = new Stage();
    helpStage.setTitle("Hướng dẫn sử dụng");
    helpStage.setScene(new Scene(root));
    helpStage.initModality(Modality.APPLICATION_MODAL); // khóa cửa sổ chính lại
    helpStage.show();
}
     */
}