package org.auctionsystem.client.Controller.Seller;

import com.google.gson.JsonObject;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.auctionsystem.client.Connectivity.ServerConnection;
import org.auctionsystem.client.Controller.Scene_Utils;
import org.auctionsystem.client.session.UserSession;

import java.io.IOException;

public class Controller_Seller_Dashboard {
    private static final String Login_View   = "/org/auctionsystem/client/View/Login_scene.fxml";
    private static final String Profile_View = "/org/auctionsystem/client/View/Seller_Profile.fxml";

    private void switch_scene(ActionEvent event, String fxml_path) {
        try {
            Scene_Utils.Change_Scene(event, fxml_path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void Go_to_profile(ActionEvent event) {
        // [SỬA] Gửi GET_PROFILE trước khi chuyển màn hình để cập nhật balance mới nhất
        // Trước đây chuyển thẳng sang Profile mà không đồng bộ dữ liệu từ server
        JsonObject request = new JsonObject();
        request.addProperty("action", "GET_PROFILE");
        request.addProperty("user_id", UserSession.getInstance().getUserId());
        JsonObject response = ServerConnection.sendAuthRequest(request);

        if (response != null && "success".equals(response.get("status").getAsString())) {
            // Cập nhật toàn bộ thông tin mới nhất từ server vào client UserSession
            // để màn hình Profile đọc trực tiếp mà không cần gọi thêm request
            com.google.gson.JsonObject info = response.get("information").getAsJsonObject();

            UserSession s = UserSession.getInstance();
            // name, email, balance không thể null — bắt buộc khi đăng ký
            s.setBalance(info.get("balance").getAsDouble());
            // phone nullable — không bắt buộc khi đăng ký
            s.setPhone(info.has("phone") && !info.get("phone").isJsonNull()
                    ? info.get("phone").getAsString() : null);
            // rating nullable — chỉ có ở Seller
            s.setRating(info.has("rating") && !info.get("rating").isJsonNull()
                    ? info.get("rating").getAsDouble() : null);
        }

        switch_scene(event, Profile_View);
    }

    @FXML
    public void Logging_out(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Đăng xuất");
        alert.setHeaderText("Bạn chuẩn bị đăng xuất khỏi tài khoản này.");
        alert.setContentText("Bạn có chắc chắn muốn đăng xuất?");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // [SỬA] Gửi LOGOUT lên server để xóa session khỏi SessionManager ngay lập tức
                // Trước đây chỉ chuyển màn hình, session vẫn còn trên server đến khi tự hết hạn
                JsonObject request = new JsonObject();
                request.addProperty("action", "LOGOUT");
                ServerConnection.sendAuthRequest(request);

                // [SỬA] Dừng session checker — trước đây không có, checker vẫn chạy sau logout
                Scene_Utils.stopSessionChecker();

                // [SỬA] Clear toàn bộ UserSession phía client
                // Trước đây không clear, dữ liệu cũ còn tồn tại trong singleton
                UserSession.getInstance().clear();

                switch_scene(event, Login_View);
            }
        });
    }
}