package org.auctionsystem.client.Controller.Admin;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import org.auctionsystem.client.Controller.Scene_Utils;

import java.io.IOException;

public class Controller_Admin_Item_Approval {
    @FXML
    public void back_to_admin_dashboard(ActionEvent event) {
        try {
            Scene_Utils.Change_Scene(event, "/org/auctionsystem/client/View/Admin_Dashboard.fxml");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
