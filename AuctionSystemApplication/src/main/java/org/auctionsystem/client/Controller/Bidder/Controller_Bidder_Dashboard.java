package org.auctionsystem.client.Controller.Bidder;

import com.google.gson.JsonObject;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.event.ActionEvent;
import org.auctionsystem.client.Connectivity.ServerConnection;
import org.auctionsystem.client.Controller.Scene_Utils;
import org.auctionsystem.client.session.UserSession;

import java.io.IOException;

public class Controller_Bidder_Dashboard {
    private static final String Login_View          = "/org/auctionsystem/client/View/Login_scene.fxml";
    private static final String Bidder_Profile_View = "/org/auctionsystem/client/View/Bidder_Profile.fxml";
    private static final String Bidding_History_View = "/org/auctionsystem/client/View/Bidding_History.fxml";
    private static final String Searching_Room_View  = "/org/auctionsystem/client/View/Searching_room.fxml";

    private void switch_scene(ActionEvent event, String fxml_path) {
        try {
            Scene_Utils.Change_Scene(event, fxml_path);
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
                // [MỚI] Gửi LOGOUT lên server — xóa session khỏi SessionManager ngay lập tức
                // Trước đây chỉ chuyển màn hình, session vẫn còn trên server đến khi tự hết hạn
                JsonObject request = new JsonObject();
                request.addProperty("action", "LOGOUT");
                ServerConnection.sendAuthRequest(request);

                // [MỚI] Dừng session checker — trước đây checker vẫn chạy sau logout
                Scene_Utils.stopSessionChecker();

                // [MỚI] Clear toàn bộ UserSession phía client
                UserSession.getInstance().clear();

                switch_scene(event, Login_View);
            }
        });
    }

    @FXML
    public void Go_to_bidder_profile(ActionEvent event) {
        // Gửi GET_PROFILE để cập nhật dữ liệu mới nhất vào UserSession trước khi vào Profile
        // Profile sẽ đọc trực tiếp từ UserSession mà không cần gọi thêm request
        JsonObject request = new JsonObject();
        request.addProperty("action", "GET_PROFILE");
        request.addProperty("user_id", UserSession.getInstance().getUserId());
        JsonObject response = ServerConnection.sendAuthRequest(request);

        if (response != null && "success".equals(response.get("status").getAsString())) {
            com.google.gson.JsonObject info = response.get("information").getAsJsonObject();
            UserSession s = UserSession.getInstance();
            s.setBalance(info.get("balance").getAsDouble());
            s.setPhone(info.has("phone") && !info.get("phone").isJsonNull()
                    ? info.get("phone").getAsString() : null);
            s.setRating(info.has("rating") && !info.get("rating").isJsonNull()
                    ? info.get("rating").getAsDouble() : null);
        }

        switch_scene(event, Bidder_Profile_View);
    }

    @FXML
    public void Go_to_bidding_history(ActionEvent event) {
        switch_scene(event, Bidding_History_View);
    }

    @FXML
    public void Go_to_searching_room(ActionEvent event) {
        switch_scene(event, Searching_Room_View);
    }
}