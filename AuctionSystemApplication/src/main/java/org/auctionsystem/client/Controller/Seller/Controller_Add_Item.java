package org.auctionsystem.client.Controller.Seller;

import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
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
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Controller_Add_Item {

    // fx:id khớp Add_Item.fxml
    @FXML private TextField  field_name;
    @FXML private TextField  field_description;
    @FXML private TextField  field_price;
    @FXML private DatePicker startDatePicker;
    @FXML private ComboBox<String> startHour;
    @FXML private ComboBox<String> startMinute;
    @FXML private DatePicker endDatePicker;   // chú ý: FXML thiếu fx:id — xem ghi chú
    @FXML private ComboBox<String> endHour;
    @FXML private ComboBox<String> endMinute;
    @FXML private ImageView  productImageView;
    @FXML private Button     btn_submit;
    @FXML private Button     btn_cancel;
    @FXML private Button     btn_change_image;

    private File   selectedImageFile;
    private String imageExtension;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String MY_ITEMS_VIEW  = "/org/auctionsystem/client/View/My_Items.fxml";

    // ─────────────────────────────────────────────────────────────────────────
    //  Khởi tạo
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Điền giờ 00–23, phút 00–59
        for (int i = 0; i < 24; i++)
            startHour.getItems().add(String.format("%02d", i));
        for (int i = 0; i < 60; i++) {
            startMinute.getItems().add(String.format("%02d", i));
            endMinute.getItems().add(String.format("%02d", i));
        }
        for (int i = 0; i < 24; i++)
            endHour.getItems().add(String.format("%02d", i));

        startHour.getSelectionModel().select("09");
        startMinute.getSelectionModel().select("00");
        endHour.getSelectionModel().select("21");
        endMinute.getSelectionModel().select("00");
        startDatePicker.setValue(LocalDate.now().plusDays(1));
        if (endDatePicker != null)
            endDatePicker.setValue(LocalDate.now().plusDays(8));

        // Bind nút (FXML chưa có onAction)
        if (btn_submit != null) btn_submit.setOnAction(this::onSubmit);
        if (btn_cancel != null) btn_cancel.setOnAction(this::onCancel);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Chọn ảnh
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void onPickImage() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Chọn ảnh sản phẩm");
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
            System.err.println("[AddItem] Không load được ảnh preview: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Đăng sản phẩm — ADD_ITEM
    // ─────────────────────────────────────────────────────────────────────────

    public void onSubmit(ActionEvent event) {
        String name        = field_name.getText().trim();
        String description = field_description.getText().trim();
        String priceStr    = field_price.getText().trim();

        if (name.isEmpty() || description.isEmpty() || priceStr.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Thiếu thông tin", "Vui lòng điền đầy đủ tên, mô tả và giá.");
            return;
        }

        double price;
        try {
            price = Double.parseDouble(priceStr);
            if (price <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Giá khởi điểm không hợp lệ.");
            return;
        }

        String startTime = buildDateTime(startDatePicker, startHour, startMinute);
        String endTime   = buildDateTime(endDatePicker,   endHour,   endMinute);

        if (startTime == null || endTime == null) {
            showAlert(Alert.AlertType.WARNING, "Thiếu thông tin", "Vui lòng chọn đầy đủ ngày, giờ, phút.");
            return;
        }

        LocalDateTime start = LocalDateTime.parse(startTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        LocalDateTime end   = LocalDateTime.parse(endTime,   DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        if (!start.isBefore(end)) {
            showAlert(Alert.AlertType.WARNING, "Thời gian không hợp lệ", "Thời gian kết thúc phải sau thời gian bắt đầu.");
            return;
        }
        if (end.isBefore(LocalDateTime.now())) {
            showAlert(Alert.AlertType.WARNING, "Thời gian không hợp lệ", "Thời gian kết thúc phải trong tương lai.");
            return;
        }

        if (btn_submit != null) btn_submit.setDisable(true);

        final double finalPrice = price;
        new Thread(() -> {
            JsonObject req = new JsonObject();
            req.addProperty("action",         "ADD_ITEM");
            req.addProperty("name",           name);
            req.addProperty("description",    description);
            req.addProperty("starting_price", finalPrice);
            req.addProperty("start_time",     startTime);
            req.addProperty("end_time",       endTime);
            req.addProperty("seller_id",      UserSession.getInstance().getUserId());

            // Đính kèm ảnh nếu có
            if (selectedImageFile != null) {
                try {
                    byte[] bytes = Files.readAllBytes(selectedImageFile.toPath());
                    req.addProperty("image_data", Base64.getEncoder().encodeToString(bytes));
                    req.addProperty("extension",  imageExtension);
                } catch (IOException e) {
                    System.err.println("[AddItem] Lỗi đọc file ảnh: " + e.getMessage());
                }
            }

            JsonObject res = ServerConnection.sendAuthRequest(req);
            Platform.runLater(() -> {
                if (btn_submit != null) btn_submit.setDisable(false);
                if (res != null && "success".equals(res.get("status").getAsString())) {
                    showAlert(Alert.AlertType.INFORMATION, "Thành công", "Đăng sản phẩm thành công!");
                    goToMyItems(event);
                } else {
                    String msg = (res != null && res.has("message"))
                            ? res.get("message").getAsString() : "Lỗi kết nối server.";
                    showAlert(Alert.AlertType.ERROR, "Lỗi", msg);
                }
            });
        }, "AddItem-Submit").start();
    }

    public void onCancel(ActionEvent event) {
        goToMyItems(event);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Điều hướng
    // ─────────────────────────────────────────────────────────────────────────

    private void goToMyItems(ActionEvent event) {
        try {
            Scene_Utils.Change_Scene(event, MY_ITEMS_VIEW);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Gom DatePicker + ComboBox giờ/phút thành "yyyy-MM-dd HH:mm:ss" */
    private String buildDateTime(DatePicker dp, ComboBox<String> hour, ComboBox<String> minute) {
        if (dp == null || dp.getValue() == null) return null;
        String h = hour   != null ? hour.getValue()   : null;
        String m = minute != null ? minute.getValue() : null;
        if (h == null || m == null) return null;
        return dp.getValue().format(DateTimeFormatter.ISO_LOCAL_DATE) + " " + h + ":" + m + ":00";
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert a = new Alert(type);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(content);
        a.showAndWait();
    }
}