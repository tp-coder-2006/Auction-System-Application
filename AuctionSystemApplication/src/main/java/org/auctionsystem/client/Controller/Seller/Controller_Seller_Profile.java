package org.auctionsystem.client.Controller.Seller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.auctionsystem.client.Controller.Scene_Utils;

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

    @FXML private ImageView imageStar1;
    @FXML private ImageView imageStar2;
    @FXML private ImageView imageStar3;
    @FXML private ImageView imageStar4;
    @FXML private ImageView imageStar5;

    private Image star;
    private Image empty_star;

    @FXML
    public void initialize() {
        try {
            star = new Image(Objects.requireNonNull(getClass().getResourceAsStream(
                    "/org/auctionsystem/Icon/star.png")));
            empty_star = new Image(Objects.requireNonNull(getClass().getResourceAsStream(
                    "/org/auctionsystem/Icon/empty_star.png")));
            // giả sử điểm rating là 3.6 để chạy thử chức năng (nhớ kết nối phần này với database nhá)
            double sellerRating = 3.6;
            setRatingStars(sellerRating);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
}
