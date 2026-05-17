package org.auctionsystem.client.Controller.Bidder;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

import javafx.event.ActionEvent;

// ╔══════════════════════════════════════════════════════════════════════╗
// ║  [MỚI] Thêm 2 import bên dưới để hỗ trợ gửi LOGOUT lên server     ║
// ║  và xóa session cục bộ khi đăng xuất                               ║
// ╚══════════════════════════════════════════════════════════════════════╝
import com.google.gson.JsonObject;
import org.auctionsystem.client.Connectivity.ServerConnection;
import org.auctionsystem.client.session.UserSession;
// ══════════════════════════════════════════════════════════════════════

import java.io.IOException;

public class Controller_Bidder_Dashboard {
    private static final String Login_View = "/org/auctionsystem/client/View/Login_scene.fxml";
    private static final String Profile_View = "/org/auctionsystem/client/View/Profile.fxml";
    private static final String Bidding_History_View = "/org/auctionsystem/client/View/Bidding_History.fxml";

    private void switch_scene(ActionEvent event, String fxml_path) {
        try {
            org.auctionsystem.client.Controller.Scene_Utils.Change_Scene(event, fxml_path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void Logging_out(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Đăng xuất");
        alert.setHeaderText("Bạn chuẩn bị đăng xuất khỏi tài khoản này.");
        alert.setContentText("Bạn có chắc chắn muốn đăng xuất?");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {

                // ╔══════════════════════════════════════════════════════════════╗
                // ║  [MỚI] Gửi action LOGOUT lên server để xóa session          ║
                // ║                                                              ║
                // ║  Trước đây: chỉ gọi switch_scene() chuyển màn hình,         ║
                // ║  session vẫn còn tồn tại trong ConcurrentHashMap trên server ║
                // ║  cho đến khi tự hết hạn sau 30 phút.                        ║
                // ║                                                              ║
                // ║  Sau khi sửa: server nhận LOGOUT → gọi                      ║
                // ║  SessionManager.removeSession() → xóa khỏi map ngay.        ║
                // ║  sendAuthRequest() tự động đính kèm session_id hiện tại.    ║
                // ╚══════════════════════════════════════════════════════════════╝
                JsonObject request = new JsonObject();
                request.addProperty("action", "LOGOUT");
                ServerConnection.sendAuthRequest(request); // [MỚI] gửi LOGOUT lên server
                // ══════════════════════════════════════════════════════════════

                // ╔══════════════════════════════════════════════════════════════╗
                // ║  [MỚI] Xóa toàn bộ dữ liệu session cục bộ (phía client)    ║
                // ║                                                              ║
                // ║  Trước đây: UserSession singleton giữ nguyên dữ liệu        ║
                // ║  (sessionId, userId, username, role, balance) sau khi       ║
                // ║  đăng xuất — có thể bị tái sử dụng không mong muốn.         ║
                // ╚══════════════════════════════════════════════════════════════╝
                UserSession.getInstance().clear();
                // ══════════════════════════════════════════════════════════════

                switch_scene(event, Login_View);
            }
        });
    }

    @FXML
    public void Go_to_profile(ActionEvent event) {
        switch_scene(event, Profile_View);
    }

    @FXML
    public void Go_to_bidding_history(ActionEvent event) {
        switch_scene(event, Bidding_History_View);
    }

    @FXML
    private TextField search_bar;
    @FXML
    private ListView<String> item_list;
    private ObservableList<String> data = FXCollections.observableArrayList("Java", "Python", "C++");

    @FXML
    public void initialize() {
        org.auctionsystem.client.Controller.Scene_Utils.set_up_search_logic(search_bar, item_list, data);
    }
}