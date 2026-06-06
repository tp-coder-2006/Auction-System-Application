package org.auctionsystem.client.Controller.Bidder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.auctionsystem.client.Connectivity.ServerConnection;
import org.auctionsystem.client.Controller.Scene_Utils;
import org.auctionsystem.client.event.EventDispatcher;
import org.auctionsystem.client.event.EventType;
import org.auctionsystem.client.session.UserSession;

import java.io.IOException;

/**
 * Controller_Bidding_Result — màn hình "Kết quả đấu giá".
 *
 * Hiển thị tất cả các item mà bidder đã từng tham gia đặt giá,
 * kèm trạng thái kết quả:
 *   • ongoing   – đang diễn ra (bidder đang dẫn đầu)
 *   • losing    – đang diễn ra (bidder không dẫn đầu)
 *   • won       – đã kết thúc, bidder thắng
 *   • lost      – đã kết thúc, bidder thua
 *   • cancelled – phiên bị hủy
 */
public class Controller_Bidding_Result {
    // UUID duy nhất cho mỗi instance — tránh ghi đè handler của cửa sổ khác
    private final String handlerKey = java.util.UUID.randomUUID().toString();


    // ── TableView ─────────────────────────────────────────────────────────────
    @FXML private TableView<JsonObject>             tableResult;
    @FXML private TableColumn<JsonObject, String>   col_item_name;
    @FXML private TableColumn<JsonObject, String>   col_bid_price;
    @FXML private TableColumn<JsonObject, String>   col_bid_time;
    @FXML private TableColumn<JsonObject, String>   col_status;

    // ── Filter buttons ────────────────────────────────────────────────────────
    @FXML private ToggleButton btn_filter_all;
    @FXML private ToggleButton btn_filter_ongoing;
    @FXML private ToggleButton btn_filter_won;
    @FXML private ToggleButton btn_filter_lost;
    @FXML private ToggleButton btn_filter_cancelled;
    @FXML private Label        lbl_summary;

    // ── Summary counters ──────────────────────────────────────────────────────
    @FXML private Label lbl_count_ongoing;
    @FXML private Label lbl_count_won;
    @FXML private Label lbl_count_lost;
    @FXML private Label lbl_count_cancelled;

    private final ObservableList<JsonObject> masterList   = FXCollections.observableArrayList();
    private FilteredList<JsonObject>         filteredList;
    private String                           activeFilter = "all";

    private static final String DASHBOARD_VIEW =
            "/org/auctionsystem/client/View/Bidder_Dashboard.fxml";

    // ─────────────────────────────────────────────────────────────────────────
    //  Khởi tạo
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        setupTable();
        setupFilter();
        loadData();

        // Real-time: lắng nghe các sự kiện server broadcast
        EventDispatcher.registerGlobal(EventType.BID_PLACED, handlerKey, this::onBidPlaced);
        EventDispatcher.registerGlobal(EventType.AUCTION_SETTLED, handlerKey, this::onAuctionSettled);
        EventDispatcher.registerGlobal(EventType.ITEM_CANCELLED, handlerKey, this::onItemCancelled);
        EventDispatcher.registerGlobal(EventType.ITEM_DELETED, handlerKey, this::onItemDeleted);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Setup bảng
    // ─────────────────────────────────────────────────────────────────────────

    private void setupTable() {
        col_item_name.setCellValueFactory(data -> new SimpleStringProperty(
                getString(data.getValue(), "itemName", getString(data.getValue(), "itemId", "—"))));

        col_bid_price.setCellValueFactory(data -> {
            JsonObject row = data.getValue();
            if (row.has("bidAmount") && !row.get("bidAmount").isJsonNull()) {
                double amount = row.get("bidAmount").getAsDouble();
                return new SimpleStringProperty(String.format("%,.0f ₫", amount));
            }
            return new SimpleStringProperty("—");
        });

        col_bid_time.setCellValueFactory(data -> new SimpleStringProperty(
                getString(data.getValue(), "bidTime", "—")));

        col_status.setCellValueFactory(data -> {
            String raw = getString(data.getValue(), "itemStatus", "");
            return new SimpleStringProperty(toVietnamese(raw));
        });

        // Tô màu theo trạng thái
        col_status.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
                getStyleClass().removeAll("status-ongoing", "status-won", "status-lost",
                        "status-losing", "status-cancelled");
                if (!empty && getTableRow() != null && getTableRow().getItem() != null) {
                    JsonObject row = (JsonObject) getTableRow().getItem();
                    String raw = getString(row, "itemStatus", "");
                    switch (raw) {
                        case "ongoing"   -> getStyleClass().add("status-ongoing");
                        case "losing"    -> getStyleClass().add("status-losing");
                        case "won"       -> getStyleClass().add("status-won");
                        case "lost"      -> getStyleClass().add("status-lost");
                        case "cancelled" -> getStyleClass().add("status-cancelled");
                    }
                }
            }
        });

        filteredList = new FilteredList<>(masterList, p -> true);
        tableResult.setItems(filteredList);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Setup filter
    // ─────────────────────────────────────────────────────────────────────────

    private void setupFilter() {
        ToggleGroup group = new ToggleGroup();
        if (btn_filter_all       != null) { btn_filter_all.setToggleGroup(group); btn_filter_all.setSelected(true); }
        if (btn_filter_ongoing   != null)   btn_filter_ongoing.setToggleGroup(group);
        if (btn_filter_won       != null)   btn_filter_won.setToggleGroup(group);
        if (btn_filter_lost      != null)   btn_filter_lost.setToggleGroup(group);
        if (btn_filter_cancelled != null)   btn_filter_cancelled.setToggleGroup(group);

        group.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) { group.selectToggle(oldVal); return; }
            applyFilter();
        });
    }

    @FXML private void onFilterAll()       { activeFilter = "all";       applyFilter(); }
    @FXML private void onFilterOngoing()   { activeFilter = "ongoing";   applyFilter(); }
    @FXML private void onFilterWon()       { activeFilter = "won";        applyFilter(); }
    @FXML private void onFilterLost()      { activeFilter = "lost";       applyFilter(); }
    @FXML private void onFilterCancelled() { activeFilter = "cancelled";  applyFilter(); }

    private void applyFilter() {
        if (filteredList == null) return;
        if ("all".equals(activeFilter)) {
            filteredList.setPredicate(p -> true);
        } else {
            String f = activeFilter;
            // "ongoing" filter hiện cả ongoing + losing
            if ("ongoing".equals(f)) {
                filteredList.setPredicate(row -> {
                    String s = getString(row, "itemStatus", "");
                    return "ongoing".equals(s) || "losing".equals(s);
                });
            } else {
                filteredList.setPredicate(row -> f.equals(getString(row, "itemStatus", "")));
            }
        }
        updateSummary();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Load dữ liệu
    // ─────────────────────────────────────────────────────────────────────────

    private void loadData() {
        String userId = UserSession.getInstance().getUserId();
        new Thread(() -> {
            JsonObject req = new JsonObject();
            req.addProperty("action",     "GET_BID_RESULTS_BY_BIDDER");
            req.addProperty("bidder_id",  userId);
            JsonObject res = ServerConnection.sendAuthRequest(req);

            Platform.runLater(() -> {
                masterList.clear();
                if (res != null && "success".equals(getString(res, "status", ""))) {
                    JsonArray arr = res.get("message").getAsJsonArray();
                    for (JsonElement el : arr) masterList.add(el.getAsJsonObject());
                }
                applyFilter();
                updateCounters();
            });
        }, "BiddingResult-Load").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Counters & summary
    // ─────────────────────────────────────────────────────────────────────────

    private void updateCounters() {
        long ongoing   = count("ongoing") + count("losing");
        long won       = count("won");
        long lost      = count("lost");
        long cancelled = count("cancelled");

        if (lbl_count_ongoing   != null) lbl_count_ongoing  .setText(String.valueOf(ongoing));
        if (lbl_count_won       != null) lbl_count_won       .setText(String.valueOf(won));
        if (lbl_count_lost      != null) lbl_count_lost      .setText(String.valueOf(lost));
        if (lbl_count_cancelled != null) lbl_count_cancelled .setText(String.valueOf(cancelled));

        updateSummary();
    }

    private void updateSummary() {
        if (lbl_summary == null) return;
        long showing = filteredList != null ? filteredList.size() : masterList.size();
        long total   = masterList.size();
        lbl_summary.setText("Hiển thị " + showing + " / " + total + " phiên");
    }

    private long count(String status) {
        return masterList.stream()
                .filter(row -> status.equals(getString(row, "itemStatus", "")))
                .count();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String getString(JsonObject obj, String key, String fallback) {
        return (obj != null && obj.has(key) && !obj.get(key).isJsonNull())
                ? obj.get(key).getAsString() : fallback;
    }

    private String toVietnamese(String status) {
        return switch (status) {
            case "ongoing"   -> "🟡 Đang diễn ra (dẫn đầu)";
            case "losing"    -> "🔴 Đang diễn ra (đang thua)";
            case "won"       -> "🏆 Thắng";
            case "lost"      -> "❌ Thua";
            case "cancelled" -> "🚫 Bị hủy";
            default          -> status.isEmpty() ? "—" : status;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Điều hướng
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void back_to_dashboard(ActionEvent event) {
        unregisterEvents();
        try {
            Scene_Utils.Change_Scene(event, DASHBOARD_VIEW);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Real-time event handlers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Khi có bid mới: cập nhật bidAmount + itemStatus của row khớp itemId.
     * Nếu item chưa có trong danh sách (bidder vừa đặt giá lần đầu) → reload toàn bộ.
     */
    private void onBidPlaced(JsonObject payload) {
        if (!payload.has("item_id")) return;
        String itemId    = payload.get("item_id").getAsString();
        String myId      = UserSession.getInstance().getUserId();
        String bidderId  = payload.has("bidder_id") ? payload.get("bidder_id").getAsString() : "";
        double bidAmount = payload.has("bid_amount") ? payload.get("bid_amount").getAsDouble() : 0;

        Platform.runLater(() -> {
            // Tìm row của item này trong masterList
            boolean found = false;
            for (int i = 0; i < masterList.size(); i++) {
                JsonObject row = masterList.get(i);
                if (itemId.equals(getString(row, "itemId", ""))) {
                    found = true;
                    // Nếu bid mới là của chính mình → cập nhật bidAmount + status ongoing
                    if (myId.equals(bidderId)) {
                        row.addProperty("bidAmount",  bidAmount);
                        row.addProperty("itemStatus", "ongoing");
                        if (payload.has("bid_time")) {
                            row.addProperty("bidTime", payload.get("bid_time").getAsString());
                        }
                    } else {
                        // Người khác outbid → đổi status của mình sang losing (nếu đang ongoing)
                        if ("ongoing".equals(getString(row, "itemStatus", ""))) {
                            row.addProperty("itemStatus", "losing");
                        }
                    }
                    // Trigger refresh của cell bằng cách replace phần tử
                    masterList.set(i, row);
                    break;
                }
            }
            // Nếu chưa có (bidder vừa tham gia lần đầu) → reload để lấy row mới
            if (!found && myId.equals(bidderId)) {
                loadData();
                return;
            }
            applyFilter();
            updateCounters();
        });
    }

    /**
     * Khi phiên kết thúc: cập nhật itemStatus → won / lost cho row tương ứng.
     */
    private void onAuctionSettled(JsonObject payload) {
        if (!payload.has("item_id")) return;
        String itemId  = payload.get("item_id").getAsString();
        String myId    = UserSession.getInstance().getUserId();
        String winner  = payload.has("bidder_id") ? payload.get("bidder_id").getAsString() : "";

        Platform.runLater(() -> {
            for (int i = 0; i < masterList.size(); i++) {
                JsonObject row = masterList.get(i);
                if (itemId.equals(getString(row, "itemId", ""))) {
                    row.addProperty("itemStatus", myId.equals(winner) ? "won" : "lost");
                    masterList.set(i, row);
                    break;
                }
            }
            applyFilter();
            updateCounters();
        });
    }

    /**
     * Khi item bị hủy: cập nhật itemStatus → cancelled.
     */
    private void onItemCancelled(JsonObject payload) {
        if (!payload.has("item_id")) return;
        String itemId = payload.get("item_id").getAsString();

        Platform.runLater(() -> {
            for (int i = 0; i < masterList.size(); i++) {
                JsonObject row = masterList.get(i);
                if (itemId.equals(getString(row, "itemId", ""))) {
                    row.addProperty("itemStatus", "cancelled");
                    masterList.set(i, row);
                    break;
                }
            }
            applyFilter();
            updateCounters();
        });
    }

    /**
     * Khi admin xóa item (hard/soft delete): xóa row khỏi danh sách luôn.
     */
    private void onItemDeleted(JsonObject payload) {
        if (!payload.has("item_id")) return;
        String itemId = payload.get("item_id").getAsString();

        Platform.runLater(() -> {
            masterList.removeIf(row -> itemId.equals(getString(row, "itemId", "")));
            applyFilter();
            updateCounters();
        });
    }

    private void unregisterEvents() {
        EventDispatcher.unregisterGlobal(EventType.BID_PLACED, handlerKey);
        EventDispatcher.unregisterGlobal(EventType.AUCTION_SETTLED, handlerKey);
        EventDispatcher.unregisterGlobal(EventType.ITEM_CANCELLED, handlerKey);
        EventDispatcher.unregisterGlobal(EventType.ITEM_DELETED, handlerKey);
    }
}