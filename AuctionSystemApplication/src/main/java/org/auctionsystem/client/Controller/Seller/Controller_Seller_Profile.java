package org.auctionsystem.client.Controller.Seller;

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
import java.util.Objects;

public class Controller_Seller_Profile {

    // fx:id khớp Seller_Profile.fxml
    @FXML private TextField TextField_HoVaTen;
    @FXML private TextField TextField_TenNguoiDung;
    @FXML private TextField TextField_Email;
    @FXML private TextField TextField_SoDienThoai;
    @FXML private Button    Button_ChinhSua;
    @FXML private Label     lbl_rating_text;
    @FXML private ImageView imageStar1;
    @FXML private ImageView imageStar2;
    @FXML private ImageView imageStar3;
    @FXML private ImageView imageStar4;
    @FXML private ImageView imageStar5;
    @FXML private Circle    avatarCircle;
    @FXML private ImageView avatarImageView;

    private Image star;
    private Image empty_star;
    private boolean isEditing = false;

    private static final String activeStyle  =
            "-fx-background-color: white; -fx-border-color: #029ef2; -fx-border-radius: 5px;";
    private static final String defaultStyle = "";

    // ─────────────────────────────────────────────────────────────────────────
    //  Khởi tạo
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        try {
            star       = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream("/org/auctionsystem/Icon/star.png")));
            empty_star = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream("/org/auctionsystem/Icon/empty_star.png")));
        } catch (Exception e) {
            System.err.println("[SellerProfile] Không tìm thấy ảnh ngôi sao: " + e.getMessage());
        }

        loadFromSession();
        loadAvatar();
        setupAvatarClickHandler();
    }

    private void loadFromSession() {
        UserSession s = UserSession.getInstance();
        TextField_HoVaTen     .setText(s.getName()     != null ? s.getName()     : "");
        TextField_TenNguoiDung.setText(s.getUsername() != null ? s.getUsername() : "");
        TextField_Email       .setText(s.getEmail()    != null ? s.getEmail()    : "");
        TextField_SoDienThoai .setText(s.getPhone()    != null ? s.getPhone()    : "");
        setFieldsEditable(false);

        double rating = s.getRating() != null ? s.getRating() : 0;
        setRatingStars(rating);
        if (lbl_rating_text != null) {
            lbl_rating_text.setText(String.format("%.1f / 5.0", rating));
        }
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
        }, "SellerProfile-LoadAvatar").start();
    }

    private void applyAvatarImage(Image img) {
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

        // Upload lên server
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
                    loadAvatar();
                }
            });
        }, "SellerProfile-UploadAvatar").start();
    }

    private void setRatingStars(double rating) {
        ImageView[] stars = {imageStar1, imageStar2, imageStar3, imageStar4, imageStar5};
        for (int i = 0; i < stars.length; i++) {
            if (stars[i] == null) continue;
            boolean filled = rating >= (i + 1);
            if (star != null && empty_star != null)
                stars[i].setImage(filled ? star : empty_star);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Chỉnh sửa / Lưu
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void onChinhSua(ActionEvent event) {
        isEditing = !isEditing;
        setFieldsEditable(isEditing);

        if (isEditing) {
            Button_ChinhSua.setText("Lưu thông tin");
            TextField_HoVaTen    .setStyle(activeStyle);
            TextField_Email      .setStyle(activeStyle);
            TextField_SoDienThoai.setStyle(activeStyle);
        } else {
            Button_ChinhSua.setText("Chỉnh sửa thông tin");
            TextField_HoVaTen    .setStyle(defaultStyle);
            TextField_Email      .setStyle(defaultStyle);
            TextField_SoDienThoai.setStyle(defaultStyle);
            luuThongTin();
        }
    }

    private void setFieldsEditable(boolean editable) {
        TextField_HoVaTen    .setEditable(editable);
        TextField_Email      .setEditable(editable);
        TextField_SoDienThoai.setEditable(editable);
        TextField_TenNguoiDung.setEditable(false); // username không cho sửa
    }

    private void luuThongTin() {
        String name  = TextField_HoVaTen    .getText().trim();
        String email = TextField_Email      .getText().trim();
        String phone = TextField_SoDienThoai.getText().trim();

        if (name.isEmpty() || email.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Họ tên và email không được để trống.");
            loadFromSession();
            return;
        }

        new Thread(() -> {
            JsonObject req = new JsonObject();
            req.addProperty("action",  "UPDATE_PROFILE");
            req.addProperty("user_id", UserSession.getInstance().getUserId());
            req.addProperty("name",    name);
            req.addProperty("email",   email);
            if (!phone.isEmpty()) req.addProperty("phone", phone);
            else req.addProperty("phone", (String) null);

            JsonObject res = ServerConnection.sendAuthRequest(req);
            Platform.runLater(() -> {
                if (res != null && "success".equals(res.get("status").getAsString())) {
                    UserSession s = UserSession.getInstance();
                    s.setName(name);
                    s.setEmail(email);
                    s.setPhone(phone.isEmpty() ? null : phone);
                    showAlert(Alert.AlertType.INFORMATION, "Thành công", "Cập nhật thông tin thành công!");
                } else {
                    String msg = (res != null && res.has("message"))
                            ? res.get("message").getAsString() : "Lỗi kết nối server.";
                    showAlert(Alert.AlertType.ERROR, "Lỗi", msg);
                    loadFromSession();
                }
            });
        }, "SellerProfile-Update").start();
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
                String oldPw  = pfOld.getText();
                String newPw  = pfNew.getText();
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
            req.addProperty("action",        "UPDATE_PASSWORD");
            req.addProperty("user_id",       UserSession.getInstance().getUserId());
            req.addProperty("old_password",  oldPw);
            req.addProperty("new_password",  newPw);

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
        }, "SellerProfile-ChangePassword").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Điều hướng
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void back_to_seller_dashboard(ActionEvent event) {
        try {
            Scene_Utils.Change_Scene(event, "/org/auctionsystem/client/View/Seller_Dashboard.fxml");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helper
    // ─────────────────────────────────────────────────────────────────────────

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(content);
        a.showAndWait();
    }
}
