package org.auctionsystem.client.Controller.Bidder;

import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import org.auctionsystem.client.Connectivity.ImageClient;
import org.auctionsystem.client.Connectivity.ServerConnection;
import org.auctionsystem.client.Controller.Scene_Utils;
import org.auctionsystem.client.session.UserSession;

import java.io.File;
import java.io.IOException;

public class Controller_Bidder_Profile {

    // fx:id khớp với Bidder_Profile.fxml
    @FXML private TextField field_name;
    @FXML private TextField field_username;
    @FXML private TextField field_email;
    @FXML private TextField field_phone;
    @FXML private Circle    avatarCircle;
    @FXML private ImageView avatarImageView;
    @FXML private Button    Button_ChinhSua;

    private boolean isEditing = false;

    private static final String activeStyle  =
            "-fx-background-color: white; -fx-border-color: #029ef2; -fx-border-radius: 5px; -fx-background-radius: 5px;";
    private static final String defaultStyle =
            "-fx-background-color: white; -fx-border-color: #dcdde1; -fx-border-radius: 5px; -fx-background-radius: 5px;";

    // ─────────────────────────────────────────────────────────────────────────
    //  Khởi tạo
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        loadFromSession();
        loadAvatar();
        setupAvatarClickHandler();
    }

    private void loadFromSession() {
        UserSession s = UserSession.getInstance();
        field_name    .setText(s.getName()     != null ? s.getName()     : "");
        field_username.setText(s.getUsername() != null ? s.getUsername() : "");
        field_email   .setText(s.getEmail()    != null ? s.getEmail()    : "");
        field_phone   .setText(s.getPhone()    != null ? s.getPhone()    : "");
        setFieldsEditable(false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Avatar
    // ─────────────────────────────────────────────────────────────────────────

    private void loadAvatar() {
        String avatarUrl = UserSession.getInstance().getAvatarUrl();
        if (avatarUrl == null || avatarUrl.isBlank()) return;

        new Thread(() -> {
            Image img = ImageClient.fetchImage(avatarUrl);
            if (img == null) return;
            Platform.runLater(() -> applyAvatarImage(img));
        }, "BidderProfile-LoadAvatar").start();
    }

    private void applyAvatarImage(Image img) {
        // Hỗ trợ cả Circle (clip tròn) và ImageView thông thường
        if (avatarCircle != null) {
            avatarCircle.setFill(new ImagePattern(img));
        }
        if (avatarImageView != null) {
            avatarImageView.setImage(img);
        }
    }

    private void setupAvatarClickHandler() {
        // Không còn click trực tiếp vào avatar — nút camera trong FXML đảm nhận
    }

    @FXML
    private void onChangeAvatar() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Chọn ảnh đại diện");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Ảnh", "*.jpg", "*.jpeg", "*.png", "*.webp"));

        // Lấy Window từ node bất kỳ đang hiển thị
        javafx.stage.Window window = (avatarCircle != null)
                ? avatarCircle.getScene().getWindow()
                : (avatarImageView != null ? avatarImageView.getScene().getWindow() : null);

        File file = fc.showOpenDialog(window);
        if (file == null) return;

        // Preview ngay lập tức
        try {
            Image previewImg = new Image(file.toURI().toString());
            applyAvatarImage(previewImg);
        } catch (Exception ignored) {}

        // Upload lên server trong background thread
        new Thread(() -> {
            JsonObject result = ImageClient.uploadAvatar(file, UserSession.getInstance().getUserId());
            Platform.runLater(() -> {
                if (result != null && "success".equals(result.get("status").getAsString())) {
                    String newAvatarUrl = result.has("avatar_url")
                            ? result.get("avatar_url").getAsString() : null;
                    if (newAvatarUrl != null) {
                        UserSession.getInstance().setAvatarUrl(newAvatarUrl);
                    }
                    showAlert(Alert.AlertType.INFORMATION, "Thành công", "Cập nhật ảnh đại diện thành công!");
                } else {
                    String msg = (result != null && result.has("message"))
                            ? result.get("message").getAsString() : "Lỗi upload ảnh.";
                    showAlert(Alert.AlertType.ERROR, "Lỗi", msg);
                    // Tải lại avatar cũ nếu upload thất bại
                    loadAvatar();
                }
            });
        }, "BidderProfile-UploadAvatar").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Chỉnh sửa / Lưu thông tin
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void onChinhSua(ActionEvent event) {
        isEditing = !isEditing;
        setFieldsEditable(isEditing);

        if (isEditing) {
            Button_ChinhSua.setText("Lưu thông tin");
            field_name .setStyle(activeStyle);
            field_email.setStyle(activeStyle);
            field_phone.setStyle(activeStyle);
        } else {
            Button_ChinhSua.setText("Chỉnh sửa thông tin");
            field_name .setStyle(defaultStyle);
            field_email.setStyle(defaultStyle);
            field_phone.setStyle(defaultStyle);
            luuThongTin();
        }
    }

    private void setFieldsEditable(boolean editable) {
        field_name    .setEditable(editable);
        field_email   .setEditable(editable);
        field_phone   .setEditable(editable);
        field_username.setEditable(false); // username không cho sửa
    }

    private void luuThongTin() {
        String name  = field_name .getText().trim();
        String email = field_email.getText().trim();
        String phone = field_phone.getText().trim();

        if (name.isEmpty() || email.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Họ tên và email không được để trống.");
            loadFromSession();
            return;
        }

        new Thread(() -> {
            JsonObject request = new JsonObject();
            request.addProperty("action",  "UPDATE_PROFILE");
            request.addProperty("user_id", UserSession.getInstance().getUserId());
            request.addProperty("name",    name);
            request.addProperty("email",   email);
            if (!phone.isEmpty()) request.addProperty("phone", phone);
            else request.addProperty("phone", (String) null);

            JsonObject response = ServerConnection.sendAuthRequest(request);

            Platform.runLater(() -> {
                if (response != null && "success".equals(response.get("status").getAsString())) {
                    UserSession s = UserSession.getInstance();
                    s.setName(name);
                    s.setEmail(email);
                    s.setPhone(phone.isEmpty() ? null : phone);
                    showAlert(Alert.AlertType.INFORMATION, "Thành công", "Cập nhật thông tin thành công!");
                } else {
                    String msg = (response != null && response.has("message"))
                            ? response.get("message").getAsString() : "Lỗi kết nối server.";
                    showAlert(Alert.AlertType.ERROR, "Lỗi", msg);
                    loadFromSession();
                }
            });
        }, "BidderProfile-Update").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Đổi mật khẩu
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void onDoiMatKhau(ActionEvent event) {
        showChangePasswordDialog();
    }

    private void showChangePasswordDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Đổi mật khẩu");
        dialog.setHeaderText("Thay đổi mật khẩu của bạn");

        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        content.setAlignment(Pos.CENTER_LEFT);

        Label lbOld = new Label("Mật khẩu hiện tại:");
        lbOld.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        PasswordField pfOld = new PasswordField();
        pfOld.setPromptText("Nhập mật khẩu hiện tại");
        pfOld.setPrefWidth(280);

        Label lbNew = new Label("Mật khẩu mới:");
        lbNew.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        PasswordField pfNew = new PasswordField();
        pfNew.setPromptText("Nhập mật khẩu mới");
        pfNew.setPrefWidth(280);

        Label lbConfirm = new Label("Xác nhận mật khẩu mới:");
        lbConfirm.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        PasswordField pfConfirm = new PasswordField();
        pfConfirm.setPromptText("Nhập lại mật khẩu mới");
        pfConfirm.setPrefWidth(280);

        content.getChildren().addAll(lbOld, pfOld, lbNew, pfNew, lbConfirm, pfConfirm);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText("Xác nhận");
        okButton.setStyle("-fx-background-color: #c8a96e; -fx-text-fill: white;");

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                String oldPw   = pfOld.getText();
                String newPw   = pfNew.getText();
                String confirm = pfConfirm.getText();

                if (oldPw.isBlank() || newPw.isBlank() || confirm.isBlank()) {
                    showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng điền đầy đủ thông tin.");
                    return;
                }
                if (!newPw.equals(confirm)) {
                    showAlert(Alert.AlertType.ERROR, "Lỗi", "Mật khẩu mới và xác nhận không khớp.");
                    return;
                }
                if (newPw.length() < 6) {
                    showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Mật khẩu mới phải có ít nhất 6 ký tự.");
                    return;
                }
                sendChangePassword(oldPw, newPw);
            }
        });
    }

    private void sendChangePassword(String oldPw, String newPw) {
        new Thread(() -> {
            JsonObject req = new JsonObject();
            req.addProperty("action",       "UPDATE_PASSWORD");
            req.addProperty("user_id",      UserSession.getInstance().getUserId());
            req.addProperty("old_password", oldPw);
            req.addProperty("new_password", newPw);

            JsonObject res = ServerConnection.sendAuthRequest(req);
            Platform.runLater(() -> {
                if (res != null && "success".equals(res.get("status").getAsString())) {
                    showAlert(Alert.AlertType.INFORMATION, "Thành công", "Đổi mật khẩu thành công!");
                } else {
                    String msg = (res != null && res.has("message"))
                            ? res.get("message").getAsString() : "Đổi mật khẩu thất bại.";
                    showAlert(Alert.AlertType.ERROR, "Lỗi", msg);
                }
            });
        }, "BidderProfile-ChangePassword").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Điều hướng
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void back_to_bidder_dashboard(ActionEvent event) {
        try {
            Scene_Utils.Change_Scene(event, "/org/auctionsystem/client/View/Bidder_Dashboard.fxml");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helper
    // ─────────────────────────────────────────────────────────────────────────

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
