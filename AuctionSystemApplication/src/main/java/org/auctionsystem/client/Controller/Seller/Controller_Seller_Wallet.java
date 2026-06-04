package org.auctionsystem.client.Controller.Seller;

import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.auctionsystem.client.Connectivity.ServerConnection;
import org.auctionsystem.client.Controller.Scene_Utils;
import org.auctionsystem.client.event.BalanceWatcher;
import org.auctionsystem.client.session.UserSession;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Controller_Seller_Wallet
 *
 * Màn hình ví dành cho Seller: chỉ có chức năng rút tiền (tiền từ bán đấu giá).
 * Lắng nghe BALANCE_UPDATED để cập nhật số dư real-time.
 *
 * FXML: Seller_Wallet.fxml
 */
public class Controller_Seller_Wallet {

    // ── FXML bindings ─────────────────────────────────────────────────────────
    @FXML private Label     lbl_balance;
    @FXML private TextField field_withdraw_amount;
    @FXML private TextField field_withdraw_note;
    @FXML private Button    btn_withdraw;
    @FXML private Label     lbl_withdraw_status;

    // ── Hằng số ───────────────────────────────────────────────────────────────
    private static final NumberFormat VND_FORMAT =
            NumberFormat.getNumberInstance(new Locale("vi", "VN"));
    private static final String STATUS_SUCCESS_STYLE =
            "-fx-font-size: 12px; -fx-text-fill: #27ae60; -fx-font-weight: bold;";
    private static final String STATUS_ERROR_STYLE =
            "-fx-font-size: 12px; -fx-text-fill: #e74c3c; -fx-font-weight: bold;";
    private static final String STATUS_LOADING_STYLE =
            "-fx-font-size: 12px; -fx-text-fill: #888;";

    private static final String SELLER_WALLET_VIEW =
            "/org/auctionsystem/client/View/Seller_Wallet.fxml";
    private static final String SELLER_HISTORY_VIEW =
            "/org/auctionsystem/client/View/Seller_Transaction_History.fxml";
    private static final String SELLER_DASHBOARD_VIEW =
            "/org/auctionsystem/client/View/Seller_Dashboard.fxml";

    // ── Khởi tạo ──────────────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        refreshBalanceLabel();
        // Lắng nghe balance updates qua BalanceWatcher (global singleton)
        BalanceWatcher.registerListener("SellerWallet", balance ->
                Platform.runLater(this::refreshBalanceLabel));
    }

    private void refreshBalanceLabel() {
        double balance = UserSession.getInstance().getBalance();
        lbl_balance.setText(formatVnd(balance) + " ₫");
    }

    // ── Rút tiền ──────────────────────────────────────────────────────────────
    @FXML
    private void onWithdraw(ActionEvent event) {
        double amount = parseAmount(field_withdraw_amount);
        if (amount <= 0) {
            showStatus("⚠ Vui lòng nhập số tiền hợp lệ (> 0).", false);
            return;
        }
        double currentBalance = UserSession.getInstance().getBalance();
        if (amount > currentBalance) {
            showStatus("⚠ Số dư không đủ. Số dư hiện tại: " + formatVnd(currentBalance) + " ₫", false);
            return;
        }

        String note = field_withdraw_note.getText().trim();
        if (note.isEmpty()) note = "Rút tiền";

        setFormEnabled(false);
        showStatus("Đang xử lý...", null);

        final String finalNote = note;
        final double finalAmount = amount;

        new Thread(() -> {
            JsonObject request = new JsonObject();
            request.addProperty("action", "WITHDRAW");
            request.addProperty("user_id", UserSession.getInstance().getUserId());
            request.addProperty("amount", finalAmount);
            request.addProperty("note", finalNote);

            JsonObject response = ServerConnection.sendAuthRequest(request);

            Platform.runLater(() -> {
                setFormEnabled(true);
                if (isSuccess(response)) {
                    showStatus("✔ Rút " + formatVnd(finalAmount) + " ₫ thành công!", true);
                    field_withdraw_amount.clear();
                    field_withdraw_note.clear();
                    if (response.has("new_balance")) {
                        UserSession.getInstance().setBalance(response.get("new_balance").getAsDouble());
                    }
                    refreshBalanceLabel();
                } else {
                    String msg = getErrorMessage(response,
                            "Rút tiền thất bại. Số dư không đủ hoặc lỗi server.");
                    showStatus("✖ " + msg, false);
                }
            });
        }, "SellerWallet-Withdraw").start();
    }

    // ── Nút nhanh ─────────────────────────────────────────────────────────────
    @FXML private void onWithdrawQuick100(ActionEvent e) { field_withdraw_amount.setText("100000"); }
    @FXML private void onWithdrawQuick500(ActionEvent e) { field_withdraw_amount.setText("500000"); }
    @FXML private void onWithdrawQuick1M(ActionEvent e)  { field_withdraw_amount.setText("1000000"); }
    @FXML private void onWithdrawQuick5M(ActionEvent e)  { field_withdraw_amount.setText("5000000"); }

    @FXML
    private void onWithdrawAll(ActionEvent event) {
        double balance = UserSession.getInstance().getBalance();
        field_withdraw_amount.setText(String.valueOf((long) balance));
    }

    // ── Điều hướng ────────────────────────────────────────────────────────────
    @FXML
    private void onRefreshBalance(ActionEvent event) {
        Platform.runLater(this::refreshBalanceLabel);
    }

    @FXML
    private void onViewHistory(ActionEvent event) {
        // Truyền đường dẫn màn hình hiện tại để nút "Quay lại" trong History hoạt động đúng
        Controller_Seller_Transaction_History.setPreviousView(SELLER_WALLET_VIEW);
        try {
            BalanceWatcher.unregisterListener("SellerWallet");
            Scene_Utils.Change_Scene(event, SELLER_HISTORY_VIEW);
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Không thể mở lịch sử giao dịch.");
        }
    }

    @FXML
    private void onBack(ActionEvent event) {
        try {
            BalanceWatcher.unregisterListener("SellerWallet");
            Scene_Utils.Change_Scene(event, SELLER_DASHBOARD_VIEW);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private void setFormEnabled(boolean enabled) {
        btn_withdraw.setDisable(!enabled);
        field_withdraw_amount.setDisable(!enabled);
        field_withdraw_note.setDisable(!enabled);
    }

    private double parseAmount(TextField field) {
        try {
            String text = field.getText().trim().replace(",", "").replace(".", "");
            double value = Double.parseDouble(text);
            return value > 0 ? value : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String formatVnd(double amount) {
        return VND_FORMAT.format((long) amount);
    }

    private void showStatus(String message, Boolean success) {
        lbl_withdraw_status.setText(message);
        if (success == null)       lbl_withdraw_status.setStyle(STATUS_LOADING_STYLE);
        else if (success)          lbl_withdraw_status.setStyle(STATUS_SUCCESS_STYLE);
        else                       lbl_withdraw_status.setStyle(STATUS_ERROR_STYLE);
    }

    private boolean isSuccess(JsonObject response) {
        return response != null
                && response.has("status")
                && !response.get("status").isJsonNull()
                && "success".equals(response.get("status").getAsString());
    }

    private String getErrorMessage(JsonObject response, String defaultMsg) {
        if (response != null && response.has("message")
                && !response.get("message").isJsonNull()
                && response.get("message").isJsonPrimitive()) {
            return response.get("message").getAsString();
        }
        return defaultMsg;
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Lỗi");
        alert.setContentText(msg);
        alert.showAndWait();
    }
}