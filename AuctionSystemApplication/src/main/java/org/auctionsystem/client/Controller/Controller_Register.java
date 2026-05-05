package org.auctionsystem.client.Controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;

import java.io.IOException;

public class Controller_Register {
    @FXML
    public void Registering(ActionEvent event) throws IOException {
        Scene_Utils.Change_Scene(event, "/org/auctionsystem/client/View/Bidder_Dashboard.fxml");
    }
}
