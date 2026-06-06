package org.auctionsystem.client.Controller.Bidder;

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
 * Controller_Wallet_Transaction
 * <p>
 * Màn hình cho phép Bidder nạp tiền (DEPOSIT) và rút tiền (WITHDRAW).
 * Giao tiếp với server qua action "DEPOSIT" / "WITHDRAW".
 * Lắng nghe event BALANCE_UPDATED để cập nhật số dư real-time.
 * <p>
 * FXML: Wallet_Transaction.fxml
 */
public class Controller_Wallet_Transaction {
    // UUID duy nhất cho mỗi instance — tránh ghi đè handler của cửa sổ khác
    private final String handlerKey = java.util.UUID.randomUUID().toString();


    // ── FXML bindings ─────────────────────────────────────────────────────────

    @FXML
    private Label lbl_balance;

    // Nạp tiền
    @FXML
    private TextField field_deposit_amount;
    @FXML
    private TextField field_deposit_note;
    @FXML
    private Button btn_deposit;
    @FXML
    private Label lbl_deposit_status;

    // Rút tiền
    @FXML
    private TextField field_withdraw_amount;
    @FXML
    private TextField field_withdraw_note;
    @FXML
    private Button btn_withdraw;
    @FXML
    private Label lbl_withdraw_status;

    // ── Hằng số ───────────────────────────────────────────────────────────────

    private static final NumberFormat VND_FORMAT =
            NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    private static final String STATUS_SUCCESS_STYLE =
            "-fx-font-size: 12px; -fx-text-fill: #27ae60; -fx-font-weight: bold;";
    private static final String STATUS_ERROR_STYLE =
            "-fx-font-size: 12px; -fx-text-fill: #e74c3c; -fx-font-weight: bold;";
    private static final String STATUS_LOADING_STYLE =
            "-fx-font-size: 12px; -fx-text-fill: #888;";

    // ── Khởi tạo ──────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        refreshBalanceLabel();
        BalanceWatcher.registerListener(handlerKey, balance ->
                Platform.runLater(this::refreshBalanceLabel));
    }

    /**
     * Cập nhật label số dư từ session hiện tại.
     */
    private void refreshBalanceLabel() {
        double balance = UserSession.getInstance().getBalance();
        lbl_balance.setText(formatVnd(balance) + " ₫");
    }

    // ── Nạp tiền ──────────────────────────────────────────────────────────────

    @FXML
    private void onDeposit(ActionEvent event) {
        double amount = parseAmount(field_deposit_amount);
        if (amount <= 0) {
            showStatus(lbl_deposit_status, "⚠ Vui lòng nhập số tiền hợp lệ (> 0).", false);
            return;
        }
        String note = field_deposit_note.getText().trim();
        if (note.isEmpty()) note = "Nạp tiền";

        setDepositEnabled(false);
        showStatus(lbl_deposit_status, "Đang xử lý...", null);

        final String finalNote = note;
        final double finalAmount = amount;

        new Thread(() -> {
            JsonObject request = new JsonObject();
            request.addProperty("action", "DEPOSIT");
            request.addProperty("user_id", UserSession.getInstance().getUserId());
            request.addProperty("amount", finalAmount);
            request.addProperty("note", finalNote);

            JsonObject response = ServerConnection.sendAuthRequest(request);

            Platform.runLater(() -> {
                setDepositEnabled(true);
                if (response != null && "success".equals(response.get("status").getAsString())) {
                    showStatus(lbl_deposit_status,
                            "✔ Nạp " + formatVnd(finalAmount) + " ₫ thành công!", true);
                    field_deposit_amount.clear();
                    field_deposit_note.clear();
                    if (response.has("new_balance")) {
                        UserSession.getInstance().setBalance(response.get("new_balance").getAsDouble());
                    }
                    refreshBalanceLabel();
                } else {
                    String msg = getErrorMessage(response, "Nạp tiền thất bại. Vui lòng thử lại.");
                    showStatus(lbl_deposit_status, "✖ " + msg, false);
                }
            });
        }, "Wallet-Deposit").start();
    }

    // Nút nạp nhanh
    @FXML
    private void onDepositQuick50(ActionEvent e) {
        addToAmountField(field_deposit_amount, 50_000);
    }

    @FXML
    private void onDepositQuick100(ActionEvent e) {
        addToAmountField(field_deposit_amount, 100_000);
    }

    @FXML
    private void onDepositQuick500(ActionEvent e) {
        addToAmountField(field_deposit_amount, 500_000);
    }

    @FXML
    private void onDepositQuick1M(ActionEvent e) {
        addToAmountField(field_deposit_amount, 1_000_000);
    }

    private void setDepositEnabled(boolean enabled) {
        btn_deposit.setDisable(!enabled);
        field_deposit_amount.setDisable(!enabled);
        field_deposit_note.setDisable(!enabled);
    }

    // ── Rút tiền ──────────────────────────────────────────────────────────────

    @FXML
    private void onWithdraw(ActionEvent event) {
        double amount = parseAmount(field_withdraw_amount);
        if (amount <= 0) {
            showStatus(lbl_withdraw_status, "⚠ Vui lòng nhập số tiền hợp lệ (> 0).", false);
            return;
        }

        double currentBalance = UserSession.getInstance().getBalance();
        if (amount > currentBalance) {
            showStatus(lbl_withdraw_status,
                    "⚠ Số dư không đủ. Số dư hiện tại: " + formatVnd(currentBalance) + " ₫", false);
            return;
        }

        String note = field_withdraw_note.getText().trim();
        if (note.isEmpty()) note = "Rút tiền";

        setWithdrawEnabled(false);
        showStatus(lbl_withdraw_status, "Đang xử lý...", null);

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
                setWithdrawEnabled(true);
                if (response != null && "success".equals(response.get("status").getAsString())) {
                    showStatus(lbl_withdraw_status,
                            "✔ Rút " + formatVnd(finalAmount) + " ₫ thành công!", true);
                    field_withdraw_amount.clear();
                    field_withdraw_note.clear();
                    if (response.has("new_balance")) {
                        UserSession.getInstance().setBalance(response.get("new_balance").getAsDouble());
                    }
                    refreshBalanceLabel();
                } else {
                    String msg = getErrorMessage(response, "Rút tiền thất bại. Số dư không đủ hoặc lỗi server.");
                    showStatus(lbl_withdraw_status, "✖ " + msg, false);
                }
            });
        }, "Wallet-Withdraw").start();
    }

    // Nút rút nhanh
    @FXML
    private void onWithdrawQuick50(ActionEvent e) {
        addToAmountField(field_withdraw_amount, 50_000);
    }

    @FXML
    private void onWithdrawQuick100(ActionEvent e) {
        addToAmountField(field_withdraw_amount, 100_000);
    }

    @FXML
    private void onWithdrawQuick500(ActionEvent e) {
        addToAmountField(field_withdraw_amount, 500_000);
    }

    /**
     * Điền toàn bộ số dư hiện tại vào ô rút tiền.
     */
    @FXML
    private void onWithdrawAll(ActionEvent event) {
        double balance = UserSession.getInstance().getBalance();
        field_withdraw_amount.setText(String.valueOf((long) balance));
    }

    private void setWithdrawEnabled(boolean enabled) {
        btn_withdraw.setDisable(!enabled);
        field_withdraw_amount.setDisable(!enabled);
        field_withdraw_note.setDisable(!enabled);
    }

    // ── Điều hướng ────────────────────────────────────────────────────────────

    @FXML
    private void onRefreshBalance(ActionEvent event) {
        new Thread(() -> {
            // Có thể gọi GET_PROFILE để lấy balance mới nhất từ server nếu cần
            // Hiện tại chỉ đồng bộ từ session (đã được server push qua BALANCE_UPDATED)
            Platform.runLater(this::refreshBalanceLabel);
        }).start();
    }

    @FXML
    private void onViewHistory(ActionEvent event) {
        try {
            BalanceWatcher.unregisterListener(handlerKey);
            // Set màn hình quay lại là Wallet_Transaction để nút "Quay lại" hoạt động
            org.auctionsystem.client.Controller.Bidder.Controller_Transaction_History
                    .setPreviousView("/org/auctionsystem/client/View/Wallet_Transaction.fxml");
            Scene_Utils.Change_Scene(event,
                    "/org/auctionsystem/client/View/Transaction_History.fxml");
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể mở lịch sử giao dịch.");
        }
    }

    @FXML
    private void onBack(ActionEvent event) {
        try {
            BalanceWatcher.unregisterListener(handlerKey);
            Scene_Utils.Change_Scene(event,
                    "/org/auctionsystem/client/View/Bidder_Dashboard.fxml");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Parse số tiền từ TextField. Hỗ trợ cả định dạng "50000" và "50,000".
     * Trả về -1 nếu không hợp lệ.
     */
    private void addToAmountField(TextField field, long delta) {
        if (field == null) return;
        String cur = field.getText().trim().replace(",", "").replace(".", "");
        long base = 0;
        try { base = Long.parseLong(cur); } catch (NumberFormatException ignored) {}
        field.setText(String.valueOf(base + delta));
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

    /**
     * Format số tiền theo kiểu Việt Nam: 1.000.000
     */
    private String formatVnd(double amount) {
        return VND_FORMAT.format((long) amount);
    }

    /**
     * Hiển thị trạng thái bên dưới nút.
     *
     * @param success true = xanh, false = đỏ, null = xám (loading)
     */
    private void showStatus(Label label, String message, Boolean success) {
        label.setText(message);
        if (success == null) {
            label.setStyle(STATUS_LOADING_STYLE);
        } else if (success) {
            label.setStyle(STATUS_SUCCESS_STYLE);
        } else {
            label.setStyle(STATUS_ERROR_STYLE);
        }
    }

    private String getErrorMessage(JsonObject response, String defaultMsg) {
        if (response != null && response.has("message")
                && !response.get("message").isJsonNull()) {
            return response.get("message").getAsString();
        }
        return defaultMsg;
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}