package org.auctionsystem.client.Controller.Seller;

import javafx.fxml.FXML;
import javafx.event.ActionEvent;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.auctionsystem.client.Controller.Scene_Utils;
import org.auctionsystem.client.session.UserSession;

import java.io.IOException;
import java.util.Objects;

public class Controller_Seller_Profile {

    @FXML private TextField     field_name;
    @FXML private TextField     field_username;
    @FXML private PasswordField field_password;
    @FXML private TextField     field_email;
    @FXML private TextField     field_phone;

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

        UserSession s = UserSession.getInstance();

        // Thứ tự khớp với FXML: name → username → password → email → phone
        field_name    .setText(s.getName());                                          // không null
        field_username.setText(s.getUsername());                                      // không null
        field_password.setText("");                                                    // không hiển thị mật khẩu
        field_email   .setText(s.getEmail());                                         // không null
        field_phone   .setText(s.getPhone() != null ? s.getPhone() : "");            // nullable

        // rating nullable — 0 sao nếu chưa có đánh giá nào
        setRatingStars(s.getRating() != null ? s.getRating() : 0);
    }

    private void setRatingStars(double rating) {
        ImageView[] stars = {imageStar1, imageStar2, imageStar3, imageStar4, imageStar5};
        for (int i = 0; i < stars.length; i++) {
            stars[i].setImage(rating >= (i + 1) ? star : empty_star);
        }
    }

    @FXML
    public void back_to_seller_dashboard(ActionEvent event) {
        try {
            Scene_Utils.Change_Scene(event, "/org/auctionsystem/client/View/Seller_Dashboard.fxml");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}