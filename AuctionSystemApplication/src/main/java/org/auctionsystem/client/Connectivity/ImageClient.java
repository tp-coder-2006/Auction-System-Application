package org.auctionsystem.client.Connectivity;

import com.google.gson.JsonObject;
import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * ImageClient — Tiện ích phía client để trao đổi hình ảnh với server.
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  Luồng UPLOAD (client → server):                                         │
 * │    1. Người dùng chọn file ảnh (FileChooser).                            │
 * │    2. ImageClient.uploadAvatar(file, userId) được gọi.                   │
 * │    3. Client đọc bytes từ file → encode Base64 → nhét vào JSON.          │
 * │    4. Gửi JSON tới server qua ServerConnection.sendAuthRequest().         │
 * │    5. Server decode Base64 → ghi file → trả về avatar_url.               │
 * │    6. Client nhận avatar_url → lưu vào UserSession (không lưu bytes).    │
 * │                                                                           │
 * │  Luồng DOWNLOAD (server → client):                                        │
 * │    1. Controller cần hiển thị ảnh, gọi ImageClient.fetchImage(url).      │
 * │    2. Client gửi GET_IMAGE với image_url.                                 │
 * │    3. Server đọc file → encode Base64 → gửi trong JSON.                  │
 * │    4. Client nhận → decode Base64 → tạo javafx.scene.image.Image.        │
 * │    5. Controller đưa Image vào ImageView để hiển thị.                    │
 * │                                                                           │
 * │  Nguyên tắc cốt lõi:                                                     │
 * │    - Session KHÔNG lưu bytes ảnh — chỉ lưu đường dẫn (avatar_url).      │
 * │    - Mỗi lần cần hiển thị: gọi fetchImage() → nhận bytes mới.           │
 * │    - Có thể thêm cache in-memory (Map<url, Image>) nếu cần tối ưu.       │
 * └──────────────────────────────────────────────────────────────────────────┘
 */
public class ImageClient {

    // Giới hạn kích thước file đọc: 5 MB
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L;

    // ─────────────────────────────────────────────────────────────────────────
    //  UPLOAD AVATAR
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Upload ảnh avatar cho người dùng.
     *
     * @param imageFile  File ảnh người dùng đã chọn (từ FileChooser)
     * @param userId     ID của người dùng đang cập nhật
     * @return           JsonObject phản hồi từ server;
     *                   nếu success → có trường "avatar_url"
     *
     * Ví dụ gọi trong Controller:
     * <pre>
     *   FileChooser chooser = new FileChooser();
     *   chooser.getExtensionFilters().add(
     *       new FileChooser.ExtensionFilter("Ảnh", "*.jpg", "*.jpeg", "*.png", "*.webp"));
     *   File file = chooser.showOpenDialog(stage);
     *   if (file != null) {
     *       JsonObject result = ImageClient.uploadAvatar(file, UserSession.getInstance().getUserId());
     *       if ("success".equals(result.get("status").getAsString())) {
     *           String newAvatarUrl = result.get("avatar_url").getAsString();
     *           // Cập nhật avatarUrl vào profile qua UPDATE_PROFILE
     *       }
     *   }
     * </pre>
     */
    public static JsonObject uploadAvatar(File imageFile, String userId) {
        try {
            validateFile(imageFile);

            String extension = getExtension(imageFile.getName());
            String base64Data = encodeFileToBase64(imageFile);

            JsonObject request = new JsonObject();
            request.addProperty("action",     "UPLOAD_AVATAR");
            request.addProperty("user_id",    userId);
            request.addProperty("image_data", base64Data);
            request.addProperty("extension",  extension);

            return ServerConnection.sendAuthRequest(request);

        } catch (IllegalArgumentException e) {
            return clientError(e.getMessage());
        } catch (IOException e) {
            return clientError("Không thể đọc file ảnh: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UPLOAD ITEM IMAGE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Upload ảnh sản phẩm đấu giá.
     *
     * @param imageFile  File ảnh sản phẩm
     * @return           JsonObject phản hồi;
     *                   nếu success → có trường "image_url" để lưu vào Item
     *
     * Ví dụ gọi trong Controller_Add_Item hoặc Controller_Edit_Item:
     * <pre>
     *   JsonObject imgResult = ImageClient.uploadItemImage(file);
     *   if ("success".equals(imgResult.get("status").getAsString())) {
     *       String imageUrl = imgResult.get("image_url").getAsString();
     *       // Truyền imageUrl vào request ADD_ITEM / UPDATE_ITEM
     *   }
     * </pre>
     */
    public static JsonObject uploadItemImage(File imageFile) {
        try {
            validateFile(imageFile);

            String extension  = getExtension(imageFile.getName());
            String base64Data = encodeFileToBase64(imageFile);

            JsonObject request = new JsonObject();
            request.addProperty("action",     "UPLOAD_ITEM_IMAGE");
            request.addProperty("image_data", base64Data);
            request.addProperty("extension",  extension);

            return ServerConnection.sendAuthRequest(request);

        } catch (IllegalArgumentException e) {
            return clientError(e.getMessage());
        } catch (IOException e) {
            return clientError("Không thể đọc file ảnh: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  FETCH IMAGE (download và giải mã)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tải ảnh từ server và giải mã thành {@link Image} JavaFX.
     *
     * @param imageUrl  Đường dẫn tương đối trả về từ server
     *                  (ví dụ: "avatars/abc.jpg", "items/xyz.png")
     * @return          {@link Image} sẵn sàng để đặt vào ImageView,
     *                  hoặc null nếu lỗi
     *
     * Ví dụ gọi trong Controller:
     * <pre>
     *   String avatarUrl = UserSession.getInstance().getAvatarUrl();
     *   if (avatarUrl != null) {
     *       Image img = ImageClient.fetchImage(avatarUrl);
     *       if (img != null) avatarImageView.setImage(img);
     *   }
     * </pre>
     */
    public static Image fetchImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;

        try {
            JsonObject request = new JsonObject();
            request.addProperty("action",    "GET_IMAGE");
            request.addProperty("image_url", imageUrl);

            JsonObject response = ServerConnection.sendAuthRequest(request);
            if (response == null) return null;

            String status = response.has("status") ? response.get("status").getAsString() : "";
            if (!"success".equals(status)) {
                System.err.println("[ImageClient] fetchImage lỗi: "
                        + (response.has("message") ? response.get("message").getAsString() : "unknown"));
                return null;
            }

            String base64Data = response.get("image_data").getAsString();
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);

            return new Image(new ByteArrayInputStream(imageBytes));

        } catch (Exception e) {
            System.err.println("[ImageClient] fetchImage exception: " + e.getMessage());
            return null;
        }
    }

    /**
     * Tải ảnh từ server, trả về bytes thô (dùng khi cần lưu cache hoặc xử lý tiếp).
     *
     * @param imageUrl  Đường dẫn tương đối
     * @return          Mảng byte ảnh, hoặc null nếu lỗi
     */
    public static byte[] fetchImageBytes(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;

        try {
            JsonObject request = new JsonObject();
            request.addProperty("action",    "GET_IMAGE");
            request.addProperty("image_url", imageUrl);

            JsonObject response = ServerConnection.sendAuthRequest(request);
            if (response == null) return null;

            String status = response.has("status") ? response.get("status").getAsString() : "";
            if (!"success".equals(status)) return null;

            String base64Data = response.get("image_data").getAsString();
            return Base64.getDecoder().decode(base64Data);

        } catch (Exception e) {
            System.err.println("[ImageClient] fetchImageBytes exception: " + e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Đọc toàn bộ nội dung file, encode thành chuỗi Base64.
     *
     * @param file  File cần encode
     * @return      Chuỗi Base64 (không kèm data URI prefix)
     */
    private static String encodeFileToBase64(File file) throws IOException {
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            int totalRead = 0;
            while (totalRead < bytes.length) {
                int read = fis.read(bytes, totalRead, bytes.length - totalRead);
                if (read == -1) break;
                totalRead += read;
            }
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Kiểm tra file hợp lệ trước khi upload.
     * Ném {@link IllegalArgumentException} nếu không hợp lệ.
     */
    private static void validateFile(File file) {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("File không tồn tại!");
        }
        if (!file.isFile()) {
            throw new IllegalArgumentException("Đường dẫn không phải là file!");
        }
        if (file.length() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File quá lớn! Tối đa 5MB.");
        }
        String ext = getExtension(file.getName()).toLowerCase();
        if (!ext.equals("jpg") && !ext.equals("jpeg")
                && !ext.equals("png") && !ext.equals("webp")) {
            throw new IllegalArgumentException(
                    "Định dạng không hỗ trợ: " + ext + ". Chỉ chấp nhận: jpg, jpeg, png, webp");
        }
    }

    /** Lấy extension từ tên file (chữ thường). */
    private static String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) return "jpg";
        return filename.substring(dotIndex + 1).toLowerCase();
    }

    /** Tạo JsonObject lỗi phía client (không gửi server). */
    private static JsonObject clientError(String message) {
        JsonObject err = new JsonObject();
        err.addProperty("status",  "error");
        err.addProperty("message", message);
        return err;
    }
}