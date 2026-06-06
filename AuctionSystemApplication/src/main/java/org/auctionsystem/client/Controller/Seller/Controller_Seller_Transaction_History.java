package org.auctionsystem.client.Controller.Seller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.auctionsystem.client.Connectivity.ServerConnection;
import org.auctionsystem.client.Controller.Scene_Utils;
import org.auctionsystem.client.event.BalanceWatcher;
import org.auctionsystem.client.event.EventDispatcher;
import org.auctionsystem.client.event.EventType;
import org.auctionsystem.client.session.UserSession;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Controller_Seller_Transaction_History
 *
 * Màn hình lịch sử biến động số dư dành cho Seller.
 * Logic giống Controller_Transaction_History của Bidder,
 * nhưng nút "Quay lại" điều hướng về Seller_Wallet.
 *
 * FXML: Seller_Transaction_History.fxml
 */
public class Controller_Seller_Transaction_History {
    private final String handlerKey = java.util.UUID.randomUUID().toString();


    // ── FXML ──────────────────────────────────────────────────────────────────
    @FXML private ComboBox<String>                   combo_filter;
    @FXML private Button                             btn_filter;
    @FXML private Label                              lbl_balance;
    @FXML private TableView<TransactionRow>          table_transactions;
    @FXML private TableColumn<TransactionRow, String> col_time;
    @FXML private TableColumn<TransactionRow, String> col_type;
    @FXML private TableColumn<TransactionRow, String> col_amount;
    @FXML private TableColumn<TransactionRow, String> col_before;
    @FXML private TableColumn<TransactionRow, String> col_after;
    @FXML private TableColumn<TransactionRow, String> col_note;
    @FXML private Button                             btn_back;

    // ── Nội bộ ────────────────────────────────────────────────────────────────
    private static final NumberFormat MONEY_FMT =
            NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    // Màn hình trước (mặc định: Seller_Wallet) — Controller_Seller_Wallet set trước khi chuyển
    private static String previousView =
            "/org/auctionsystem/client/View/Seller_Wallet.fxml";

    public static void setPreviousView(String fxmlPath) {
        previousView = fxmlPath;
    }

    // Filter hiện tại — để biết có nên hiện row mới khi nhận event không
    private String currentFilter = null;  // null = Tất cả

    // ── Khởi tạo ──────────────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        col_time  .setCellValueFactory(new PropertyValueFactory<>("time"));
        col_type  .setCellValueFactory(new PropertyValueFactory<>("type"));
        col_amount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        col_before.setCellValueFactory(new PropertyValueFactory<>("before"));
        col_after .setCellValueFactory(new PropertyValueFactory<>("after"));
        col_note  .setCellValueFactory(new PropertyValueFactory<>("note"));

        // Seller chủ yếu nhận BID_CREDIT và rút WITHDRAW
        combo_filter.setItems(FXCollections.observableArrayList(
                "Tất cả",
                "Nhận tiền bán hàng (BID_CREDIT)",
                "Rút tiền (WITHDRAW)",
                "Nạp tiền (DEPOSIT)",
                "Trừ tiền đấu giá (BID_DEDUCT)"
        ));
        combo_filter.getSelectionModel().selectFirst();

        lbl_balance.setText("Số dư: "
                + formatMoney(UserSession.getInstance().getBalance()) + " ₫");

        loadTransactions(null);

        // Đăng ký BalanceWatcher để cập nhật label số dư realtime
        BalanceWatcher.registerListener(handlerKey, balance ->
                lbl_balance.setText("Số dư: " + formatMoney(balance) + " ₫"));

        // Đăng ký real-time events để thêm row mới vào bảng
        EventDispatcher.registerGlobal(EventType.BID_CREDIT, handlerKey, this::onTransactionEvent);
        EventDispatcher.registerGlobal(EventType.BID_DEDUCT, handlerKey, this::onTransactionEvent);
        EventDispatcher.registerGlobal(EventType.BALANCE_UPDATED, handlerKey, this::onTransactionEvent);
    }

    // ── Filter ────────────────────────────────────────────────────────────────
    @FXML
    public void on_filter(ActionEvent event) {
        String selected = combo_filter.getValue();
        if (selected == null) return;
        currentFilter = switch (selected) {
            case "Nhận tiền bán hàng (BID_CREDIT)" -> "BID_CREDIT";
            case "Rút tiền (WITHDRAW)"              -> "WITHDRAW";
            case "Nạp tiền (DEPOSIT)"               -> "DEPOSIT";
            case "Trừ tiền đấu giá (BID_DEDUCT)"    -> "BID_DEDUCT";
            default                                 -> null;
        };
        loadTransactions(currentFilter);
    }

    // ── Load dữ liệu ──────────────────────────────────────────────────────────
    private void loadTransactions(String typeFilter) {
        combo_filter.setDisable(true);
        btn_filter.setDisable(true);
        btn_filter.setText("Đang tải...");

        new Thread(() -> {
            JsonObject request = new JsonObject();
            request.addProperty("user_id", UserSession.getInstance().getUserId());

            if (typeFilter == null) {
                request.addProperty("action", "GET_MY_TRANSACTIONS");
            } else {
                request.addProperty("action", "GET_MY_TRANSACTIONS_BY_TYPE");
                request.addProperty("type", typeFilter);
            }

            JsonObject response = ServerConnection.sendAuthRequest(request);

            Platform.runLater(() -> {
                combo_filter.setDisable(false);
                btn_filter.setDisable(false);
                btn_filter.setText("Lọc");

                if (response == null
                        || !response.has("status")
                        || response.get("status").isJsonNull()
                        || !"success".equals(response.get("status").getAsString())) {
                    showAlert("Không thể tải lịch sử giao dịch!");
                    return;
                }

                ObservableList<TransactionRow> rows = FXCollections.observableArrayList();

                if (!response.has("message") || response.get("message").isJsonNull()
                        || !response.get("message").isJsonArray()) {
                    table_transactions.setItems(rows);
                    return;
                }

                JsonArray arr = response.get("message").getAsJsonArray();

                for (JsonElement el : arr) {
                    if (!el.isJsonObject()) continue;
                    JsonObject tx = el.getAsJsonObject();

                    String createdAt = getStrSafe(tx, "created_at");
                    String type      = getStrSafe(tx, "type");
                    double amount    = getDblSafe(tx, "amount");
                    double balBefore = getDblSafe(tx, "balance_before");
                    double balAfter  = getDblSafe(tx, "balance_after");
                    String note      = (tx.has("note") && !tx.get("note").isJsonNull())
                            ? tx.get("note").getAsString() : "";

                    rows.add(new TransactionRow(
                            formatTime(createdAt),
                            typeLabel(type),
                            formatMoney(amount) + " ₫",
                            formatMoney(balBefore) + " ₫",
                            formatMoney(balAfter) + " ₫",
                            note
                    ));
                }
                table_transactions.setItems(rows);
            });
        }, "SellerTransactionHistory-Load").start();
    }

    // ── Real-time: nhận BID_CREDIT, BID_DEDUCT hoặc BALANCE_UPDATED → thêm row mới lên đầu bảng ──
    private void onTransactionEvent(com.google.gson.JsonObject payload) {
        String eventType = payload.has("event") ? payload.get("event").getAsString() : "";

        String typeCode;
        switch (eventType) {
            case EventType.BID_CREDIT      -> typeCode = "BID_CREDIT";
            case EventType.BID_DEDUCT      -> typeCode = "BID_DEDUCT";
            case EventType.BALANCE_UPDATED -> {
                typeCode = payload.has("tx_type") && !payload.get("tx_type").isJsonNull()
                        ? payload.get("tx_type").getAsString()
                        : "DEPOSIT";
            }
            default -> { return; }
        }

        // Nếu đang filter theo loại khác thì bỏ qua
        if (currentFilter != null && !currentFilter.equals(typeCode)) return;

        double amount  = getDblSafe(payload, "amount");
        double balance = getDblSafe(payload, "balance");

        String note;
        if (typeCode.equals("BID_CREDIT") || typeCode.equals("BID_DEDUCT")) {
            note = payload.has("item_name") && !payload.get("item_name").isJsonNull()
                    ? (typeCode.equals("BID_CREDIT") ? "Nhận tiền bán hàng " : "Thanh toán đấu giá ")
                    + payload.get("item_name").getAsString()
                    : typeCode;
        } else {
            note = payload.has("note") && !payload.get("note").isJsonNull()
                    ? payload.get("note").getAsString() : "";
        }

        boolean isDeduct = typeCode.equals("BID_DEDUCT") || typeCode.equals("WITHDRAW");
        double balBefore = isDeduct ? balance + amount : balance - amount;

        String now = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

        TransactionRow row = new TransactionRow(
                now,
                typeLabel(typeCode),
                formatMoney(amount) + " ₫",
                formatMoney(balBefore) + " ₫",
                formatMoney(balance) + " ₫",
                note
        );

        ObservableList<TransactionRow> items = table_transactions.getItems();
        if (items == null) items = FXCollections.observableArrayList();
        items.add(0, row);
        table_transactions.setItems(items);
    }

    // ── Quay lại ──────────────────────────────────────────────────────────────
    @FXML
    public void on_back(ActionEvent event) {
        BalanceWatcher.unregisterListener(handlerKey);
        EventDispatcher.unregisterGlobal(EventType.BID_CREDIT, handlerKey);
        EventDispatcher.unregisterGlobal(EventType.BID_DEDUCT, handlerKey);
        EventDispatcher.unregisterGlobal(EventType.BALANCE_UPDATED, handlerKey);
        try {
            Scene_Utils.Change_Scene(event, previousView);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private String getStrSafe(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
        return obj.get(key).getAsString();
    }

    private double getDblSafe(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return 0.0;
        try { return obj.get(key).getAsDouble(); } catch (Exception e) { return 0.0; }
    }

    private String formatMoney(double amount) {
        return MONEY_FMT.format((long) amount);
    }

    private String formatTime(String raw) {
        if (raw == null || raw.isBlank()) return "";
        raw = raw.trim();

        // Dạng mảng JSON: [2026,5,24,10,30,45]
        if (raw.startsWith("[")) {
            raw = raw.replaceAll("[\\[\\]\\s]", "");
            String[] p = raw.split(",");
            if (p.length >= 5) {
                return String.format("%s/%s/%s %s:%s",
                        pad(p[2]), pad(p[1]), p[0], pad(p[3]), pad(p[4]));
            }
        }

        // Dạng JDBC Timestamp: "2026-05-24 10:30:45.0"
        if (raw.contains("-") && raw.contains(" ") && !raw.contains("T")) {
            String[] parts = raw.split(" ");
            if (parts.length >= 2) {
                String[] dateParts = parts[0].split("-");
                String timePart = parts[1].contains(".")
                        ? parts[1].substring(0, parts[1].indexOf('.'))
                        : parts[1];
                if (timePart.length() >= 5) timePart = timePart.substring(0, 5);
                if (dateParts.length == 3)
                    return dateParts[2] + "/" + dateParts[1] + "/" + dateParts[0]
                            + " " + timePart;
            }
        }

        // Dạng ISO: "2026-05-24T10:30:45"
        if (raw.contains("T")) {
            String[] dt = raw.split("T");
            String[] dateParts = dt[0].split("-");
            String timePart = dt[1].length() >= 5 ? dt[1].substring(0, 5) : dt[1];
            if (dateParts.length == 3)
                return dateParts[2] + "/" + dateParts[1] + "/" + dateParts[0]
                        + " " + timePart;
        }

        return raw;
    }

    private String pad(String s) {
        return s.length() == 1 ? "0" + s : s;
    }

    private String typeLabel(String type) {
        return switch (type) {
            case "DEPOSIT"    -> "✅ Nạp tiền";
            case "WITHDRAW"   -> "🔴 Rút tiền";
            case "BID_DEDUCT" -> "🔻 Thanh toán đấu giá";
            case "BID_CREDIT" -> "💰 Nhận tiền bán hàng";
            default           -> type;
        };
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Lỗi");
        alert.setContentText(msg);
        alert.showAndWait();
    }

    // ── Inner class: row model ─────────────────────────────────────────────────
    public static class TransactionRow {
        private final String time, type, amount, before, after, note;

        public TransactionRow(String time, String type, String amount,
                              String before, String after, String note) {
            this.time   = time;
            this.type   = type;
            this.amount = amount;
            this.before = before;
            this.after  = after;
            this.note   = note;
        }

        public String getTime()   { return time; }
        public String getType()   { return type; }
        public String getAmount() { return amount; }
        public String getBefore() { return before; }
        public String getAfter()  { return after; }
        public String getNote()   { return note; }
    }
}