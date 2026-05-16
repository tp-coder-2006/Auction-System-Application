package org.auctionsystem.client.Controller.Bidder;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import javafx.event.ActionEvent;

import java.io.IOException;

public class Controller_Bidder_Dashboard {
    private static final String Login_View = "/org/auctionsystem/client/View/Login_scene.fxml";
    private static final String Profile_View = "/org/auctionsystem/client/View/Bidder_Profile.fxml";
    private static final String Bidding_History_View = "/org/auctionsystem/client/View/Bidding_History.fxml";
    private static final String Searching_Room_View = "/org/auctionsystem/client/View/Searching_room.fxml";

    private void switch_scene(ActionEvent event, String fxml_path) {
        try {
            org.auctionsystem.client.Controller.Scene_Utils.Change_Scene(event, fxml_path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML //Nếu đăng xuất ra khỏi tài khoản
    public void Logging_out(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Đăng xuất");
        alert.setHeaderText("Bạn chuẩn bị đăng xuất khỏi tài khoản này.");
        alert.setContentText("Bạn có chắc chắn muốn đăng xuất?");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                switch_scene(event, Login_View);
            }
        });
    }

    @FXML //Đến trang hồ sơ
    public void Go_to_profile(ActionEvent event) {
        switch_scene(event, Profile_View);
    }

    @FXML //Đến trang chứa lịch sử đấu giá
    public void Go_to_bidding_history(ActionEvent event) {
        switch_scene(event, Bidding_History_View);
    }

    @FXML
    public void Go_to_searching_room(ActionEvent event) {
        switch_scene(event, Searching_Room_View);
    }
    //bỏ thanh tìm kiếm trên trang chủ, thay vào đó thì để thanh tìm kiếm ở màn hình Searching_room

    /*
    @FXML //Thanh tìm kiếm trên trang chủ
    private TextField search_bar;
    @FXML
    private ListView<String> item_list;
    private ObservableList<String> data = FXCollections.observableArrayList("Java","Python","C++");
    // Ghi tạm để chỉnh sửa sau
    @FXML
    public void initialize() {
        org.auctionsystem.client.Controller.Scene_Utils.set_up_search_logic(search_bar, item_list, data);
    }
*/
}