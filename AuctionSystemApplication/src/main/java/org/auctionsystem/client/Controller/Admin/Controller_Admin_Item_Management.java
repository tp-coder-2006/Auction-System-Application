package org.auctionsystem.client.Controller.Admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import org.auctionsystem.client.Connectivity.ServerConnection;
import org.auctionsystem.client.Controller.Scene_Utils;
import org.auctionsystem.client.event.EventDispatcher;
import org.auctionsystem.client.event.EventType;

import java.io.IOException;
import java.util.Optional;

/**
 * Controller_Admin_Item_Management — màn hình admin xem và quản lý toàn bộ item.
 *
 * Hỗ trợ:
 *   - Xem danh sách TẤT CẢ item (kể cả is_active = 0)
 *   - Xóa vĩnh viễn (hard delete) sản phẩm → ADMIN_DELETE_ITEM
 *     Xóa toàn bộ: bids, item_ownership_history, images metadata, item chính.
 *     Transactions tài chính được giữ lại (related_item_id → NULL).
 */
public class Controller_Admin_Item_Management {

    // ─── FXML fields ──────────────────────────────────────────────────────────
    @FXML private TableView<ItemRow>           tableItems;
    @FXML private TableColumn<ItemRow, String> colName;
    @FXML private TableColumn<ItemRow, String> colStatus;
    @FXML private TableColumn<ItemRow, String> colSeller;
    @FXML private TableColumn<ItemRow, String> colStartTime;
    @FXML private TableColumn<ItemRow, String> colEndTime;
    @FXML private TableColumn<ItemRow, String> colPrice;
    @FXML private TableColumn<ItemRow, String> colVisible;
    @FXML private TableColumn<ItemRow, Void>   colActions;

    @FXML private TextField fieldSearch;
    @FXML private Label     labelResult;

    private final ObservableList<ItemRow> allItems = FXCollections.observableArrayList();

    // ─── Khởi tạo ─────────────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        colName     .setCellValueFactory(new PropertyValueFactory<>("name"));
        colStatus   .setCellValueFactory(new PropertyValueFactory<>("status"));
        colSeller   .setCellValueFactory(new PropertyValueFactory<>("sellerUsername"));
        colStartTime.setCellValueFactory(new PropertyValueFactory<>("startTime"));
        colEndTime  .setCellValueFactory(new PropertyValueFactory<>("endTime"));
        colPrice    .setCellValueFactory(new PropertyValueFactory<>("currentPrice"));
        colVisible  .setCellValueFactory(new PropertyValueFactory<>("visible"));

        setupActionsColumn();
        loadItems();

        if (fieldSearch != null) {
            fieldSearch.textProperty().addListener((obs, oldVal, newVal) -> filterItems(newVal));
        }

        // Tự động reload khi có item đổi trạng thái, thêm/sửa/xóa từ bất kỳ client nào
        EventDispatcher.register(EventType.ADMIN_STATS_UPDATE, payload -> loadItems());
    }

    // ─── Cột hành động: nút Xóa vĩnh viễn ────────────────────────────────────
    private void setupActionsColumn() {
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnDelete = new Button("🗑 Xóa vĩnh viễn");
            private final HBox   box       = new HBox(btnDelete);

            {
                box.setAlignment(Pos.CENTER);
                btnDelete.setStyle(
                        "-fx-background-color: #c0392b; -fx-text-fill: white; " +
                                "-fx-font-size: 12px; -fx-font-weight: bold; " +
                                "-fx-padding: 4 10; -fx-cursor: hand; -fx-background-radius: 4;");
                btnDelete.setOnAction(e -> {
                    ItemRow row = getTableView().getItems().get(getIndex());
                    handleHardDeleteItem(row);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                } else {
                    setGraphic(box);
                }
            }
        });
    }

    // ─── Xử lý Hard Delete với xác nhận 2 bước ────────────────────────────────
    private void handleHardDeleteItem(ItemRow row) {
        // Bước 1: cảnh báo
        Alert warn = new Alert(Alert.AlertType.WARNING);
        warn.setTitle("⚠ Xóa vĩnh viễn sản phẩm");
        warn.setHeaderText("Hành động này KHÔNG THỂ HOÀN TÁC!");
        warn.setContentText(
                "Sản phẩm: \"" + row.getName() + "\"\n\n" +
                        "Toàn bộ dữ liệu sau sẽ bị xóa vĩnh viễn:\n" +
                        "  • Tất cả lượt đặt giá (bids)\n" +
                        "  • Lịch sử sở hữu (item_ownership_history)\n" +
                        "  • Ảnh sản phẩm (images)\n" +
                        "  • Bản ghi sản phẩm\n\n" +
                        "(Lịch sử giao dịch tài chính được giữ lại)\n\n" +
                        "Bạn có chắc chắn muốn tiếp tục?"
        );
        warn.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

        Optional<ButtonType> step1 = warn.showAndWait();
        if (step1.isEmpty() || step1.get() != ButtonType.YES) return;

        // Bước 2: xác nhận lần cuối
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận lần cuối");
        confirm.setHeaderText("Xóa vĩnh viễn \"" + row.getName() + "\"?");
        confirm.setContentText("Nhấn OK để xóa. Dữ liệu sẽ mất hoàn toàn.");
        confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> step2 = confirm.showAndWait();
        if (step2.isEmpty() || step2.get() != ButtonType.OK) return;

        // Gửi request xóa
        showResult("⏳ Đang xóa sản phẩm...", true);
        new Thread(() -> {
            JsonObject req = new JsonObject();
            req.addProperty("action", "ADMIN_DELETE_ITEM");
            req.addProperty("item_id", row.getId());
            JsonObject resp = ServerConnection.sendAuthRequest(req);
            Platform.runLater(() -> {
                if (resp != null && "success".equals(resp.get("status").getAsString())) {
                    showResult("✅ " + resp.get("message").getAsString(), true);
                    loadItems();
                } else {
                    String msg = resp != null ? resp.get("message").getAsString() : "Lỗi kết nối!";
                    showResult("❌ " + msg, false);
                }
            });
        }, "AdminHardDeleteItem").start();
    }

    // ─── Tải danh sách item từ server (background thread) ─────────────────────
    private void loadItems() {
        showResult("⏳ Đang tải danh sách sản phẩm...", true);
        new Thread(() -> {
            JsonObject request = new JsonObject();
            request.addProperty("action", "ADMIN_GET_ALL_ITEMS");
            JsonObject response = ServerConnection.sendAuthRequest(request);

            Platform.runLater(() -> {
                allItems.clear();

                if (response == null) {
                    showResult("❌ Không thể kết nối tới server!", false);
                    return;
                }

                String status = response.get("status").getAsString();
                if (!"success".equals(status)) {
                    showResult("❌ " + response.get("message").getAsString(), false);
                    return;
                }

                JsonArray arr = response.get("message").getAsJsonArray();
                for (JsonElement el : arr) {
                    JsonObject item = el.getAsJsonObject();

                    boolean active = false;
                    if (item.has("isActive") && !item.get("isActive").isJsonNull()) {
                        active = item.get("isActive").getAsBoolean();
                    } else if (item.has("active") && !item.get("active").isJsonNull()) {
                        active = item.get("active").getAsBoolean();
                    }

                    String statusStr = "UNKNOWN";
                    if (item.has("status") && !item.get("status").isJsonNull()) {
                        statusStr = item.get("status").isJsonPrimitive()
                                ? item.get("status").getAsString()
                                : item.get("status").toString();
                    }

                    String sellerDisplay = item.has("sellerUsername") && !item.get("sellerUsername").isJsonNull()
                            ? item.get("sellerUsername").getAsString()
                            : safeStr(item, "sellerId", "—");

                    double price = 0;
                    if (item.has("currentHighestPrice") && !item.get("currentHighestPrice").isJsonNull()) {
                        price = item.get("currentHighestPrice").getAsDouble();
                    } else if (item.has("startingPrice") && !item.get("startingPrice").isJsonNull()) {
                        price = item.get("startingPrice").getAsDouble();
                    }

                    allItems.add(new ItemRow(
                            item.get("id").getAsString(),
                            item.get("name").getAsString(),
                            statusStr,
                            sellerDisplay,
                            safeStr(item, "startTime", "—"),
                            safeStr(item, "endTime",   "—"),
                            price,
                            active
                    ));
                }

                tableItems.setItems(allItems);
                showResult("✅ Tải danh sách thành công (" + allItems.size() + " sản phẩm).", true);
            });
        }, "AdminItemMgmt-Load").start();
    }

    // ─── Lọc theo từ khóa ─────────────────────────────────────────────────────
    private void filterItems(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            tableItems.setItems(allItems);
            return;
        }
        String lower = keyword.toLowerCase();
        ObservableList<ItemRow> filtered = FXCollections.observableArrayList();
        for (ItemRow row : allItems) {
            if (row.getName().toLowerCase().contains(lower)
                    || row.getStatus().toLowerCase().contains(lower)) {
                filtered.add(row);
            }
        }
        tableItems.setItems(filtered);
    }

    // ─── Reload ───────────────────────────────────────────────────────────────
    @FXML
    public void refreshItems(ActionEvent event) {
        if (fieldSearch != null) fieldSearch.clear();
        loadItems();
    }

    // ─── Quay lại dashboard ───────────────────────────────────────────────────
    @FXML
    public void back_to_admin_dashboard(ActionEvent event) {
        EventDispatcher.unregister(EventType.ADMIN_STATS_UPDATE);
        try {
            Scene_Utils.Change_Scene(event, "/org/auctionsystem/client/View/Admin_Dashboard.fxml");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // ─── Helper ───────────────────────────────────────────────────────────────
    private void showResult(String message, boolean success) {
        if (labelResult == null) return;
        labelResult.setText(message);
        labelResult.setStyle(success ? "-fx-text-fill: green;" : "-fx-text-fill: red;");
    }

    private String safeStr(JsonObject obj, String key, String fallback) {
        return (obj.has(key) && !obj.get(key).isJsonNull())
                ? obj.get(key).getAsString() : fallback;
    }

    // ─── Model row ────────────────────────────────────────────────────────────
    public static class ItemRow {
        private final String  id;
        private final String  name;
        private final String  status;
        private final String  sellerUsername;
        private final String  startTime;
        private final String  endTime;
        private final double  price;
        private final boolean activeStatus;

        public ItemRow(String id, String name, String status, String sellerUsername,
                       String startTime, String endTime, double price, boolean activeStatus) {
            this.id             = id;
            this.name           = name;
            this.status         = status;
            this.sellerUsername = sellerUsername;
            this.startTime      = startTime;
            this.endTime        = endTime;
            this.price          = price;
            this.activeStatus   = activeStatus;
        }

        public String getId()             { return id; }
        public String getName()           { return name; }
        public String getStatus()         { return status; }
        public String getSellerUsername() { return sellerUsername; }
        public String getStartTime()      { return startTime; }
        public String getEndTime()        { return endTime; }
        public String getCurrentPrice()   { return String.format("%.0f đ", price); }
        public String getVisible()        { return activeStatus ? "Hiển thị" : "Đã ẩn"; }
        public boolean isActiveStatus()   { return activeStatus; }
    }
}