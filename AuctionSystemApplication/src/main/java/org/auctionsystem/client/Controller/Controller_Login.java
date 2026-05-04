package org.auctionsystem.client.Controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;

import java.io.IOException;

public class Controller_Login {
    @FXML
    public void Logging_in(ActionEvent event) throws IOException {
        Scene_Utils.Change_Scene(event, "/org/auctionsystem/View/Bidder_Dashboard.fxml");
    }
    @FXML
    private void Switching_to_register_scene(ActionEvent event) throws IOException {
        Scene_Utils.Change_Scene(event,"/org/auctionsystem/View/Register_scene.fxml");
    }
}
