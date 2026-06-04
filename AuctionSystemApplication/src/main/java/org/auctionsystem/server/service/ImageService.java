package org.auctionsystem.server.service;

import com.google.gson.JsonObject;
import org.auctionsystem.server.DAO.ImageDAO;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.UUID;

/**
 * ImageService — Xử lý upload, lưu trữ và lấy hình ảnh.
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  Bộ 3 phối hợp: ImageService + ImageDAO + disk                          │
 * │                                                                          │
 * │  Thứ tự UPLOAD (đảm bảo nhất quán):                                     │
 * │    1. Tra cứu path cũ qua ImageDAO.getCurrentAvatarPath()               │
 * │    2. Ghi file MỚI lên disk trước                                        │
 * │    3. Đăng ký metadata mới vào DB qua ImageDAO.registerImage()          │
 * │    4. Xóa file CŨ khỏi disk                                              │
 * │    5. Xóa metadata cũ khỏi DB qua ImageDAO.deleteImageRecord()          │
 * │                                                                          │
 * │  Tại sao thứ tự này?                                                     │
 * │    - Nếu ghi file mới thất bại → rollback sạch, không mất ảnh cũ.      │
 * │    - Nếu DB insert thất bại → xóa file mới vừa ghi, ảnh cũ còn nguyên. │
 * │    - Nếu xóa ảnh cũ thất bại → chỉ dư 1 file rác, không mất dữ liệu.  │
 * └──────────────────────────────────────────────────────────────────────────┘
 */
public class ImageService {

    private static final String IMAGE_ROOT = "auction_images";
    private static final long   MAX_IMAGE_BYTES   = 5 * 1024 * 1024L;
    private static final String[] ALLOWED_EXTENSIONS = {"jpg", "jpeg", "png", "webp"};

    private final ImageDAO imageDAO = new ImageDAO();

    public ImageService() {
        createDirectoryIfAbsent(IMAGE_ROOT + "/avatars");
        createDirectoryIfAbsent(IMAGE_ROOT + "/items");
    }


    // ─────────────────────────────────────────────────────────────────────────
    //  GET IMAGE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Đọc file ảnh từ disk, encode base64, trả về.
     *
     * Request: { action, session_id, image_url:"avatars/uuid.jpg" }
     * Response success: { status, image_data (base64), extension, image_url }
     */
    public JsonObject getImage(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            String imageUrl = getRequired(request, "image_url");

            // Bảo vệ path traversal
            if (!imageUrl.startsWith("avatars/") && !imageUrl.startsWith("items/")) {
                return error("Đường dẫn ảnh không hợp lệ!");
            }
            if (imageUrl.contains("..") || imageUrl.contains("\\")) {
                return error("Đường dẫn ảnh không hợp lệ!");
            }

            // Xác minh file tồn tại trong DB (không phục vụ file không được đăng ký)
            if (!imageDAO.existsByFilePath(imageUrl)) {
                return error("Ảnh không tồn tại hoặc chưa được đăng ký: " + imageUrl);
            }

            String absolutePath = IMAGE_ROOT + "/" + imageUrl;
            File   imageFile    = new File(absolutePath);

            if (!imageFile.exists() || !imageFile.isFile()) {
                return error("File ảnh bị thiếu trên server: " + imageUrl);
            }

            byte[] imageBytes = readFile(absolutePath);
            String base64Data = Base64.getEncoder().encodeToString(imageBytes);
            String extension  = getExtension(imageFile.getName());

            response.addProperty("status",     "success");
            response.addProperty("image_data", base64Data);
            response.addProperty("extension",  extension);
            response.addProperty("image_url",  imageUrl);
            return response;

        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        } catch (IOException e) {
            System.err.println("[ImageService.getImage] IO error: " + e.getMessage());
            return error("Lỗi đọc file ảnh!");
        } catch (Exception e) {
            System.err.println("[ImageService.getImage] Lỗi: " + e.getMessage());
            return error("Lỗi hệ thống: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  DỌN DẸP ORPHAN (gọi từ AuctionScheduler định kỳ)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Dọn dẹp file mồ côi: file có metadata trong DB nhưng owner không còn.
     * Nên gọi định kỳ qua AuctionScheduler (ví dụ: mỗi 24 giờ).
     *
     * Ví dụ tích hợp trong AuctionScheduler:
     * <pre>
     *   new ImageService().cleanOrphanImages();
     * </pre>
     */
    public void cleanOrphanImages() {
        // Dọn avatar mồ côi
        for (String path : imageDAO.findOrphanAvatars()) {
            deleteFileQuietly(IMAGE_ROOT + "/" + path);
            imageDAO.deleteImageRecord(path);
            System.out.println("[ImageService] Đã xóa avatar mồ côi: " + path);
        }

        // Dọn item image mồ côi
        for (String path : imageDAO.findOrphanItemImages()) {
            deleteFileQuietly(IMAGE_ROOT + "/" + path);
            imageDAO.deleteImageRecord(path);
            System.out.println("[ImageService] Đã xóa item image mồ côi: " + path);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  STATIC HELPERS — dùng chung cho ImageService, ItemService, UserService
    // ─────────────────────────────────────────────────────────────────────────

    public static byte[] decodeBase64(String base64String) {
        if (base64String == null) return null;
        try {
            String clean = base64String.contains(",")
                    ? base64String.substring(base64String.indexOf(',') + 1)
                    : base64String;
            return Base64.getDecoder().decode(clean);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static void writeFile(String path, byte[] data) throws IOException {
        Path filePath = Paths.get(path);
        Files.createDirectories(filePath.getParent());
        try (FileOutputStream fos = new FileOutputStream(path)) {
            fos.write(data);
        }
    }

    public static byte[] readFile(String path) throws IOException {
        return Files.readAllBytes(Paths.get(path));
    }

    /** Xóa file, im lặng nếu thất bại (chỉ log, không ném exception). */
    public static void deleteFileQuietly(String path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException e) {
            System.err.println("[ImageService] Không thể xóa file: " + path + " — " + e.getMessage());
        }
    }

    public static String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot < 0 || dot == filename.length() - 1) ? "jpg"
                : filename.substring(dot + 1).toLowerCase();
    }

    public static boolean isAllowedExtension(String ext) {
        for (String a : ALLOWED_EXTENSIONS) if (a.equals(ext)) return true;
        return false;
    }

    private void createDirectoryIfAbsent(String path) {
        File dir = new File(path);
        if (!dir.exists() && dir.mkdirs()) {
            System.out.println("[ImageService] Đã tạo thư mục: " + dir.getAbsolutePath());
        }
    }

    private String getRequired(JsonObject req, String field) {
        if (!req.has(field) || req.get(field).isJsonNull()
                || req.get(field).getAsString().isBlank()) {
            throw new IllegalArgumentException("Thiếu trường: " + field);
        }
        return req.get(field).getAsString();
    }

    private JsonObject error(String message) {
        JsonObject err = new JsonObject();
        err.addProperty("status",  "error");
        err.addProperty("message", message);
        return err;
    }
}