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
    private static long lastPingTime=0;
    private static ScheduledExecutorService checkPingScheduler;
    private static Stage primaryStage;

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

        attachActivityListener(scene);

        Apply_Default_CSS_Style(scene);

        stage.setScene(scene);
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

    private static void attachActivityListener(Scene scene) {
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, e -> ping());
        scene.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> ping());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> ping());
    }

    private static void ping() {
        long now = System.currentTimeMillis();
        if (now - lastPingTime > 60 * 1000) {
            lastPingTime = now;
            JsonObject request = new JsonObject();
            request.addProperty("action",     "PING");
            request.addProperty("session_id", UserSession.getInstance().getSessionId());
            ServerConnection.sendRequest(request);
        }
    }

    public static void resetLastPingTime() {
        lastPingTime = System.currentTimeMillis();
    }

    public static void startSessionChecker() {
        stopSessionChecker();

        checkPingScheduler = Executors.newSingleThreadScheduledExecutor();
        checkPingScheduler.scheduleAtFixedRate(() -> {

            // Chưa login → bỏ qua
            if (UserSession.getInstance().getSessionId() == null) return;

            JsonObject request = new JsonObject();
            request.addProperty("action", "CHECK_PING");
            request.addProperty("session_id", UserSession.getInstance().getSessionId());

            JsonObject response = ServerConnection.sendRequest(request);

            if (response != null) {
                String status = response.get("status").getAsString();

                if ("expired".equals(status) || "error".equals(status)) {
                    Platform.runLater(() -> {
                        // Hiện popup thông báo
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle("Phiên đăng nhập hết hạn");
                        alert.setHeaderText(null);
                        alert.setContentText("Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại!");
                        alert.showAndWait();

                        // Clear session
                        UserSession.getInstance().clear();
                        stopSessionChecker();

                        // Chuyển về login
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

    public static void stopSessionChecker() {
        if (checkPingScheduler != null && !checkPingScheduler.isShutdown()) {
            checkPingScheduler.shutdown();
        }
    }
}
