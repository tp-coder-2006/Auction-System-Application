package org.auctionsystem.client.Controller.Seller;

import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import org.auctionsystem.client.Connectivity.ServerConnection;
import org.auctionsystem.client.Controller.Scene_Utils;
import org.auctionsystem.client.session.UserSession;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public class Controller_Edit_Item {

    // fx:id khớp Edit_Item.fxml
    @FXML private TextField  field_name;
    @FXML private TextField  field_description;
    @FXML private TextField  field_price;
    @FXML private DatePicker startDatePicker;
    @FXML private ComboBox<String> startHour;
    @FXML private ComboBox<String> startMinute;
    @FXML private DatePicker endDatePicker;
    @FXML private ComboBox<String> endHour;
    @FXML private ComboBox<String> endMinute;
    @FXML private ImageView  productImageView;
    @FXML private Button     btn_save;
    @FXML private Button     btn_cancel;
    @FXML private Button     btn_change_image;
    @FXML private Label      lbl_error;

    // Item được truyền từ Controller_My_Items
    private static JsonObject currentItem;
    private static boolean relistMode = false;

    public static void setRelistMode(boolean mode) { relistMode = mode; }

    private File   selectedImageFile;
    private String imageExtension;

    private static final DateTimeFormatter FMT        = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT   = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String MY_ITEMS_VIEW = "/org/auctionsystem/client/View/My_Items.fxml";

    // ─────────────────────────────────────────────────────────────────────────
    //  Static setter — Controller_My_Items gọi trước khi chuyển scene
    // ─────────────────────────────────────────────────────────────────────────

    public static void setCurrentItem(JsonObject item) {
        currentItem = item;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Khởi tạo
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Điền giờ/phút vào ComboBox
        for (int i = 0; i < 24; i++) {
            startHour.getItems().add(String.format("%02d", i));
            endHour.getItems().add(String.format("%02d", i));
        }
        for (int i = 0; i < 60; i++) {
            startMinute.getItems().add(String.format("%02d", i));
            endMinute.getItems().add(String.format("%02d", i));
        }

        // Bind nút (FXML chưa có onAction)
        if (btn_save   != null) btn_save  .setOnAction(this::onSave);
        if (btn_cancel != null) btn_cancel.setOnAction(this::onCancel);

        // Điền dữ liệu item hiện tại
        if (currentItem != null) prefillFields();

        // Relist mode: khóa tên sản phẩm, đổi tiêu đề nút lưu
        if (relistMode) {
            if (field_name  != null) field_name.setDisable(true);
            if (btn_save    != null) btn_save.setText("Đăng bán lại");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Pre-fill dữ liệu từ item
    // ─────────────────────────────────────────────────────────────────────────

    private void prefillFields() {
        field_name       .setText(getString(currentItem, "name"));
        field_description.setText(getString(currentItem, "description"));

        if (currentItem.has("startingPrice") && !currentItem.get("startingPrice").isJsonNull())
            field_price.setText(String.valueOf((long) currentItem.get("startingPrice").getAsDouble()));

        // startTime
        parseAndSetDateTime(getString(currentItem, "startTime"), startDatePicker, startHour, startMinute);
        // endTime
        parseAndSetDateTime(getString(currentItem, "endTime"),   endDatePicker,   endHour,   endMinute);

        // Ảnh hiện tại từ server
        String imageUrl = getString(currentItem, "imageUrl");
        if (!imageUrl.isBlank() && productImageView != null) {
            loadImageFromServer(imageUrl);
        }
    }

    private void parseAndSetDateTime(String raw, DatePicker dp, ComboBox<String> hour, ComboBox<String> minute) {
        if (raw == null || raw.isBlank()) return;
        try {
            LocalDateTime dt = LocalDateTime.parse(raw.replace("T", " "), FMT);
            if (dp     != null) dp.setValue(dt.toLocalDate());
            if (hour   != null) hour  .getSelectionModel().select(String.format("%02d", dt.getHour()));
            if (minute != null) minute.getSelectionModel().select(String.format("%02d", dt.getMinute()));
        } catch (Exception e) {
            System.err.println("[EditItem] Không parse được datetime: " + raw);
        }
    }

    private void loadImageFromServer(String imageUrl) {
        new Thread(() -> {
            JsonObject req = new JsonObject();
            req.addProperty("action",    "GET_IMAGE");
            req.addProperty("image_url", imageUrl);
            JsonObject res = ServerConnection.sendAuthRequest(req);
            if (res != null && "success".equals(res.get("status").getAsString())
                    && res.has("image_data")) {
                try {
                    byte[] bytes = Base64.getDecoder().decode(res.get("image_data").getAsString());
                    Image img = new Image(new java.io.ByteArrayInputStream(bytes));
                    Platform.runLater(() -> productImageView.setImage(img));
                } catch (Exception e) {
                    System.err.println("[EditItem] Lỗi decode ảnh: " + e.getMessage());
                }
            }
        }, "EditItem-LoadImage").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Chọn ảnh mới
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void onPickImage() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Chọn ảnh mới");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Ảnh", "*.jpg", "*.jpeg", "*.png", "*.webp"));
        File file = fc.showOpenDialog(productImageView.getScene().getWindow());
        if (file == null) return;

        selectedImageFile = file;
        String name = file.getName();
        imageExtension = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
        try {
            productImageView.setImage(new Image(file.toURI().toString()));
        } catch (Exception e) {
            System.err.println("[EditItem] Không load được preview: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Lưu — UPDATE_ITEM
    // ─────────────────────────────────────────────────────────────────────────

    public void onSave(ActionEvent event) {
        if (lbl_error != null) lbl_error.setText("");

        String name        = field_name       .getText().trim();
        String description = field_description.getText().trim();
        String priceStr    = field_price      .getText().trim();

        if (name.isEmpty() || description.isEmpty() || priceStr.isEmpty()) {
            setError("Vui lòng điền đầy đủ tên, mô tả và giá.");
            return;
        }

        double price;
        try {
            price = Double.parseDouble(priceStr);
            if (price <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            setError("Giá khởi điểm không hợp lệ.");
            return;
        }

        String startTime = buildDateTime(startDatePicker, startHour, startMinute);
        String endTime   = buildDateTime(endDatePicker,   endHour,   endMinute);

        if (startTime == null || endTime == null) {
            setError("Vui lòng chọn đầy đủ ngày, giờ, phút.");
            return;
        }

        LocalDateTime start = LocalDateTime.parse(startTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        LocalDateTime end   = LocalDateTime.parse(endTime,   DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        if (!start.isBefore(end)) {
            setError("Thời gian kết thúc phải sau thời gian bắt đầu.");
            return;
        }
        if (end.isBefore(LocalDateTime.now())) {
            setError("Thời gian kết thúc phải trong tương lai.");
            return;
        }

        if (btn_save != null) btn_save.setDisable(true);

        final double finalPrice = price;
        new Thread(() -> {
            JsonObject req = new JsonObject();
            if (relistMode) {
                req.addProperty("action",          "RESTART_AUCTION");
                req.addProperty("item_id",         getString(currentItem, "id"));
                req.addProperty("owner_id",        UserSession.getInstance().getUserId());
                req.addProperty("starting_price",  finalPrice);
                req.addProperty("start_time",      startTime);
                req.addProperty("end_time",        endTime);

                if (selectedImageFile != null) {
                    try {
                        byte[] bytes = Files.readAllBytes(selectedImageFile.toPath());
                        req.addProperty("image_data", Base64.getEncoder().encodeToString(bytes));
                        req.addProperty("extension",  imageExtension);
                    } catch (IOException e) {
                        System.err.println("[EditItem] Lỗi đọc file ảnh: " + e.getMessage());
                    }
                }
            } else {
                req.addProperty("action",          "UPDATE_ITEM");
                req.addProperty("item_id",         getString(currentItem, "id"));
                req.addProperty("seller_id",       UserSession.getInstance().getUserId());
                req.addProperty("name",            name);
                req.addProperty("description",     description);
                req.addProperty("starting_price",  finalPrice);
                req.addProperty("start_time",      startTime);
                req.addProperty("end_time",        endTime);

                if (selectedImageFile != null) {
                    try {
                        byte[] bytes = Files.readAllBytes(selectedImageFile.toPath());
                        req.addProperty("image_data", Base64.getEncoder().encodeToString(bytes));
                        req.addProperty("extension",  imageExtension);
                    } catch (IOException e) {
                        System.err.println("[EditItem] Lỗi đọc file ảnh: " + e.getMessage());
                    }
                }
            }

            JsonObject res = ServerConnection.sendAuthRequest(req);
            Platform.runLater(() -> {
                if (btn_save != null) btn_save.setDisable(false);
                if (res != null && "success".equals(res.get("status").getAsString())) {
                    String msg = relistMode ? "Đăng bán lại thành công!" : "Cập nhật sản phẩm thành công!";
                    // Cập nhật sellerUsername trong currentItem để Item_Detail hiện đúng
                    if (relistMode && res.has("seller_username") && currentItem != null) {
                        currentItem.addProperty("sellerUsername", res.get("seller_username").getAsString());
                        currentItem.addProperty("status", "PENDING");
                    }
                    showAlert(Alert.AlertType.INFORMATION, "Thành công", msg);
                    relistMode = false;
                    goToMyItems(event);
                } else {
                    String msg = (res != null && res.has("message"))
                            ? res.get("message").getAsString() : "Lỗi kết nối server.";
                    setError(msg);
                }
            });
        }, "EditItem-Save").start();
    }

    public void onCancel(ActionEvent event) {
        relistMode = false;
        goToMyItems(event);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Điều hướng
    // ─────────────────────────────────────────────────────────────────────────

    private void goToMyItems(ActionEvent event) {
        currentItem = null;
        try {
            Scene_Utils.Change_Scene(event, MY_ITEMS_VIEW);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String buildDateTime(DatePicker dp, ComboBox<String> hour, ComboBox<String> minute) {
        if (dp == null || dp.getValue() == null) return null;
        String h = hour   != null ? hour.getValue()   : null;
        String m = minute != null ? minute.getValue() : null;
        if (h == null || m == null) return null;
        return dp.getValue().format(DateTimeFormatter.ISO_LOCAL_DATE) + " " + h + ":" + m + ":00";
    }

    private static String getString(JsonObject obj, String key) {
        return obj != null && obj.has(key) && !obj.get(key).isJsonNull()
                ? obj.get(key).getAsString() : "";
    }

    private void setError(String msg) {
        if (lbl_error != null) lbl_error.setText(msg);
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert a = new Alert(type);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(content);
        a.showAndWait();
    }
}