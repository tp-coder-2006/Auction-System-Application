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

import java.io.IOException;

/**
 * Controller_Admin_User_Management — màn hình admin quản lý người dùng.
 *
 * Chức năng:
 *   - Tải toàn bộ danh sách user (ADMIN_GET_ALL_USERS)
 *   - Tìm kiếm realtime theo username / họ tên / email
 *   - Khóa / mở khóa tài khoản (ADMIN_BAN_USER / ADMIN_UNBAN_USER)
 */
public class Controller_Admin_User_Management {

    // ─── FXML fields ──────────────────────────────────────────────────────────
    @FXML private TableView<UserRow>           tableUsers;
    @FXML private TableColumn<UserRow, String> col_username;
    @FXML private TableColumn<UserRow, String> col_fullname;
    @FXML private TableColumn<UserRow, String> col_email;
    @FXML private TableColumn<UserRow, String> col_role;
    @FXML private TableColumn<UserRow, String> col_status;
    @FXML private TableColumn<UserRow, String> col_action;   // String để dùng setCellFactory

    @FXML private TextField field_search_user;
    @FXML private Button    btn_search;

    private final ObservableList<UserRow> allUsers = FXCollections.observableArrayList();

    // ─── Khởi tạo ─────────────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        col_username.setCellValueFactory(new PropertyValueFactory<>("username"));
        col_fullname.setCellValueFactory(new PropertyValueFactory<>("fullname"));
        col_email   .setCellValueFactory(new PropertyValueFactory<>("email"));
        col_role    .setCellValueFactory(new PropertyValueFactory<>("role"));
        col_status  .setCellValueFactory(new PropertyValueFactory<>("status"));

        setupActionColumn();
        loadUsers();

        if (field_search_user != null) {
            field_search_user.textProperty().addListener((obs, o, n) -> filterUsers(n));
        }
        if (btn_search != null) {
            btn_search.setOnAction(e -> filterUsers(field_search_user.getText()));
        }
    }

    // ─── Cột hành động (Khóa / Mở khóa) ──────────────────────────────────────
    private void setupActionColumn() {
        col_action.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button();
            {
                btn.setOnAction(e -> {
                    UserRow row = getTableView().getItems().get(getIndex());
                    toggleBan(row);
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                UserRow row = getTableView().getItems().get(getIndex());
                if ("ADMIN".equalsIgnoreCase(row.getRawRole())) {
                    setGraphic(null);
                    return;
                }
                boolean active = row.isActiveStatus();
                btn.setText(active ? "🔒 Khóa" : "🔓 Mở khóa");
                btn.setStyle(active
                        ? "-fx-background-color:#e74c3c;-fx-text-fill:white;-fx-cursor:hand;"
                        : "-fx-background-color:#27ae60;-fx-text-fill:white;-fx-cursor:hand;");
                setGraphic(btn);
            }
        });
    }

    // ─── Tải danh sách user từ server ─────────────────────────────────────────
    private void loadUsers() {
        new Thread(() -> {
            JsonObject req = new JsonObject();
            req.addProperty("action", "ADMIN_GET_ALL_USERS");
            JsonObject resp = ServerConnection.sendAuthRequest(req);

            Platform.runLater(() -> {
                allUsers.clear();
                if (resp == null) {
                    showAlert("Lỗi", "Không thể kết nối tới server!", Alert.AlertType.ERROR);
                    return;
                }
                if (!"success".equals(resp.get("status").getAsString())) {
                    showAlert("Lỗi", resp.get("message").getAsString(), Alert.AlertType.ERROR);
                    return;
                }
                JsonArray arr = resp.get("message").getAsJsonArray();
                for (JsonElement el : arr) {
                    JsonObject u = el.getAsJsonObject();
                    String email = (u.has("email") && !u.get("email").isJsonNull())
                            ? u.get("email").getAsString() : "";
                    // Gson serializes boolean field "isActive" → key "isActive" (not "active")
                    boolean active = false;
                    if (u.has("isActive") && !u.get("isActive").isJsonNull()) {
                        active = u.get("isActive").getAsBoolean();
                    } else if (u.has("active") && !u.get("active").isJsonNull()) {
                        active = u.get("active").getAsBoolean();
                    }
                    // role may be a JsonObject (enum) or a plain string
                    String roleStr;
                    if (u.has("role") && !u.get("role").isJsonNull()) {
                        if (u.get("role").isJsonPrimitive()) {
                            roleStr = u.get("role").getAsString();
                        } else {
                            roleStr = u.get("role").toString();
                        }
                    } else {
                        roleStr = "UNKNOWN";
                    }
                    allUsers.add(new UserRow(
                            u.get("id").getAsString(),
                            u.get("username").getAsString(),
                            u.get("name").getAsString(),
                            email,
                            roleStr,
                            active
                    ));
                }
                tableUsers.setItems(allUsers);
            });
        }, "AdminUserMgmt-Load").start();
    }

    // ─── Khóa / mở khóa ───────────────────────────────────────────────────────
    private void toggleBan(UserRow row) {
        boolean willBan = row.isActiveStatus();
        String msg = willBan
                ? "Khóa tài khoản \"" + row.getUsername() + "\"?"
                : "Mở khóa tài khoản \"" + row.getUsername() + "\"?";
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.YES, ButtonType.NO);
        confirm.setTitle(willBan ? "Xác nhận khóa" : "Xác nhận mở khóa");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.YES) return;
            new Thread(() -> {
                JsonObject req = new JsonObject();
                req.addProperty("action", willBan ? "ADMIN_BAN_USER" : "ADMIN_UNBAN_USER");
                req.addProperty("user_id", row.getId());
                JsonObject resp = ServerConnection.sendAuthRequest(req);
                Platform.runLater(() -> {
                    if (resp == null) { showAlert("Lỗi", "Không kết nối được server!", Alert.AlertType.ERROR); return; }
                    if ("success".equals(resp.get("status").getAsString())) {
                        showAlert("Thành công", resp.get("message").getAsString(), Alert.AlertType.INFORMATION);
                        loadUsers();
                    } else {
                        showAlert("Lỗi", resp.get("message").getAsString(), Alert.AlertType.ERROR);
                    }
                });
            }, "AdminUserMgmt-Ban").start();
        });
    }

    // ─── Lọc theo từ khóa ─────────────────────────────────────────────────────
    private void filterUsers(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            tableUsers.setItems(allUsers);
            return;
        }
        String lower = keyword.toLowerCase();
        ObservableList<UserRow> filtered = FXCollections.observableArrayList();
        for (UserRow row : allUsers) {
            if (row.getUsername().toLowerCase().contains(lower)
                    || row.getFullname().toLowerCase().contains(lower)
                    || row.getEmail().toLowerCase().contains(lower)) {
                filtered.add(row);
            }
        }
        tableUsers.setItems(filtered);
    }

    // ─── Quay lại ─────────────────────────────────────────────────────────────
    @FXML
    public void back_to_admin_dashboard(ActionEvent event) {
        try { Scene_Utils.Change_Scene(event, "/org/auctionsystem/client/View/Admin_Dashboard.fxml"); }
        catch (IOException e) { throw new RuntimeException(e); }
    }

    // ─── Helper ───────────────────────────────────────────────────────────────
    private void showAlert(String title, String msg, Alert.AlertType type) {
        Alert a = new Alert(type); a.setTitle(title); a.setHeaderText(null);
        a.setContentText(msg); a.showAndWait();
    }

    // ─── Model row ────────────────────────────────────────────────────────────
    public static class UserRow {
        private final String  id, username, fullname, email, rawRole;
        private final boolean activeStatus;

        public UserRow(String id, String username, String fullname,
                       String email, String rawRole, boolean activeStatus) {
            this.id = id; this.username = username; this.fullname = fullname;
            this.email = email; this.rawRole = rawRole; this.activeStatus = activeStatus;
        }

        public String  getId()          { return id; }
        public String  getUsername()    { return username; }
        public String  getFullname()    { return fullname; }
        public String  getEmail()       { return email; }
        public String  getRawRole()     { return rawRole; }
        public boolean isActiveStatus() { return activeStatus; }

        // Cột hiển thị
        public String getRole()   { return capitalize(rawRole); }
        public String getStatus() { return activeStatus ? "✅ Hoạt động" : "🔒 Đã khóa"; }

        private String capitalize(String s) {
            if (s == null || s.isEmpty()) return s;
            return s.substring(0,1).toUpperCase() + s.substring(1).toLowerCase();
        }
    }
}