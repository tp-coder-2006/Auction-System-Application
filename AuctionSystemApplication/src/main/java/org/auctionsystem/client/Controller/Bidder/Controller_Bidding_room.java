package org.auctionsystem.client.Controller.Bidder;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import org.auctionsystem.client.Controller.Scene_Utils;

import java.io.IOException;

public class Controller_Bidding_room {
    @FXML
    public void back_to_searching_room(ActionEvent event) {
        try {
            Scene_Utils.Change_Scene(event, "/org/auctionsystem/client/View/Searching_room.fxml");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
