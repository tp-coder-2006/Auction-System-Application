package org.auctionsystem.client.Controller.Seller;

import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import org.auctionsystem.client.Connectivity.ServerConnection;
import org.auctionsystem.client.Controller.Scene_Utils;
import org.auctionsystem.client.event.BanWatcher;
import org.auctionsystem.client.event.NotificationManager;
import org.auctionsystem.client.event.EventDispatcher;
import org.auctionsystem.client.event.EventType;
import org.auctionsystem.client.session.UserSession;

import java.io.IOException;

public class Controller_Seller_Dashboard {

    @FXML private Label  lbl_balance;
    @FXML private Button btn_add_item;

    private static final String Login_View           = "/org/auctionsystem/client/View/Login_scene.fxml";
    private static final String Profile_View         = "/org/auctionsystem/client/View/Seller_Profile.fxml";
    private static final String Selling_History_View = "/org/auctionsystem/client/View/Selling_History.fxml";
    private static final String My_Items_View        = "/org/auctionsystem/client/View/My_Items.fxml";
    private static final String Add_Item_View        = "/org/auctionsystem/client/View/Add_Item.fxml";
    private static final String Seller_Wallet_View   = "/org/auctionsystem/client/View/Seller_Wallet.fxml";
    private static final String Search_User_View     = "/org/auctionsystem/client/View/Search_User_Seller.fxml";

    // ─────────────────────────────────────────────────────────────────────────
    //  Khởi tạo
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        updateBalanceLabel(UserSession.getInstance().getBalance());

        // Bind nút đăng sản phẩm (không có onAction trong FXML)
        if (btn_add_item != null)
            btn_add_item.setOnAction(this::Go_to_add_item);

        // Real-time: cập nhật số dư khi nạp/rút/bid
        EventDispatcher.register(EventType.BALANCE_UPDATED, this::onBalanceUpdated);
        EventDispatcher.register(EventType.BID_CREDIT,      this::onBalanceUpdated);
    }

    private void onBalanceUpdated(JsonObject payload) {
        try {
            double newBal = payload.get("balance").getAsDouble();
            UserSession.getInstance().setBalance(newBal);
            Platform.runLater(() -> updateBalanceLabel(newBal));
        } catch (Exception e) {
            System.err.println("[SellerDashboard] Lỗi parse balance: " + e.getMessage());
        }
    }

    private void updateBalanceLabel(double balance) {
        if (lbl_balance != null)
            lbl_balance.setText("Số dư tài khoản của bạn: " + String.format("%,.0f ₫", balance));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Điều hướng
    // ─────────────────────────────────────────────────────────────────────────

    private void switch_scene(ActionEvent event, String fxml) {
        try { Scene_Utils.Change_Scene(event, fxml); }
        catch (IOException e) { e.printStackTrace(); }
    }

    private void unregister() {
        EventDispatcher.unregister(EventType.BALANCE_UPDATED);
        EventDispatcher.unregister(EventType.BID_CREDIT);
    }

    @FXML
    public void Go_to_profile(ActionEvent event) {
        unregister();
        new Thread(() -> {
            JsonObject req = new JsonObject();
            req.addProperty("action",  "GET_PROFILE");
            req.addProperty("user_id", UserSession.getInstance().getUserId());
            JsonObject res = ServerConnection.sendAuthRequest(req);

            if (res != null && "success".equals(res.get("status").getAsString())) {
                JsonObject info = res.get("information").getAsJsonObject();
                UserSession s = UserSession.getInstance();
                s.setName(info.get("name").getAsString());
                s.setEmail(info.get("email").getAsString());
                s.setBalance(info.get("balance").getAsDouble());
                s.setPhone(info.has("phone") && !info.get("phone").isJsonNull()
                        ? info.get("phone").getAsString() : null);
                s.setRating(info.has("rating") && !info.get("rating").isJsonNull()
                        ? info.get("rating").getAsDouble() : null);
            }
            Platform.runLater(() -> switch_scene(event, Profile_View));
        }, "Nav-SellerProfile").start();
    }

    @FXML
    public void Go_to_selling_history(ActionEvent event) {
        unregister();
        switch_scene(event, Selling_History_View);
    }

    @FXML
    public void Go_to_wallet(ActionEvent event) {
        unregister();
        switch_scene(event, Seller_Wallet_View);
    }

    @FXML
    public void Go_to_my_items_scene(ActionEvent event) {
        unregister();
        switch_scene(event, My_Items_View);
    }

    public void Go_to_add_item(ActionEvent event) {
        unregister();
        switch_scene(event, Add_Item_View);
    }

    @FXML
    public void Go_to_search_user(ActionEvent event) {
        unregister();
        switch_scene(event, Search_User_View);
    }

    @FXML
    public void Logging_out(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Đăng xuất");
        alert.setHeaderText("Bạn chuẩn bị đăng xuất.");
        alert.setContentText("Bạn có chắc chắn muốn đăng xuất?");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                JsonObject req = new JsonObject();
                req.addProperty("action", "LOGOUT");
                ServerConnection.sendAuthRequest(req);

                BanWatcher.deactivate();
                NotificationManager.deactivate();
                EventDispatcher.unregisterAll();
                UserSession.getInstance().clear();
                ServerConnection.disconnect();
                switch_scene(event, Login_View);
            }
        });
    }
}