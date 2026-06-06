package org.auctionsystem.client.Controller.Admin;

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
import org.auctionsystem.client.event.EventDispatcher;
import org.auctionsystem.client.event.EventType;

import java.io.IOException;
import java.time.LocalDate;

/**
 * Controller_Admin_Financial_Auditing — màn hình kiểm soát tài chính.
 *
 * [SỬA] Tải TOÀN BỘ giao dịch (ADMIN_GET_ALL_TRANSACTIONS) khi mở màn hình.
 * Tìm kiếm theo username chỉ là bộ lọc phụ trên client-side.
 *
 * Chức năng:
 *   - Hiển thị tất cả giao dịch ngay khi mở (tối đa 500 gần nhất)
 *   - Tìm kiếm / lọc theo username trực tiếp trên dữ liệu đã tải
 *   - Lọc theo loại giao dịch (ComboBox)
 *   - Lọc theo khoảng thời gian (DatePicker từ / đến)
 *   - Tra cứu chi tiết theo username từ server (ADMIN_GET_USER_BY_USERNAME → ADMIN_GET_TRANSACTIONS_BY_USER)
 */
public class Controller_Admin_Financial_Auditing {
    // UUID duy nhất cho mỗi instance — tránh ghi đè handler của cửa sổ khác
    private final String handlerKey = java.util.UUID.randomUUID().toString();


    // ─── FXML fields ──────────────────────────────────────────────────────────
    @FXML private ComboBox<String>                    ComboBox_TransactionType;
    @FXML private DatePicker                          datePicker_from;
    @FXML private DatePicker                          datePicker_to;
    @FXML private TextField                           field_search_user;
    @FXML private Button                              btn_search;
    @FXML private Label                               lbl_status;

    @FXML private TableView<TransactionRow>           tableTransactions;
    @FXML private TableColumn<TransactionRow, String> col_transactionId;
    @FXML private TableColumn<TransactionRow, String> col_account;
    @FXML private TableColumn<TransactionRow, String> col_product;
    @FXML private TableColumn<TransactionRow, String> col_type;
    @FXML private TableColumn<TransactionRow, String> col_amount;
    @FXML private TableColumn<TransactionRow, String> col_time;

    private final ObservableList<TransactionRow> allTransactions = FXCollections.observableArrayList();

    // ─── Khởi tạo ─────────────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        // ComboBox
        if (ComboBox_TransactionType != null) {
            ComboBox_TransactionType.getItems().addAll(
                    "Tất cả giao dịch", "Nạp tiền", "Rút tiền",
                    "Thanh toán đấu giá", "Nhận tiền bán đấu giá"
            );
            ComboBox_TransactionType.setValue("Tất cả giao dịch");
            ComboBox_TransactionType.valueProperty().addListener((obs, o, n) -> applyFilter());
        }

        // Cột bảng
        col_transactionId.setCellValueFactory(new PropertyValueFactory<>("transactionId"));
        col_account      .setCellValueFactory(new PropertyValueFactory<>("account"));
        col_product      .setCellValueFactory(new PropertyValueFactory<>("itemName"));
        col_type         .setCellValueFactory(new PropertyValueFactory<>("type"));
        col_amount       .setCellValueFactory(new PropertyValueFactory<>("amount"));
        col_time         .setCellValueFactory(new PropertyValueFactory<>("createdAt"));

        // DatePicker listener
        if (datePicker_from != null) datePicker_from.valueProperty().addListener((obs, o, n) -> applyFilter());
        if (datePicker_to   != null) datePicker_to  .valueProperty().addListener((obs, o, n) -> applyFilter());

        // Enter trong ô tìm kiếm → lọc client-side ngay
        if (field_search_user != null) {
            field_search_user.textProperty().addListener((obs, o, n) -> applyFilter());
            field_search_user.setOnAction(e -> searchByUserFromServer());
        }

        // [MỚI] Tải toàn bộ giao dịch khi mở màn hình
        loadAllTransactions();

        // Tự động reload khi có giao dịch mới — chỉ reload nếu admin chưa đang filter theo user cụ thể
        EventDispatcher.registerGlobal(EventType.ADMIN_STATS_UPDATE, handlerKey, payload -> {
            String currentSearch = (field_search_user != null) ? field_search_user.getText().trim() : "";
            if (currentSearch.isEmpty()) {
                loadAllTransactions();
            }
        });
    }

    // ─── [MỚI] Tải toàn bộ giao dịch từ server ────────────────────────────────
    private void loadAllTransactions() {
        setStatus("⏳ Đang tải dữ liệu...", true);
        new Thread(() -> {
            JsonObject req = new JsonObject();
            req.addProperty("action", "ADMIN_GET_ALL_TRANSACTIONS");
            JsonObject resp = ServerConnection.sendAuthRequest(req);

            Platform.runLater(() -> {
                allTransactions.clear();
                if (resp == null) {
                    setStatus("❌ Không thể kết nối tới server!", false);
                    return;
                }
                String status = resp.has("status") ? resp.get("status").getAsString() : "error";
                if (!"success".equals(status)) {
                    String msg = resp.has("message") ? resp.get("message").getAsString() : "Lỗi không xác định";
                    setStatus("❌ " + msg, false);
                    return;
                }
                parseAndAddTransactions(resp.get("message").getAsJsonArray());
                applyFilter();
                setStatus("✅ Đã tải " + allTransactions.size() + " giao dịch gần nhất.", true);
            });
        }, "AdminFinancial-LoadAll").start();
    }

    // ─── Tra cứu giao dịch theo username từ server ────────────────────────────
    @FXML
    private void searchByUserFromServer() {
        String username = (field_search_user != null) ? field_search_user.getText().trim() : "";
        if (username.isEmpty()) {
            // Nếu xóa trắng → reload toàn bộ
            loadAllTransactions();
            return;
        }
        setStatus("⏳ Đang tra cứu \"" + username + "\"...", true);

        new Thread(() -> {
            // Bước 1: lấy user_id từ username
            JsonObject userReq = new JsonObject();
            userReq.addProperty("action", "ADMIN_GET_USER_BY_USERNAME");
            userReq.addProperty("username", username);
            JsonObject userResp = ServerConnection.sendAuthRequest(userReq);

            if (userResp == null) {
                Platform.runLater(() -> setStatus("❌ Không thể kết nối tới server!", false));
                return;
            }
            String userStatus = userResp.has("status") ? userResp.get("status").getAsString() : "error";
            if (!"success".equals(userStatus)) {
                Platform.runLater(() -> setStatus("❌ Không tìm thấy người dùng \"" + username + "\".", false));
                return;
            }

            // Lấy id từ "information" object
            String userId;
            try {
                userId = userResp.get("information").getAsJsonObject().get("id").getAsString();
            } catch (Exception ex) {
                Platform.runLater(() -> setStatus("❌ Lỗi đọc thông tin người dùng.", false));
                return;
            }

            // Bước 2: lấy giao dịch theo user_id
            JsonObject txReq = new JsonObject();
            txReq.addProperty("action", "ADMIN_GET_TRANSACTIONS_BY_USER");
            txReq.addProperty("user_id", userId);
            JsonObject txResp = ServerConnection.sendAuthRequest(txReq);

            Platform.runLater(() -> {
                allTransactions.clear();
                if (txResp == null) {
                    setStatus("❌ Không thể kết nối tới server!", false);
                    return;
                }
                String txStatus = txResp.has("status") ? txResp.get("status").getAsString() : "error";
                if (!"success".equals(txStatus)) {
                    setStatus("❌ Không thể lấy danh sách giao dịch.", false);
                    return;
                }
                parseAndAddTransactions(txResp.get("message").getAsJsonArray());
                applyFilter();
                setStatus("✅ Tìm thấy " + allTransactions.size() + " giao dịch của \"" + username + "\".", true);
            });
        }, "AdminFinancial-SearchUser").start();
    }

    // ─── Parse JSON array → TransactionRow list ───────────────────────────────
    private void parseAndAddTransactions(JsonArray arr) {
        allTransactions.clear();
        for (JsonElement el : arr) {
            JsonObject tx = el.getAsJsonObject();
            String itemName = safeStr(tx, "item_name", "—");
            // "account" hiển thị: username nếu có, fallback về user_id
            String account = tx.has("username") && !tx.get("username").isJsonNull()
                    ? tx.get("username").getAsString()
                    : safeStr(tx, "user_id", "—");
            allTransactions.add(new TransactionRow(
                    safeStr(tx, "id",         "—"),
                    safeStr(tx, "user_id",    "—"),
                    account,
                    itemName,
                    safeStr(tx, "type",       "—"),
                    tx.has("amount") ? tx.get("amount").getAsDouble() : 0,
                    safeStr(tx, "created_at", "—")
            ));
        }
    }

    // ─── Lọc theo loại, ngày, và username ─────────────────────────────────────
    private void applyFilter() {
        String typeFilter = (ComboBox_TransactionType != null) ? ComboBox_TransactionType.getValue() : null;
        LocalDate from    = (datePicker_from != null) ? datePicker_from.getValue() : null;
        LocalDate to      = (datePicker_to   != null) ? datePicker_to  .getValue() : null;
        String userFilter = (field_search_user != null) ? field_search_user.getText().trim().toLowerCase() : "";

        ObservableList<TransactionRow> filtered = FXCollections.observableArrayList();
        for (TransactionRow row : allTransactions) {
            // Lọc username (client-side, instant)
            if (!userFilter.isEmpty()) {
                boolean matchUser = row.getAccount().toLowerCase().contains(userFilter)
                        || row.getUserId().toLowerCase().contains(userFilter);
                if (!matchUser) continue;
            }
            // Lọc loại giao dịch
            if (typeFilter != null && !"Tất cả giao dịch".equals(typeFilter)) {
                if (!matchType(row.getRawType(), typeFilter)) continue;
            }
            // Lọc khoảng ngày
            if (from != null || to != null) {
                LocalDate rowDate = parseDate(row.getRawCreatedAt());
                if (rowDate != null) {
                    if (from != null && rowDate.isBefore(from)) continue;
                    if (to   != null && rowDate.isAfter(to))    continue;
                }
            }
            filtered.add(row);
        }
        tableTransactions.setItems(filtered);
    }

    // ─── Nút tìm kiếm ─────────────────────────────────────────────────────────
    @FXML
    private void searchByUser() {
        // Nếu có username → tra server; nếu rỗng → lọc client-side
        String username = (field_search_user != null) ? field_search_user.getText().trim() : "";
        if (username.isEmpty()) {
            applyFilter();
        } else {
            searchByUserFromServer();
        }
    }

    // ─── Xóa bộ lọc ───────────────────────────────────────────────────────────
    @FXML
    private void clearFilters() {
        if (ComboBox_TransactionType != null) ComboBox_TransactionType.setValue("Tất cả giao dịch");
        if (datePicker_from != null) datePicker_from.setValue(null);
        if (datePicker_to   != null) datePicker_to  .setValue(null);
        if (field_search_user != null) field_search_user.clear();
        loadAllTransactions();
    }

    // ─── Quay lại ─────────────────────────────────────────────────────────────
    @FXML
    public void back_to_admin_dashboard(ActionEvent event) {
        EventDispatcher.unregisterGlobal(EventType.ADMIN_STATS_UPDATE, handlerKey);
        try { Scene_Utils.Change_Scene(event, "/org/auctionsystem/client/View/Admin_Dashboard.fxml"); }
        catch (IOException e) { throw new RuntimeException(e); }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    private boolean matchType(String rawType, String display) {
        if (rawType == null) return false;
        return switch (display) {
            case "Nạp tiền"          -> rawType.equalsIgnoreCase("DEPOSIT");
            case "Rút tiền"          -> rawType.equalsIgnoreCase("WITHDRAW");
            case "Thanh toán đấu giá"      -> rawType.equalsIgnoreCase("BID_DEDUCT");
            case "Nhận tiền bán đấu giá" -> rawType.equalsIgnoreCase("BID_CREDIT");
            default -> true;
        };
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.length() < 10) return null;
        try { return LocalDate.parse(raw.substring(0, 10)); }
        catch (Exception e) { return null; }
    }

    private void setStatus(String msg, boolean ok) {
        if (lbl_status == null) return;
        lbl_status.setText(msg);
        lbl_status.setStyle(ok ? "-fx-text-fill:#27ae60;" : "-fx-text-fill:#e74c3c;");
    }

    private String safeStr(JsonObject obj, String key, String fallback) {
        try {
            return (obj.has(key) && !obj.get(key).isJsonNull())
                    ? obj.get(key).getAsString() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    // ─── Model row ────────────────────────────────────────────────────────────
    public static class TransactionRow {
        private final String transactionId, userId, account, itemName, rawType, rawCreatedAt;
        private final double rawAmount;

        public TransactionRow(String transactionId, String userId, String account,
                              String itemName, String rawType, double rawAmount, String rawCreatedAt) {
            this.transactionId = transactionId;
            this.userId        = userId;
            this.account       = account;
            this.itemName      = itemName;
            this.rawType       = rawType;
            this.rawAmount     = rawAmount;
            this.rawCreatedAt  = rawCreatedAt;
        }

        public String getTransactionId() { return transactionId; }
        public String getUserId()        { return userId; }
        public String getAccount()       { return account; }
        public String getItemName()      { return itemName; }
        public String getRawType()       { return rawType; }
        public String getRawCreatedAt()  { return rawCreatedAt; }

        // Cột hiển thị
        public String getType() {
            if (rawType == null) return "—";
            return switch (rawType.toUpperCase()) {
                case "DEPOSIT"    -> "💰 Nạp tiền";
                case "WITHDRAW"   -> "💸 Rút tiền";
                case "BID_DEDUCT" -> "💸 Thanh toán đấu giá";
                case "BID_CREDIT" -> "💰 Nhận tiền bán đấu giá";
                default           -> rawType;
            };
        }

        public String getAmount() { return String.format("%,.0f đ", rawAmount); }

        public String getCreatedAt() {
            if (rawCreatedAt == null || rawCreatedAt.length() < 16) return rawCreatedAt != null ? rawCreatedAt : "—";
            try {
                String s = rawCreatedAt.substring(0, 16);
                String[] parts = s.split(" ");
                String[] d = parts[0].split("-");
                return d[2] + "/" + d[1] + "/" + d[0] + " " + parts[1];
            } catch (Exception e) { return rawCreatedAt; }
        }
    }
}