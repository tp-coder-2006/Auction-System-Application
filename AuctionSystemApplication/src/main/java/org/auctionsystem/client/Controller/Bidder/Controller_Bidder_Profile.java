package org.auctionsystem.client.Controller.Bidder;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import org.auctionsystem.client.Controller.Scene_Utils;

import java.io.IOException;

public class Controller_Bidder_Profile {
    @FXML
    public void back_to_bidder_dashboard(ActionEvent event) {
        try {
            Scene_Utils.Change_Scene(event, "/org/auctionsystem/client/View/Bidder_Dashboard.fxml");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
