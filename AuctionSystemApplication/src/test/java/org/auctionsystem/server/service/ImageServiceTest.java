package org.auctionsystem.server.service;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho ImageService (các hàm static / pure logic).
 *
 * Không cần DB hay file system thật cho phần lớn test.
 *
 * [SỬA] Thay thế các test kích thước file chỉ test phép toán
 *       bằng test gọi ItemService thật với byte array > 5MB.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ImageServiceTest {

    // ═══════════════════════════════════════════════════════
    // 1. isAllowedExtension
    // ═══════════════════════════════════════════════════════

    @ParameterizedTest @Order(1)
    @ValueSource(strings = {"jpg", "jpeg", "png", "webp"})
    void isAllowedExtension_validExtensions_returnsTrue(String ext) {
        assertTrue(ImageService.isAllowedExtension(ext),
                ext + " phải được chấp nhận");
    }

    @ParameterizedTest @Order(2)
    @ValueSource(strings = {"gif", "bmp", "tiff", "svg", "exe", "pdf", "", "JPG", "PNG"})
    void isAllowedExtension_invalidExtensions_returnsFalse(String ext) {
        assertFalse(ImageService.isAllowedExtension(ext),
                ext + " không được phép");
    }

    // ═══════════════════════════════════════════════════════
    // 2. decodeBase64
    // ═══════════════════════════════════════════════════════

    @Test @Order(10)
    void decodeBase64_validBase64_returnsBytes() {
        String data = Base64.getEncoder().encodeToString("Hello World".getBytes());
        byte[] result = ImageService.decodeBase64(data);
        assertNotNull(result);
        assertArrayEquals("Hello World".getBytes(), result);
    }

    @Test @Order(11)
    void decodeBase64_emptyString_returnsEmptyOrNull() {
        byte[] result = ImageService.decodeBase64("");
        assertTrue(result == null || result.length == 0);
    }

    @Test @Order(12)
    void decodeBase64_nullInput_returnsNull() {
        byte[] result = ImageService.decodeBase64(null);
        assertNull(result);
    }

    @Test @Order(13)
    void decodeBase64_invalidBase64_returnsNull() {
        byte[] result = ImageService.decodeBase64("NOT_VALID_BASE64!!@#$");
        assertNull(result);
    }

    @Test @Order(14)
    void decodeBase64_validImageLikeData_returnsCorrectSize() {
        byte[] fakeImageBytes = new byte[100];
        for (int i = 0; i < fakeImageBytes.length; i++) fakeImageBytes[i] = (byte) i;
        String encoded = Base64.getEncoder().encodeToString(fakeImageBytes);

        byte[] decoded = ImageService.decodeBase64(encoded);
        assertNotNull(decoded);
        assertEquals(100, decoded.length);
    }

    @Test @Order(15)
    void decodeBase64_withDataUriPrefix_stripsPrefix() {
        // Dữ liệu từ browser thường có prefix "data:image/png;base64,"
        String original = "test image content";
        String base64   = Base64.getEncoder().encodeToString(original.getBytes());
        String withPrefix = "data:image/png;base64," + base64;

        byte[] decoded = ImageService.decodeBase64(withPrefix);
        assertNotNull(decoded);
        assertEquals(original, new String(decoded));
    }

    @Test @Order(16)
    void decodeBase64_withPadding_decodesCorrectly() {
        String original = "test";
        String encoded  = Base64.getEncoder().encodeToString(original.getBytes());
        byte[] decoded  = ImageService.decodeBase64(encoded);
        assertNotNull(decoded);
        assertEquals(original, new String(decoded));
    }

    // ═══════════════════════════════════════════════════════
    // 3. File size limit — test qua ItemService thật
    //    (thay thế test phép toán thuần)
    // ═══════════════════════════════════════════════════════

    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private static String futureTime(int plusMinutes) {
        return java.time.LocalDateTime.now().plusMinutes(plusMinutes)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @Test @Order(20)
    void addItem_imageExactly5MB_passedSizeCheck() {
        // 5MB chính xác: điều kiện là imageBytes.length > 5*1024*1024
        // nên 5MB chính xác KHÔNG bị từ chối bởi size check
        byte[] exactly5MB   = new byte[(int) MAX_BYTES];
        String encoded      = Base64.getEncoder().encodeToString(exactly5MB);

        ItemService itemService = new ItemService();
        JsonObject req = new JsonObject();
        req.addProperty("name",           "Size Test Item");
        req.addProperty("description",    "desc");
        req.addProperty("starting_price", 100.0);
        req.addProperty("start_time",     futureTime(10));
        req.addProperty("end_time",       futureTime(60));
        req.addProperty("seller_id",      "seller-001");
        req.addProperty("image_data",     encoded);
        req.addProperty("extension",      "jpg");

        JsonObject res = itemService.addItem(req);
        // Không bị reject bởi size check → lỗi phải là DB (không có DB) bukan "quá lớn"
        assertEquals("error", res.get("status").getAsString());
        assertFalse(res.get("message").getAsString().contains("quá lớn"),
                "5MB chính xác không được bị từ chối bởi size check");
    }

    @Test @Order(21)
    void addItem_imageOver5MB_returnsErrorWithSizeMessage() {
        // 5MB + 1 byte: phải bị từ chối với message chứa "quá lớn"
        byte[] over5MB = new byte[(int) MAX_BYTES + 1];
        String encoded = Base64.getEncoder().encodeToString(over5MB);

        ItemService itemService = new ItemService();
        JsonObject req = new JsonObject();
        req.addProperty("name",           "Oversized Item");
        req.addProperty("description",    "desc");
        req.addProperty("starting_price", 100.0);
        req.addProperty("start_time",     futureTime(10));
        req.addProperty("end_time",       futureTime(60));
        req.addProperty("seller_id",      "seller-001");
        req.addProperty("image_data",     encoded);
        req.addProperty("extension",      "png");

        JsonObject res = itemService.addItem(req);
        assertEquals("error", res.get("status").getAsString());
        assertTrue(res.get("message").getAsString().contains("quá lớn"),
                "Ảnh > 5MB phải trả về message chứa 'quá lớn'");
    }

    @Test @Order(22)
    void addItem_image10MB_returnsErrorWithSizeMessage() {
        // 10MB: rõ ràng vượt giới hạn
        byte[] tenMB   = new byte[10 * 1024 * 1024];
        String encoded = Base64.getEncoder().encodeToString(tenMB);

        ItemService itemService = new ItemService();
        JsonObject req = new JsonObject();
        req.addProperty("name",           "10MB Item");
        req.addProperty("description",    "desc");
        req.addProperty("starting_price", 100.0);
        req.addProperty("start_time",     futureTime(10));
        req.addProperty("end_time",       futureTime(60));
        req.addProperty("seller_id",      "seller-001");
        req.addProperty("image_data",     encoded);
        req.addProperty("extension",      "jpg");

        JsonObject res = itemService.addItem(req);
        assertEquals("error", res.get("status").getAsString());
        assertTrue(res.get("message").getAsString().contains("quá lớn"));
    }

    // ═══════════════════════════════════════════════════════
    // 4. deleteFileQuietly — không throw khi file không tồn tại
    // ═══════════════════════════════════════════════════════

    @Test @Order(30)
    void deleteFileQuietly_nonExistentFile_doesNotThrow() {
        assertDoesNotThrow(() ->
                ImageService.deleteFileQuietly("non/existent/path/file.jpg"));
    }

    @Test @Order(31)
    void deleteFileQuietly_nullPath_doesNotThrow() {
        assertDoesNotThrow(() ->
                ImageService.deleteFileQuietly(null));
    }

    @Test @Order(32)
    void deleteFileQuietly_emptyPath_doesNotThrow() {
        assertDoesNotThrow(() ->
                ImageService.deleteFileQuietly(""));
    }

    // ═══════════════════════════════════════════════════════
    // 5. Extension case sensitivity
    // ═══════════════════════════════════════════════════════

    @Test @Order(40)
    void isAllowedExtension_uppercaseExtension_returnsFalse() {
        assertFalse(ImageService.isAllowedExtension("JPG"));
        assertFalse(ImageService.isAllowedExtension("PNG"));
        assertFalse(ImageService.isAllowedExtension("WEBP"));
        assertFalse(ImageService.isAllowedExtension("JPEG"));
    }

    @Test @Order(41)
    void isAllowedExtension_mixedCase_returnsFalse() {
        assertFalse(ImageService.isAllowedExtension("Jpg"));
        assertFalse(ImageService.isAllowedExtension("pNg"));
        assertFalse(ImageService.isAllowedExtension("WebP"));
    }

    // ═══════════════════════════════════════════════════════
    // 6. getExtension helper
    // ═══════════════════════════════════════════════════════

    @Test @Order(50)
    void getExtension_normalFilename_returnsExtension() {
        assertEquals("jpg",  ImageService.getExtension("avatar.jpg"));
        assertEquals("png",  ImageService.getExtension("photo.png"));
        assertEquals("webp", ImageService.getExtension("img.webp"));
    }

    @Test @Order(51)
    void getExtension_noExtension_returnsDefaultJpg() {
        // Theo code: dot < 0 → trả về "jpg"
        assertEquals("jpg", ImageService.getExtension("filenameWithoutDot"));
    }

    @Test @Order(52)
    void getExtension_endsWithDot_returnsDefaultJpg() {
        // Theo code: dot == filename.length() - 1 → trả về "jpg"
        assertEquals("jpg", ImageService.getExtension("filename."));
    }

    @Test @Order(53)
    void getExtension_multipleDotsInName_returnsLastPart() {
        assertEquals("jpg", ImageService.getExtension("my.avatar.backup.jpg"));
    }
}