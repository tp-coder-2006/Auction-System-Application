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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import org.auctionsystem.client.Connectivity.ServerConnection;
import org.auctionsystem.client.Controller.Scene_Utils;
import org.auctionsystem.client.session.UserSession;

import java.io.IOException;
import java.util.Objects;

public class Controller_View_Other_Profile {

    @FXML private TextField field_name;
    @FXML private TextField field_username;
    @FXML private TextField field_email;
    @FXML private TextField field_phone;

    @FXML private Circle    avatarCircle;
    @FXML private ImageView avatar_view;
    @FXML private Label     lbl_role_badge;

    // Chỉ hiện khi là seller
    @FXML private Label     lbl_rating_title;
    @FXML private HBox      hbox_stars;
    @FXML private Label     lbl_rating_text;
    @FXML private ImageView imageStar1;
    @FXML private ImageView imageStar2;
    @FXML private ImageView imageStar3;
    @FXML private ImageView imageStar4;
    @FXML private ImageView imageStar5;

    // Nút đánh giá (chỉ hiện nếu bidder đã mua hàng của seller này)
    @FXML private Button btn_rate_seller;

    private Image starImg;
    private Image emptyStarImg;

    // Lưu seller_id để gửi đánh giá
    private String currentSellerUsername;
    // Điểm đánh giá hiện tại của bidder này với seller (-1 nếu chưa đánh giá)
    private int currentExistingScore = -1;

    private static final String SEARCH_VIEW =
            "/org/auctionsystem/client/View/Search_User.fxml";

    // ── Khởi tạo ─────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        try {
            starImg      = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream("/org/auctionsystem/Icon/star.png")));
            emptyStarImg = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream("/org/auctionsystem/Icon/empty_star.png")));
        } catch (Exception e) {
            System.err.println("[ViewOtherProfile] Không tìm thấy ảnh ngôi sao: " + e.getMessage());
        }

        JsonObject info = Controller_Search_User.getPendingProfile();
        if (info != null) populateFields(info);
    }

    // ── Điền dữ liệu ─────────────────────────────────────────────────────────

    private void populateFields(JsonObject info) {
        setText(field_name,     getString(info, "name",     ""));
        setText(field_username, getString(info, "username", ""));
        setText(field_email,    getString(info, "email",    ""));
        setText(field_phone,    getString(info, "phone",    ""));

        // Role badge
        String role = getString(info, "role", "");
        String roleBadge = switch (role.toUpperCase()) {
            case "BIDDER" -> "🛒 Người mua";
            case "SELLER" -> "🏪 Người bán";
            case "ADMIN"  -> "🛡️ Quản trị viên";
            default       -> role;
        };
        if (lbl_role_badge != null) lbl_role_badge.setText(roleBadge);

        // Rating + nút đánh giá — chỉ hiện cho seller
        if ("SELLER".equalsIgnoreCase(role)) {
            double rating = 0;
            int cnt = 0;
            if (info.has("rating") && !info.get("rating").isJsonNull())
                rating = info.get("rating").getAsDouble();
            if (info.has("ratingCount") && !info.get("ratingCount").isJsonNull())
                cnt = info.get("ratingCount").getAsInt();

            setVisible(lbl_rating_title, true);
            setVisible(hbox_stars, true);
            setVisible(lbl_rating_text, true);
            setRatingStars(rating);
            if (lbl_rating_text != null)
                lbl_rating_text.setText(String.format("%.1f / 5.0  (%d lượt)", rating, cnt));

            // Lưu username để dùng khi gửi đánh giá
            currentSellerUsername = getString(info, "username", "");

            // Kiểm tra xem bidder hiện tại đã mua hàng từ seller này chưa
            String sellerId = getString(info, "id", "");
            if (!sellerId.isBlank()) {
                checkAndShowRateButton(sellerId);
            }
        }

        // Avatar
        String avatarUrl = getString(info, "avatarUrl", "");
        if (!avatarUrl.isBlank()) loadAvatar(avatarUrl);
    }

    // ── Kiểm tra lịch sử mua hàng → hiện nút đánh giá ───────────────────────

    private void checkAndShowRateButton(String sellerId) {
        String buyerId = UserSession.getInstance().getUserId();
        // Chỉ bidder mới có thể đánh giá
        String myRole = UserSession.getInstance().getRole();
        if (!"BIDDER".equalsIgnoreCase(myRole)) return;

        new Thread(() -> {
            // Bước 1: Kiểm tra đã mua hàng từ seller này chưa
            JsonObject reqBought = new JsonObject();
            reqBought.addProperty("action",    "CHECK_BOUGHT_FROM_SELLER");
            reqBought.addProperty("buyer_id",  buyerId);
            reqBought.addProperty("seller_id", sellerId);

            JsonObject resBought = ServerConnection.sendAuthRequest(reqBought);
            boolean hasBought = resBought != null
                    && "success".equals(getString(resBought, "status", ""))
                    && resBought.has("hasBought")
                    && resBought.get("hasBought").getAsBoolean();

            if (!hasBought) return; // chưa mua → không hiện nút

            // Bước 2: Kiểm tra đã đánh giá seller này chưa
            JsonObject reqRated = new JsonObject();
            reqRated.addProperty("action",     "CHECK_ALREADY_RATED");
            reqRated.addProperty("bidder_id",  buyerId);
            reqRated.addProperty("seller_id",  sellerId);

            JsonObject resRated = ServerConnection.sendAuthRequest(reqRated);
            boolean hasRated = resRated != null
                    && "success".equals(getString(resRated, "status", ""))
                    && resRated.has("hasRated")
                    && resRated.get("hasRated").getAsBoolean();
            int existingScore = (resRated != null && resRated.has("existingScore"))
                    ? resRated.get("existingScore").getAsInt() : -1;

            Platform.runLater(() -> {
                setVisible(btn_rate_seller, true);
                if (hasRated) {
                    // Đã đánh giá → đổi text nút thành "Sửa đánh giá" và lưu điểm cũ
                    btn_rate_seller.setText("✏️ Sửa đánh giá");
                    currentExistingScore = existingScore;
                } else {
                    btn_rate_seller.setText("⭐ Đánh giá");
                    currentExistingScore = -1;
                }
            });
        }, "ViewOtherProfile-CheckBought").start();
    }

    // ── Xử lý nút đánh giá ───────────────────────────────────────────────────

    @FXML
    private void onRateSeller(ActionEvent event) {
        if (currentSellerUsername == null || currentSellerUsername.isBlank()) return;
        showRatingDialog();
    }

    private void showRatingDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Đánh giá Seller");

        boolean isEdit = currentExistingScore != -1;
        dialog.setHeaderText(isEdit
                ? "Sửa đánh giá seller: @" + currentSellerUsername + "  (điểm hiện tại: " + currentExistingScore + "/5)"
                : "Đánh giá seller: @" + currentSellerUsername);

        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        content.setAlignment(Pos.CENTER_LEFT);

        Label lbInstruct = new Label(isEdit
                ? "Chọn điểm mới (1 – 5):"
                : "Chọn số sao (1 – 5):");

        // Pre-fill slider với điểm cũ nếu đang sửa
        double initialValue = isEdit ? currentExistingScore : 5;
        Slider slider = new Slider(1, 5, initialValue);
        slider.setBlockIncrement(1);
        slider.setMajorTickUnit(1);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        slider.setPrefWidth(280);

        HBox starRow = new HBox(6);
        starRow.setAlignment(Pos.CENTER_LEFT);
        Label[] stars = new Label[5];
        for (int i = 0; i < 5; i++) {
            stars[i] = new Label("⭐");
            stars[i].setStyle("-fx-font-size: 22px;");
        }
        starRow.getChildren().addAll(stars);

        Label lbScore = new Label((int) initialValue + " / 5");
        lbScore.setStyle("-fx-font-size: 13px; -fx-text-fill: #8d6e63;");

        // Khởi tạo trạng thái ngôi sao theo giá trị ban đầu
        for (int i = 0; i < 5; i++)
            stars[i].setStyle("-fx-font-size: 22px; -fx-opacity: " + (i < (int) initialValue ? "1.0" : "0.3") + ";");

        slider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int score = (int) Math.round(newVal.doubleValue());
            lbScore.setText(score + " / 5");
            for (int i = 0; i < 5; i++)
                stars[i].setStyle("-fx-font-size: 22px; -fx-opacity: " + (i < score ? "1.0" : "0.3") + ";");
        });

        content.getChildren().addAll(lbInstruct, starRow, slider, lbScore);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText(isEdit ? "Cập nhật đánh giá" : "Gửi đánh giá");
        okButton.setStyle("-fx-background-color: #c8a96e; -fx-text-fill: white;");

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                int score = (int) Math.round(slider.getValue());
                sendRating(score);
            }
        });
    }

    private void sendRating(int score) {
        new Thread(() -> {
            JsonObject req = new JsonObject();
            req.addProperty("action",          "UPDATE_RATING");
            req.addProperty("seller_username", currentSellerUsername);
            req.addProperty("rating",          score);

            JsonObject res = ServerConnection.sendAuthRequest(req);
            Platform.runLater(() -> {
                if (res != null && "success".equals(getString(res, "status", ""))) {
                    boolean wasEdit = res.has("isEdit") && res.get("isEdit").getAsBoolean();
                    showAlert(Alert.AlertType.INFORMATION,
                            wasEdit ? "Cập nhật đánh giá thành công!"
                                    : "Đánh giá thành công! Cảm ơn bạn đã phản hồi.");
                    // Sau khi đánh giá thành công → đổi nút thành "Sửa đánh giá"
                    currentExistingScore = score;
                    if (btn_rate_seller != null) btn_rate_seller.setText("✏️ Sửa đánh giá");
                } else {
                    String msg = (res != null && res.has("message"))
                            ? res.get("message").getAsString()
                            : "Không thể gửi đánh giá. Vui lòng thử lại.";
                    showAlert(Alert.AlertType.ERROR, msg);
                }
            });
        }, "ViewOtherProfile-SendRating").start();
    }

    // ── Ngôi sao rating ──────────────────────────────────────────────────────

    private void setRatingStars(double rating) {
        ImageView[] stars = {imageStar1, imageStar2, imageStar3, imageStar4, imageStar5};
        for (int i = 0; i < stars.length; i++) {
            if (stars[i] == null) continue;
            boolean filled = rating >= (i + 1);
            if (starImg != null && emptyStarImg != null)
                stars[i].setImage(filled ? starImg : emptyStarImg);
        }
    }

    // ── Load avatar ───────────────────────────────────────────────────────────

    private void loadAvatar(String url) {
        new Thread(() -> {
            try {
                JsonObject req = new JsonObject();
                req.addProperty("action",    "GET_IMAGE");
                req.addProperty("image_url", url);
                JsonObject res = ServerConnection.sendAuthRequest(req);
                if (res != null && "success".equals(getString(res, "status", ""))
                        && res.has("image_data")) {
                    byte[] bytes = java.util.Base64.getDecoder()
                            .decode(res.get("image_data").getAsString());
                    Image img = new Image(new java.io.ByteArrayInputStream(bytes));
                    Platform.runLater(() -> {
                        if (avatarCircle != null) {
                            avatarCircle.setFill(new ImagePattern(img));
                        }
                        if (avatar_view != null) {
                            avatar_view.setImage(null);
                            avatar_view.setVisible(false);
                        }
                    });
                }
            } catch (Exception e) {
                System.err.println("[ViewOtherProfile] Lỗi load avatar: " + e.getMessage());
            }
        }, "ViewOtherProfile-LoadAvatar").start();
    }

    // ── Điều hướng ────────────────────────────────────────────────────────────

    @FXML
    public void back_to_search(ActionEvent event) {
        Controller_Search_User.clearPending();
        try {
            Scene_Utils.Change_Scene(event, SEARCH_VIEW);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void setText(TextField tf, String text) {
        if (tf != null) tf.setText(text != null ? text : "");
    }

    private static String getString(JsonObject obj, String key, String fallback) {
        return (obj != null && obj.has(key) && !obj.get(key).isJsonNull())
                ? obj.get(key).getAsString() : fallback;
    }

    private static void setVisible(javafx.scene.Node node, boolean v) {
        if (node != null) { node.setVisible(v); node.setManaged(v); }
    }

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
