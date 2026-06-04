package org.auctionsystem.client.Controller;

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

public class Controller_Transaction_History {

    // ── FXML ─────────────────────────────────────────────────────────────────
    @FXML private ComboBox<String>         combo_filter;
    @FXML private Button                   btn_filter;
    @FXML private Label                    lbl_balance;

    @FXML private TableView<TransactionRow>       table_transactions;
    @FXML private TableColumn<TransactionRow, String> col_time;
    @FXML private TableColumn<TransactionRow, String> col_type;
    @FXML private TableColumn<TransactionRow, String> col_amount;
    @FXML private TableColumn<TransactionRow, String> col_before;
    @FXML private TableColumn<TransactionRow, String> col_after;
    @FXML private TableColumn<TransactionRow, String> col_note;

    @FXML private Button btn_back;

    // ── Nội bộ ───────────────────────────────────────────────────────────────
    private static final NumberFormat MONEY_FMT =
            NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    // Màn hình trước để quay lại — được set bởi Dashboard trước khi chuyển scene
    private static String previousView = null;

    public static void setPreviousView(String fxmlPath) {
        previousView = fxmlPath;
    }

    // Filter hiện tại — để biết có nên hiện row mới khi nhận event không
    private String currentFilter = null;  // null = Tất cả

    // ── Khởi tạo ─────────────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        // Cột
        col_time  .setCellValueFactory(new PropertyValueFactory<>("time"));
        col_type  .setCellValueFactory(new PropertyValueFactory<>("type"));
        col_amount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        col_before.setCellValueFactory(new PropertyValueFactory<>("before"));
        col_after .setCellValueFactory(new PropertyValueFactory<>("after"));
        col_note  .setCellValueFactory(new PropertyValueFactory<>("note"));

        // ComboBox filter
        combo_filter.setItems(FXCollections.observableArrayList(
                "Tất cả", "Nạp tiền (DEPOSIT)", "Rút tiền (WITHDRAW)",
                "Trừ tiền đấu giá (BID_DEDUCT)", "Nhận tiền bán hàng (BID_CREDIT)"
        ));
        combo_filter.getSelectionModel().selectFirst();

        // Hiển thị số dư hiện tại
        lbl_balance.setText("Số dư: " + formatMoney(UserSession.getInstance().getBalance()) + " ₫");

        // Load toàn bộ lịch sử
        loadTransactions(null);

        // Đăng ký BalanceWatcher để cập nhật label số dư realtime
        BalanceWatcher.registerListener("TransactionHistory", balance ->
                lbl_balance.setText("Số dư: " + formatMoney(balance) + " ₫"));

        // Đăng ký real-time events để thêm row mới vào bảng
        EventDispatcher.register(EventType.BID_DEDUCT, this::onTransactionEvent);
        EventDispatcher.register(EventType.BID_CREDIT, this::onTransactionEvent);
    }

    // ── Xử lý filter ─────────────────────────────────────────────────────────
    @FXML
    public void on_filter(ActionEvent event) {
        String selected = combo_filter.getValue();
        if (selected == null) return;
        currentFilter = switch (selected) {
            case "Nạp tiền (DEPOSIT)"             -> "DEPOSIT";
            case "Rút tiền (WITHDRAW)"            -> "WITHDRAW";
            case "Trừ tiền đấu giá (BID_DEDUCT)"  -> "BID_DEDUCT";
            case "Nhận tiền bán hàng (BID_CREDIT)" -> "BID_CREDIT";
            default -> null;  // "Tất cả"
        };
        loadTransactions(currentFilter);
    }

    // ── Real-time: nhận BID_DEDUCT hoặc BID_CREDIT → thêm row mới lên đầu bảng ──
    private void onTransactionEvent(com.google.gson.JsonObject payload) {
        String eventType = payload.has("event") ? payload.get("event").getAsString() : "";
        String typeCode  = eventType.equals(EventType.BID_DEDUCT) ? "BID_DEDUCT" : "BID_CREDIT";

        // Nếu đang filter theo loại khác thì bỏ qua
        if (currentFilter != null && !currentFilter.equals(typeCode)) return;

        double amount  = getDblSafe(payload, "amount");
        double balance = getDblSafe(payload, "balance");
        String note    = payload.has("item_name") && !payload.get("item_name").isJsonNull()
                ? (typeCode.equals("BID_DEDUCT") ? "Thanh toán đấu giá " : "Nhận tiền bán hàng ")
                + payload.get("item_name").getAsString()
                : typeCode;

        // balance_before = balance_after ± amount
        double balBefore = typeCode.equals("BID_DEDUCT") ? balance + amount : balance - amount;

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

        // Thêm lên đầu bảng — balance label đã được BalanceWatcher cập nhật tự động
        ObservableList<TransactionRow> items = table_transactions.getItems();
        if (items == null) items = FXCollections.observableArrayList();
        items.add(0, row);
        table_transactions.setItems(items);
    }

    // ── Load dữ liệu từ server ────────────────────────────────────────────────
    private void loadTransactions(String typeFilter) {
        // Disable controls khi đang tải
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
                // Khôi phục controls
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
        }, "TransactionHistory-Load").start();
    }

    // ── Quay lại ─────────────────────────────────────────────────────────────
    @FXML
    public void on_back(ActionEvent event) {
        BalanceWatcher.unregisterListener("TransactionHistory");
        EventDispatcher.unregister(EventType.BID_DEDUCT);
        EventDispatcher.unregister(EventType.BID_CREDIT);
        if (previousView == null) {
            String role = UserSession.getInstance().getRole();
            previousView = "seller".equalsIgnoreCase(role)
                    ? "/org/auctionsystem/client/View/Seller_Dashboard.fxml"
                    : "/org/auctionsystem/client/View/Bidder_Dashboard.fxml";
        }
        try {
            Scene_Utils.Change_Scene(event, previousView);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Trả về chuỗi rỗng nếu field không tồn tại hoặc null, tránh NPE */
    private String getStrSafe(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
        return obj.get(key).getAsString();
    }

    /** Trả về 0.0 nếu field không tồn tại hoặc null, tránh NPE */
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

        // Dạng JDBC Timestamp: "2026-05-24 10:30:45.0" hoặc "2026-05-24 10:30:45"
        if (raw.contains("-") && raw.contains(" ") && !raw.contains("T")) {
            String[] parts = raw.split(" ");
            if (parts.length >= 2) {
                String[] dateParts = parts[0].split("-");
                String timePart = parts[1].contains(".") ? parts[1].substring(0, parts[1].indexOf('.')) : parts[1];
                if (timePart.length() >= 5) timePart = timePart.substring(0, 5);
                if (dateParts.length == 3)
                    return dateParts[2] + "/" + dateParts[1] + "/" + dateParts[0] + " " + timePart;
            }
        }

        // Dạng ISO: "2026-05-24T10:30:45"
        if (raw.contains("T")) {
            String[] dt = raw.split("T");
            String[] dateParts = dt[0].split("-");
            String timePart = dt[1].length() >= 5 ? dt[1].substring(0, 5) : dt[1];
            if (dateParts.length == 3)
                return dateParts[2] + "/" + dateParts[1] + "/" + dateParts[0] + " " + timePart;
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

    // ── Inner class: row model ────────────────────────────────────────────────
    public static class TransactionRow {
        private final String time;
        private final String type;
        private final String amount;
        private final String before;
        private final String after;
        private final String note;

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