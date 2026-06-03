package org.auctionsystem.client.Controller.Bidder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import org.auctionsystem.client.Connectivity.ServerConnection;
import org.auctionsystem.client.Controller.Scene_Utils;

import java.io.IOException;

/**
 * Controller_Search_User
 *
 * - Khi vào màn hình: load toàn bộ user đang hoạt động (GET_ALL_ACTIVE_USERS).
 * - Thanh tìm kiếm addListener realtime: lọc local theo username/name/email.
 * - Nhấn vào một hàng: mở View_Other_Profile hiển thị đầy đủ thông tin.
 */
public class Controller_Search_User {

    // ── FXML bindings ────────────────────────────────────────────────────────
    @FXML private TextField                    field_username;
    @FXML private Label                        lbl_search_status;
    @FXML private TableView<UserRow>           table_users;
    @FXML private TableColumn<UserRow, String> col_username;
    @FXML private TableColumn<UserRow, String> col_name;
    @FXML private TableColumn<UserRow, String> col_role;
    @FXML private TableColumn<UserRow, String> col_rating;

    private static final String DASHBOARD_VIEW =
            "/org/auctionsystem/client/View/Bidder_Dashboard.fxml";
    private static final String PROFILE_VIEW =
            "/org/auctionsystem/client/View/View_Other_Profile.fxml";

    /** Dữ liệu truyền sang màn hình profile */
    private static JsonObject pendingProfile = null;

    public static JsonObject getPendingProfile() { return pendingProfile; }
    public static void clearPending()             { pendingProfile = null; }

    private final ObservableList<UserRow> allUsers  = FXCollections.observableArrayList();
    private final ObservableList<UserRow> displayed = FXCollections.observableArrayList();

    // ── Khởi tạo ─────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        col_username.setCellValueFactory(new PropertyValueFactory<>("username"));
        col_name    .setCellValueFactory(new PropertyValueFactory<>("name"));
        col_role    .setCellValueFactory(new PropertyValueFactory<>("roleBadge"));
        col_rating  .setCellValueFactory(new PropertyValueFactory<>("ratingText"));

        table_users.setItems(displayed);
        table_users.setPlaceholder(new Label("Đang tải danh sách người dùng..."));

        // Click vào hàng → mở profile
        table_users.setRowFactory(tv -> {
            TableRow<UserRow> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 1 && !row.isEmpty()) {
                    openProfile(row.getItem());
                }
            });
            return row;
        });

        // Listener realtime trên thanh tìm kiếm
        field_username.textProperty().addListener((obs, oldVal, newVal) ->
                filterLocally(newVal == null ? "" : newVal.trim()));

        // Load toàn bộ user ngay khi vào màn hình
        loadAllActiveUsers();
        hideStatus();
    }

    // ── Load tất cả user đang hoạt động ──────────────────────────────────────

    private void loadAllActiveUsers() {
        new Thread(() -> {
            JsonObject req = new JsonObject();
            req.addProperty("action", "GET_ALL_ACTIVE_USERS");
            JsonObject res = ServerConnection.sendAuthRequest(req);

            Platform.runLater(() -> {
                allUsers.clear();
                if (res == null) {
                    showStatus("❌ Không thể kết nối đến server.", false);
                    table_users.setPlaceholder(new Label("Không thể kết nối server."));
                    return;
                }
                if (!"success".equals(getString(res, "status", ""))) {
                    showStatus("❌ " + getString(res, "message", "Lỗi không xác định."), false);
                    return;
                }
                JsonArray arr = res.get("users").getAsJsonArray();
                for (JsonElement el : arr) {
                    allUsers.add(UserRow.from(el.getAsJsonObject()));
                }
                // Hiển thị tất cả (hoặc áp filter nếu user đã gõ trước)
                filterLocally(field_username.getText() == null ? "" : field_username.getText().trim());
                table_users.setPlaceholder(new Label("Không tìm thấy người dùng phù hợp."));
                hideStatus();
            });
        }, "SearchUser-LoadAll").start();
    }

    // ── Filter local realtime ─────────────────────────────────────────────────

    private void filterLocally(String keyword) {
        displayed.clear();
        if (keyword.isEmpty()) {
            displayed.addAll(allUsers);
            return;
        }
        String lower = keyword.toLowerCase();
        for (UserRow row : allUsers) {
            if (row.getUsername().toLowerCase().contains(lower)
                    || row.getName().toLowerCase().contains(lower)
                    || row.getEmail().toLowerCase().contains(lower)) {
                displayed.add(row);
            }
        }
    }

    // ── Mở profile khi click vào hàng ────────────────────────────────────────

    private void openProfile(UserRow row) {
        new Thread(() -> {
            JsonObject req = new JsonObject();
            req.addProperty("action",   "GET_OTHER_PROFILE");
            req.addProperty("username", row.getUsername());
            JsonObject res = ServerConnection.sendAuthRequest(req);

            Platform.runLater(() -> {
                if (res == null || !"success".equals(getString(res, "status", ""))) {
                    String msg = (res != null && res.has("message"))
                            ? res.get("message").getAsString() : "Không thể tải profile.";
                    showStatus("❌ " + msg, false);
                    return;
                }
                pendingProfile = res.get("information").getAsJsonObject();
                try {
                    FXMLLoader loader = new FXMLLoader(
                            getClass().getResource(PROFILE_VIEW));
                    Parent root = loader.load();
                    Stage stage = Scene_Utils.getPrimaryStage();
                    double w = stage.getWidth(), h = stage.getHeight();
                    boolean max = stage.isMaximized();
                    Scene scene = new Scene(root);
                    Scene_Utils.Apply_Default_CSS_Style(scene);
                    stage.setMaximized(false);
                    stage.setScene(scene);
                    if (max) stage.setMaximized(true);
                    else { stage.setWidth(w); stage.setHeight(h); }
                    stage.show();
                } catch (IOException ex) {
                    ex.printStackTrace();
                    showStatus("❌ Không thể mở trang profile.", false);
                }
            });
        }, "SearchUser-OpenProfile").start();
    }

    // ── Điều hướng ───────────────────────────────────────────────────────────

    @FXML
    public void back_to_dashboard(ActionEvent event) {
        try {
            Scene_Utils.Change_Scene(event, DASHBOARD_VIEW);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void showStatus(String msg, boolean isSuccess) {
        if (lbl_search_status == null) return;
        lbl_search_status.setText(msg);
        lbl_search_status.setStyle(isSuccess
                ? "-fx-text-fill: #27ae60;" : "-fx-text-fill: #e74c3c;");
        lbl_search_status.setVisible(true);
        lbl_search_status.setManaged(true);
    }

    private void hideStatus() {
        if (lbl_search_status == null) return;
        lbl_search_status.setVisible(false);
        lbl_search_status.setManaged(false);
    }

    private static String getString(JsonObject obj, String key, String fallback) {
        return (obj != null && obj.has(key) && !obj.get(key).isJsonNull())
                ? obj.get(key).getAsString() : fallback;
    }

    // ── Model row ─────────────────────────────────────────────────────────────

    public static class UserRow {
        private final String username, name, email, roleBadge, ratingText;

        public UserRow(String username, String name, String email,
                       String roleBadge, String ratingText) {
            this.username   = username;
            this.name       = name;
            this.email      = email;
            this.roleBadge  = roleBadge;
            this.ratingText = ratingText;
        }

        public static UserRow from(JsonObject u) {
            String username = get(u, "username", "—");
            String name     = get(u, "name",     "—");
            String email    = get(u, "email",    "");
            String rawRole  = get(u, "role",     "");

            String roleBadge = switch (rawRole.toUpperCase()) {
                case "BIDDER" -> "🛒 Người mua";
                case "SELLER" -> "🏪 Người bán";
                default       -> rawRole.isEmpty() ? "—" : rawRole;
            };

            String ratingText;
            if (u.has("rating") && !u.get("rating").isJsonNull()) {
                double r   = u.get("rating").getAsDouble();
                int    cnt = (u.has("ratingCount") && !u.get("ratingCount").isJsonNull())
                        ? u.get("ratingCount").getAsInt() : 0;
                int full = (int) Math.floor(r);
                String stars = "★".repeat(Math.max(0, full)) + "☆".repeat(Math.max(0, 5 - full));
                ratingText = stars + String.format(" %.1f (%d)", r, cnt);
            } else {
                ratingText = "Chưa có";
            }

            return new UserRow(username, name, email, roleBadge, ratingText);
        }

        private static String get(JsonObject o, String k, String fb) {
            return (o != null && o.has(k) && !o.get(k).isJsonNull())
                    ? o.get(k).getAsString() : fb;
        }

        public String getUsername()   { return username; }
        public String getName()       { return name; }
        public String getEmail()      { return email; }
        public String getRoleBadge()  { return roleBadge; }
        public String getRatingText() { return ratingText; }
    }
}
