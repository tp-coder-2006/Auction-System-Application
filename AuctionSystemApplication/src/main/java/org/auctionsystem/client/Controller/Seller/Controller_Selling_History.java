package org.auctionsystem.client.Controller.Seller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import org.auctionsystem.client.Controller.Scene_Utils;

import java.io.IOException;

public class Controller_Selling_History {
    @FXML
    public void back_to_seller_dashboard(ActionEvent event) {
        try {
            Scene_Utils.Change_Scene(event, "/org/auctionsystem/client/View/Seller_Dashboard.fxml");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
