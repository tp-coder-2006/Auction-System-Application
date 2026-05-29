package org.auctionsystem.client.Controller.Admin;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import org.auctionsystem.client.Controller.Scene_Utils;

import java.io.IOException;

public class Controller_Admin_Financial_Auditing {
    @FXML private ComboBox<String> ComboBox_TransactionType;
    @FXML
    public void initialize() {
        ComboBox_TransactionType.getItems().addAll(
                "Tất cả giao dịch",
                "Nạp tiền",
                "Rút tiền"
        );
        ComboBox_TransactionType.setValue("Tất cả giao dịch");
    }
    @FXML
    public void back_to_admin_dashboard(ActionEvent event) {
        try {
            Scene_Utils.Change_Scene(event, "/org/auctionsystem/client/View/Admin_Dashboard.fxml");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
