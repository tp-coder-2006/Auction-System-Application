package org.auctionsystem.client.Controller.Bidder;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import org.auctionsystem.client.Controller.Scene_Utils;

import java.io.IOException;

public class Controller_Searching_room {
    //điều kiện để tìm kiếm sản phẩm: nhập tên sản phẩm + chọn loại sản phẩm
    //thiếu 1 trong 2 là không được
    @FXML
    public void Back_to_Dashboard(ActionEvent event) {
        try {
            Scene_Utils.Change_Scene(event, "/org/auctionsystem/client/View/Bidder_Dashboard.fxml");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
