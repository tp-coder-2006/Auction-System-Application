package org.auctionsystem.client.Controller.Seller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import org.auctionsystem.client.Connectivity.ServerConnection;
import org.auctionsystem.client.Controller.Scene_Utils;
import org.auctionsystem.client.event.EventDispatcher;
import org.auctionsystem.client.event.EventType;
import org.auctionsystem.client.session.UserSession;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Controller_My_Items {
    // UUID duy nhất cho mỗi instance — tránh ghi đè handler của cửa sổ khác
    private final String handlerKey = java.util.UUID.randomUUID().toString();


    @FXML private TableView<JsonObject>           tableMyItems;
    @FXML private TableColumn<JsonObject, String> col_name;
    @FXML private TableColumn<JsonObject, String> col_start_price;
    @FXML private TableColumn<JsonObject, String> col_current_price;
    @FXML private TableColumn<JsonObject, String> col_time;
    @FXML private TableColumn<JsonObject, String> col_status;
    @FXML private TableColumn<JsonObject, Void>   col_action;
    @FXML private Button                          btn_back;

    private final ObservableList<JsonObject> itemList = FXCollections.observableArrayList();

    private static final DateTimeFormatter DT_FMT     = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DT_DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");
    private static final String DASHBOARD_VIEW        = "/org/auctionsystem/client/View/Seller_Dashboard.fxml";
    private static final String EDIT_ITEM_VIEW        = "/org/auctionsystem/client/View/Edit_Item.fxml";
    private static final String SELLER_ITEM_DETAIL_VIEW = "/org/auctionsystem/client/View/Seller_Item_Detail.fxml";

    // ─────────────────────────────────────────────────────────────────────────
    //  Khởi tạo
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        setupColumns();
        loadItems();

        if (btn_back != null)
            btn_back.setOnAction(this::back_to_dashboard);

        EventDispatcher.registerGlobal(EventType.ITEM_DELETED, handlerKey, payload -> {
            String itemId = payload.has("item_id") ? payload.get("item_id").getAsString() : "";
            if (!itemId.isEmpty()) {
                Platform.runLater(() -> itemList.removeIf(item ->
                        itemId.equals(item.has("id") ? item.get("id").getAsString() : "")));
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Setup cột
    // ─────────────────────────────────────────────────────────────────────────

    private void setupColumns() {
        col_name.setCellValueFactory(d ->
                new SimpleStringProperty(getString(d.getValue(), "name")));

        col_name.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty || name == null || getIndex() >= getTableView().getItems().size()) {
                    setText(null);
                    setStyle("");
                    setOnMouseClicked(null);
                    return;
                }
                setText(name);
                JsonObject item = getTableView().getItems().get(getIndex());
                setStyle("-fx-cursor: hand; -fx-text-fill: #1565c0; -fx-underline: true;");
                setOnMouseClicked(e -> openItemDetail(item));
            }
        });

        col_start_price.setCellValueFactory(d ->
                new SimpleStringProperty(money(d.getValue(), "startingPrice")));

        col_current_price.setCellValueFactory(d -> {
            JsonObject item = d.getValue();
            boolean hasHighest = item.has("currentHighestPrice")
                    && !item.get("currentHighestPrice").isJsonNull();
            return new SimpleStringProperty(hasHighest
                    ? money(item, "currentHighestPrice") : "—");
        });

        col_time.setCellValueFactory(d ->
                new SimpleStringProperty(datetime(d.getValue(), "startTime")));

        col_status.setCellValueFactory(d ->
                new SimpleStringProperty(getString(d.getValue(), "status")));

        // Cột hành động: nút Sửa + Hủy/Xóa tùy trạng thái
        col_action.setCellFactory(tc -> new TableCell<>() {

            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }
                JsonObject item = getTableView().getItems().get(getIndex());
                String status   = getString(item, "status").toUpperCase();

                switch (status) {
                    case "CANCELLED" -> {
                        Button btnRelist = new Button("Bán lại");
                        btnRelist.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
                        btnRelist.setOnAction(e -> openRelistItem(item));

                        Button btnXoa = new Button("Xóa");
                        btnXoa.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
                        btnXoa.setOnAction(e -> confirmDeleteItem(item));

                        setGraphic(new HBox(6, btnRelist, btnXoa));
                    }
                    case "PENDING" -> {
                        Button btnEdit = new Button("Sửa");
                        btnEdit.setOnAction(e -> openEditItem(item));

                        Button btnHuy = new Button("Hủy");
                        btnHuy.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white;");
                        btnHuy.setOnAction(e -> confirmCancelItem(item));

                        setGraphic(new HBox(6, btnEdit, btnHuy));
                    }
                    case "ACTIVE" -> {
                        setGraphic(null);
                    }
                    default -> {
                        Button btnXoa = new Button("Xóa");
                        btnXoa.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
                        btnXoa.setOnAction(e -> confirmDeleteItem(item));

                        setGraphic(new HBox(6, btnXoa));
                    }
                }
            }
        });

        tableMyItems.setItems(itemList);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Load dữ liệu — GET_ITEMS_BY_SELLER
    // ─────────────────────────────────────────────────────────────────────────

    private void loadItems() {
        new Thread(() -> {
            JsonObject req = new JsonObject();
            req.addProperty("action",    "GET_ITEMS_BY_SELLER");
            req.addProperty("seller_id", UserSession.getInstance().getUserId());
            JsonObject res = ServerConnection.sendAuthRequest(req);

            Platform.runLater(() -> {
                itemList.clear();
                if (res != null && "success".equals(res.get("status").getAsString())) {
                    JsonArray arr = res.get("message").getAsJsonArray();
                    for (JsonElement el : arr) itemList.add(el.getAsJsonObject());
                }
            });
        }, "MyItems-Load").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Real-time: cập nhật status item tại chỗ (không reload toàn bộ)
    // ─────────────────────────────────────────────────────────────────────────

    private void updateItemStatus(JsonObject payload, String newStatus) {
        if (!payload.has("item_id")) return;
        String itemId = payload.get("item_id").getAsString();
        Platform.runLater(() -> {
            for (JsonObject item : itemList) {
                if (itemId.equals(item.has("id") ? item.get("id").getAsString() : "")) {
                    item.addProperty("status", newStatus);
                    break;
                }
            }
            tableMyItems.refresh();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Xem chi tiết item (tất cả trạng thái)
    // ─────────────────────────────────────────────────────────────────────────

    private void openItemDetail(JsonObject item) {
        Controller_Seller_Item_Detail.setCurrentItem(item);
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource(SELLER_ITEM_DETAIL_VIEW));
            javafx.scene.Parent root = loader.load();
            javafx.stage.Stage stage = Scene_Utils.getPrimaryStage();
            double w = stage.getWidth(), h = stage.getHeight();
            boolean max = stage.isMaximized();
            javafx.scene.Scene scene = new javafx.scene.Scene(root);
            Scene_Utils.Apply_Default_CSS_Style(scene);
            stage.setMaximized(false);
            stage.setScene(scene);
            if (max) stage.setMaximized(true);
            else { stage.setWidth(w); stage.setHeight(h); }
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Bán lại item (CANCELLED)
    // ─────────────────────────────────────────────────────────────────────────

    private void openRelistItem(JsonObject item) {
        Controller_Edit_Item.setCurrentItem(item);
        Controller_Edit_Item.setRelistMode(true);
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource(EDIT_ITEM_VIEW));
            javafx.scene.Parent root = loader.load();
            javafx.stage.Stage stage = Scene_Utils.getPrimaryStage();
            double w = stage.getWidth(), h = stage.getHeight();
            boolean max = stage.isMaximized();
            javafx.scene.Scene scene = new javafx.scene.Scene(root);
            Scene_Utils.Apply_Default_CSS_Style(scene);
            stage.setMaximized(false);
            stage.setScene(scene);
            if (max) stage.setMaximized(true);
            else { stage.setWidth(w); stage.setHeight(h); }
            stage.show();
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Edit item
    // ─────────────────────────────────────────────────────────────────────────

    private void openEditItem(JsonObject item) {
        Controller_Edit_Item.setCurrentItem(item);
        Controller_Edit_Item.setRelistMode(false);
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource(EDIT_ITEM_VIEW));
            javafx.scene.Parent root = loader.load();
            javafx.stage.Stage stage = Scene_Utils.getPrimaryStage();
            double w = stage.getWidth(), h = stage.getHeight();
            boolean max = stage.isMaximized();
            javafx.scene.Scene scene = new javafx.scene.Scene(root);
            Scene_Utils.Apply_Default_CSS_Style(scene);
            stage.setMaximized(false);
            stage.setScene(scene);
            if (max) stage.setMaximized(true);
            else { stage.setWidth(w); stage.setHeight(h); }
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Hủy item — CANCEL_ITEM
    // ─────────────────────────────────────────────────────────────────────────

    private void confirmCancelItem(JsonObject item) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Hủy sản phẩm");
        confirm.setHeaderText("Bạn sắp hủy: " + getString(item, "name"));
        confirm.setContentText("Hành động này không thể hoàn tác. Tiếp tục?");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) cancelItem(item);
        });
    }

    private void cancelItem(JsonObject item) {
        new Thread(() -> {
            JsonObject req = new JsonObject();
            req.addProperty("action",    "CANCEL_ITEM");
            req.addProperty("item_id",   getString(item, "id"));
            req.addProperty("seller_id", UserSession.getInstance().getUserId());
            JsonObject res = ServerConnection.sendAuthRequest(req);

            Platform.runLater(() -> {
                if (res != null && "success".equals(res.get("status").getAsString())) {
                    item.addProperty("status", "CANCELLED");
                    tableMyItems.refresh();
                } else {
                    String msg = (res != null && res.has("message"))
                            ? res.get("message").getAsString() : "Lỗi kết nối server.";
                    showAlert(Alert.AlertType.ERROR, "Lỗi", msg);
                }
            });
        }, "MyItems-Cancel").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Xóa item — DELETE_ITEM
    // ─────────────────────────────────────────────────────────────────────────

    private void confirmDeleteItem(JsonObject item) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xóa sản phẩm");
        confirm.setHeaderText("Bạn sắp xóa: " + getString(item, "name"));
        confirm.setContentText("Sản phẩm sẽ bị ẩn khỏi hệ thống. Tiếp tục?");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) deleteItem(item);
        });
    }

    private void deleteItem(JsonObject item) {
        new Thread(() -> {
            JsonObject req = new JsonObject();
            req.addProperty("action",    "DELETE_ITEM");
            req.addProperty("item_id",   getString(item, "id"));
            req.addProperty("seller_id", UserSession.getInstance().getUserId());
            JsonObject res = ServerConnection.sendAuthRequest(req);

            Platform.runLater(() -> {
                if (res != null && "success".equals(res.get("status").getAsString())) {
                    itemList.remove(item);
                } else {
                    String msg = (res != null && res.has("message"))
                            ? res.get("message").getAsString() : "Lỗi kết nối server.";
                    showAlert(Alert.AlertType.ERROR, "Lỗi", msg);
                }
            });
        }, "MyItems-Delete").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Điều hướng
    // ─────────────────────────────────────────────────────────────────────────

    public void back_to_dashboard(ActionEvent event) {
        try {
            Scene_Utils.Change_Scene(event, DASHBOARD_VIEW);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static String getString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : "";
    }

    private static String money(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return "";
        return String.format("%,.0f ₫", obj.get(key).getAsDouble());
    }

    private static String datetime(JsonObject obj, String key) {
        String raw = getString(obj, key);
        if (raw.isBlank()) return "—";
        try {
            return LocalDateTime.parse(raw.replace("T", " "), DT_FMT).format(DT_DISPLAY);
        } catch (Exception e) { return raw; }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert a = new Alert(type);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(content);
        a.showAndWait();
    }
}