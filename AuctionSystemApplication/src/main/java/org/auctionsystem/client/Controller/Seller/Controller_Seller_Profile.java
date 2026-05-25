package org.auctionsystem.client.Controller.Seller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.auctionsystem.client.Controller.Scene_Utils;
import org.auctionsystem.client.session.UserSession;

import java.io.IOException;
import java.util.Objects;

public class Controller_Seller_Profile {
    @FXML
    public void back_to_seller_dashboard(ActionEvent event) {
        try {
            Scene_Utils.Change_Scene(event, "/org/auctionsystem/client/View/Seller_Dashboard.fxml");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    @FXML private TextField TextField_HoVaTen, TextField_TenNguoiDung, TextField_Email, TextField_SoDienThoai;
    @FXML private PasswordField PasswordField_MatKhau;
    @FXML private Button Button_ChinhSua;

    @FXML private ImageView imageStar1;
    @FXML private ImageView imageStar2;
    @FXML private ImageView imageStar3;
    @FXML private ImageView imageStar4;
    @FXML private ImageView imageStar5;

    private Image star;
    private Image empty_star;

    @FXML
    public void initialize() {
        star = new Image(Objects.requireNonNull(
                getClass().getResourceAsStream("/org/auctionsystem/Icon/star.png")));
        empty_star = new Image(Objects.requireNonNull(
                getClass().getResourceAsStream("/org/auctionsystem/Icon/empty_star.png")));

        // Đọc trực tiếp từ UserSession — dữ liệu đã được cập nhật
        // trong Go_to_profile() trước khi chuyển sang màn hình này
        // Thứ tự khớp FXML: name → username → password → email → phone
        UserSession s = UserSession.getInstance();
        TextField_HoVaTen.setText(s.getName());
        TextField_TenNguoiDung.setText(s.getUsername());
        PasswordField_MatKhau.setText("");                                         // không hiển thị mật khẩu
        TextField_Email.setText(s.getEmail());
        TextField_SoDienThoai.setText(s.getPhone() != null ? s.getPhone() : ""); // nullable

        // rating nullable — 0 sao nếu chưa có đánh giá nào
        setRatingStars(s.getRating() != null ? s.getRating() : 0);
    }
    /**
     * Hàm tự động đổi ảnh ngôi sao đen/rỗng theo số điểm số
     * @param rating điểm đánh giá (từ 0.0 đến 5.0)
     */
    private void setRatingStars(double rating) {
        ImageView[] stars = {imageStar1, imageStar2, imageStar3, imageStar4, imageStar5};
        for (int i = 0; i < stars.length; i++) {
            if (rating >= (i + 1)) {
                stars[i].setImage(star);
            } else {
                stars[i].setImage(empty_star);
            }
        }
    }

    private boolean isEditing = false;

    @FXML
    private void onChinhSua(ActionEvent event) {
        isEditing = !isEditing;

        // Bật/tắt chỉnh sửa
        TextField_HoVaTen.setEditable(isEditing);
        TextField_TenNguoiDung.setEditable(isEditing);
        TextField_Email.setEditable(isEditing);
        TextField_SoDienThoai.setEditable(isEditing);
        PasswordField_MatKhau.setEditable(isEditing);

        if (isEditing) {
            Button_ChinhSua.setText("Lưu thông tin");
            // Highlight các field đang được chỉnh
            TextField_HoVaTen.setStyle("-fx-background-color: white; -fx-border-color: #029ef2;");
            TextField_TenNguoiDung.setStyle("-fx-background-color: white; -fx-border-color: #029ef2;");
            TextField_Email.setStyle("-fx-background-color: white; -fx-border-color: #029ef2;");
            TextField_SoDienThoai.setStyle("-fx-background-color: white; -fx-border-color: #029ef2;");
            PasswordField_MatKhau.setStyle("-fx-background-color: white; -fx-border-color: #029ef2;");
        } else {
            Button_ChinhSua.setText("Chỉnh sửa thông tin");
            // Gửi dữ liệu lên server ở đây
            luuThongTin();
            // Reset style
            TextField_HoVaTen.setStyle("");
            TextField_TenNguoiDung.setStyle("");
            TextField_Email.setStyle("");
            TextField_SoDienThoai.setStyle("");
            PasswordField_MatKhau.setStyle("");
        }
    }

    private void luuThongTin() {
        // Gọi ServerConnection để cập nhật
    }
}
