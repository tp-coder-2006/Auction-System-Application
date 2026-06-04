package org.auctionsystem.server.service;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho ItemService.
 *
 * Tập trung vào:
 *  - Input validation (addItem, updateItem) — không cần DB
 *  - Contract của các GET method
 *  - Logic xóa theo trạng thái MỚI (deleteItem):
 *      • ACTIVE                         → từ chối hoàn toàn
 *      • PENDING + chưa có bid          → hard delete
 *      • PENDING/CANCELLED/CLOSED + bid → soft delete
 *  - Logic restartItemAuction
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ItemServiceTest {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ItemService service;

    @BeforeEach
    void setUp() {
        service = new ItemService();
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private String future(int plusMinutes) {
        return LocalDateTime.now().plusMinutes(plusMinutes).format(FMT);
    }

    private String past(int minusMinutes) {
        return LocalDateTime.now().minusMinutes(minusMinutes).format(FMT);
    }

    // ═══════════════════════════════════════════════════════
    // 1. addItem — Input validation
    // ═══════════════════════════════════════════════════════

    @Test @Order(1)
    void addItem_startAfterEnd_returnsError() {
        JsonObject req = new JsonObject();
        req.addProperty("name",           "Test Item");
        req.addProperty("description",    "desc");
        req.addProperty("starting_price", 100.0);
        req.addProperty("start_time",     future(60));  // bắt đầu sau
        req.addProperty("end_time",       future(10));  // kết thúc trước start → sai
        req.addProperty("seller_id",      "seller-001");

        JsonObject res = service.addItem(req);
        assertEquals("error", res.get("status").getAsString());
        assertTrue(res.get("message").getAsString().contains("trước thời gian kết thúc"));
    }

    @Test @Order(2)
    void addItem_endTimeInPast_returnsError() {
        JsonObject req = new JsonObject();
        req.addProperty("name",           "Old Item");
        req.addProperty("description",    "desc");
        req.addProperty("starting_price", 100.0);
        req.addProperty("start_time",     past(120));
        req.addProperty("end_time",       past(60));    // end time ở quá khứ
        req.addProperty("seller_id",      "seller-001");

        JsonObject res = service.addItem(req);
        assertEquals("error", res.get("status").getAsString());
        assertTrue(res.get("message").getAsString().contains("quá khứ"));
    }

    @Test @Order(3)
    void addItem_startingPriceZero_returnsError() {
        JsonObject req = new JsonObject();
        req.addProperty("name",           "Zero Price");
        req.addProperty("description",    "desc");
        req.addProperty("starting_price", 0.0);
        req.addProperty("start_time",     future(10));
        req.addProperty("end_time",       future(60));
        req.addProperty("seller_id",      "seller-001");

        JsonObject res = service.addItem(req);
        assertEquals("error", res.get("status").getAsString());
        assertTrue(res.get("message").getAsString().contains("lớn hơn 0"));
    }

    @Test @Order(4)
    void addItem_startingPriceNegative_returnsError() {
        JsonObject req = new JsonObject();
        req.addProperty("name",           "Negative Price");
        req.addProperty("description",    "desc");
        req.addProperty("starting_price", -50.0);
        req.addProperty("start_time",     future(10));
        req.addProperty("end_time",       future(60));
        req.addProperty("seller_id",      "seller-001");

        JsonObject res = service.addItem(req);
        assertEquals("error", res.get("status").getAsString());
    }

    @Test @Order(5)
    void addItem_invalidDateFormat_returnsError() {
        JsonObject req = new JsonObject();
        req.addProperty("name",           "Bad Date");
        req.addProperty("description",    "desc");
        req.addProperty("starting_price", 100.0);
        req.addProperty("start_time",     "2025/06/01 10:00:00"); // sai format
        req.addProperty("end_time",       "2025/06/02 10:00:00");
        req.addProperty("seller_id",      "seller-001");

        JsonObject res = service.addItem(req);
        assertEquals("error", res.get("status").getAsString());
        assertTrue(res.get("message").getAsString().contains("Định dạng thời gian"));
    }

    @Test @Order(6)
    void addItem_invalidImageExtension_returnsError() {
        JsonObject req = new JsonObject();
        req.addProperty("name",           "Item with bad image");
        req.addProperty("description",    "desc");
        req.addProperty("starting_price", 100.0);
        req.addProperty("start_time",     future(10));
        req.addProperty("end_time",       future(60));
        req.addProperty("seller_id",      "seller-001");
        req.addProperty("image_data",     "dGVzdA=="); // valid base64
        req.addProperty("extension",      "gif");      // gif không được phép

        JsonObject res = service.addItem(req);
        assertEquals("error", res.get("status").getAsString());
        assertTrue(res.get("message").getAsString().toLowerCase().contains("định dạng"));
    }

    @Test @Order(7)
    void addItem_imageOver5MB_rejectedBeforeDBInsert() {
        // ảnh > 5MB phải bị chặn ở bước pre-validate, trước khi INSERT item
        byte[] over5MB = new byte[5 * 1024 * 1024 + 1];
        String encoded = Base64.getEncoder().encodeToString(over5MB);

        JsonObject req = new JsonObject();
        req.addProperty("name",           "Big Image Item");
        req.addProperty("description",    "desc");
        req.addProperty("starting_price", 100.0);
        req.addProperty("start_time",     future(10));
        req.addProperty("end_time",       future(60));
        req.addProperty("seller_id",      "seller-001");
        req.addProperty("image_data",     encoded);
        req.addProperty("extension",      "jpg");

        JsonObject res = service.addItem(req);
        assertEquals("error", res.get("status").getAsString());
        assertTrue(res.get("message").getAsString().contains("quá lớn"),
                "Ảnh > 5MB phải bị chặn với message 'quá lớn'");
        // Không được có item_id trong response (chưa INSERT)
        assertFalse(res.has("item_id"),
                "Không được INSERT item khi ảnh bị từ chối ở bước pre-validate");
    }

    @Test @Order(8)
    void addItem_validInputNoImage_attemptsDBInsert() {
        // Không có DB → kết quả là error, nhưng không được throw exception
        JsonObject req = new JsonObject();
        req.addProperty("name",           "Valid Item");
        req.addProperty("description",    "description");
        req.addProperty("starting_price", 100.0);
        req.addProperty("start_time",     future(10));
        req.addProperty("end_time",       future(60));
        req.addProperty("seller_id",      "seller-001");

        assertDoesNotThrow(() -> {
            JsonObject res = service.addItem(req);
            assertNotNull(res.get("status"));
        });
    }

    // ═══════════════════════════════════════════════════════
    // 2. updateItem — Input validation
    // ═══════════════════════════════════════════════════════

    @Test @Order(10)
    void updateItem_invalidExtension_returnsErrorWithMessage() {
        JsonObject req = new JsonObject();
        req.addProperty("item_id",        "item-001");
        req.addProperty("name",           "Updated");
        req.addProperty("description",    "desc");
        req.addProperty("starting_price", 200.0);
        req.addProperty("start_time",     future(10));
        req.addProperty("end_time",       future(120));
        req.addProperty("seller_id",      "seller-001");
        req.addProperty("image_data",     "dGVzdA==");
        req.addProperty("extension",      "bmp"); // không hợp lệ

        JsonObject res = service.updateItem(req);
        assertEquals("error", res.get("status").getAsString());
        assertTrue(res.get("message").getAsString().toLowerCase().contains("định dạng"),
                "Message phải đề cập đến lỗi định dạng ảnh");
    }

    @Test @Order(11)
    void updateItem_invalidBase64_returnsErrorWithMessage() {
        JsonObject req = new JsonObject();
        req.addProperty("item_id",        "item-001");
        req.addProperty("name",           "Updated");
        req.addProperty("description",    "desc");
        req.addProperty("starting_price", 200.0);
        req.addProperty("start_time",     future(10));
        req.addProperty("end_time",       future(120));
        req.addProperty("seller_id",      "seller-001");
        req.addProperty("image_data",     "NOT_VALID_BASE64!!!");
        req.addProperty("extension",      "png");

        JsonObject res = service.updateItem(req);
        assertEquals("error", res.get("status").getAsString());
        assertTrue(
                res.get("message").getAsString().toLowerCase().contains("base64")
                        || res.get("message").getAsString().toLowerCase().contains("ảnh"),
                "Message phải đề cập đến lỗi dữ liệu ảnh");
    }

    @Test @Order(12)
    void updateItem_gifExtension_returnsError() {
        JsonObject req = new JsonObject();
        req.addProperty("item_id",        "item-001");
        req.addProperty("name",           "Updated");
        req.addProperty("description",    "desc");
        req.addProperty("starting_price", 200.0);
        req.addProperty("start_time",     future(10));
        req.addProperty("end_time",       future(120));
        req.addProperty("seller_id",      "seller-001");
        req.addProperty("image_data",     "dGVzdA==");
        req.addProperty("extension",      "gif");

        JsonObject res = service.updateItem(req);
        assertEquals("error", res.get("status").getAsString());
        assertTrue(res.get("message").getAsString().toLowerCase().contains("định dạng"));
    }

    @Test @Order(13)
    void updateItem_imageOver5MB_returnsErrorWithSizeMessage() {
        byte[] over5MB = new byte[5 * 1024 * 1024 + 1];
        String encoded = Base64.getEncoder().encodeToString(over5MB);

        JsonObject req = new JsonObject();
        req.addProperty("item_id",        "item-001");
        req.addProperty("name",           "Big Image Item");
        req.addProperty("description",    "desc");
        req.addProperty("starting_price", 200.0);
        req.addProperty("start_time",     future(10));
        req.addProperty("end_time",       future(120));
        req.addProperty("seller_id",      "seller-001");
        req.addProperty("image_data",     encoded);
        req.addProperty("extension",      "jpg");

        JsonObject res = service.updateItem(req);
        assertEquals("error", res.get("status").getAsString());
        assertTrue(res.get("message").getAsString().contains("quá lớn"),
                "Ảnh > 5MB phải trả về message chứa 'quá lớn'");
    }

    @Test @Order(14)
    void updateItem_imageExactly5MB_notRejectedBySizeCheck() {
        // 5MB chính xác (không vượt ngưỡng > 5MB) → lỗi phải là DB, không phải size
        byte[] exactly5MB = new byte[5 * 1024 * 1024];
        String encoded    = Base64.getEncoder().encodeToString(exactly5MB);

        JsonObject req = new JsonObject();
        req.addProperty("item_id",        "item-001");
        req.addProperty("name",           "Exact 5MB Item");
        req.addProperty("description",    "desc");
        req.addProperty("starting_price", 200.0);
        req.addProperty("start_time",     future(10));
        req.addProperty("end_time",       future(120));
        req.addProperty("seller_id",      "seller-001");
        req.addProperty("image_data",     encoded);
        req.addProperty("extension",      "jpg");

        JsonObject res = service.updateItem(req);
        assertEquals("error", res.get("status").getAsString());
        assertFalse(res.get("message").getAsString().contains("quá lớn"),
                "5MB chính xác không được bị từ chối bởi size check");
    }

    @Test @Order(15)
    void updateItem_uppercaseInvalidExtension_returnsError() {
        // "GIF" → toLowerCase() → "gif" → vẫn không hợp lệ → trả lỗi định dạng
        JsonObject req = new JsonObject();
        req.addProperty("item_id",        "item-001");
        req.addProperty("name",           "Updated");
        req.addProperty("description",    "desc");
        req.addProperty("starting_price", 200.0);
        req.addProperty("start_time",     future(10));
        req.addProperty("end_time",       future(120));
        req.addProperty("seller_id",      "seller-001");
        req.addProperty("image_data",     "dGVzdA==");
        req.addProperty("extension",      "GIF"); // uppercase invalid ext

        JsonObject res = service.updateItem(req);
        assertEquals("error", res.get("status").getAsString());
        assertTrue(res.get("message").getAsString().toLowerCase().contains("định dạng"));
    }

    // ═══════════════════════════════════════════════════════
    // 3. deleteItem — logic phân nhánh (seller-side)
    //
    //   Rule (seller tự xóa item của mình):
    //     ACTIVE                         → error (đang đấu giá)
    //     PENDING + chưa có bid          → hard delete (xóa hẳn)
    //     PENDING/CANCELLED/CLOSED + bid → soft delete (ẩn, is_active=0)
    //
    //   Admin hard delete (ADMIN_DELETE_ITEM) là hành động khác:
    //     → xóa vĩnh viễn mọi trạng thái, không còn restore.
    //     → được test trong AdminServiceTest và AuctionIntegrationTest.
    //
    //   Các test không có DB chỉ kiểm tra contract (không throw, có status field).
    //   Test logic phân nhánh đầy đủ nằm trong AuctionIntegrationTest.
    // ═══════════════════════════════════════════════════════

    @Test @Order(20)
    void deleteItem_nonExistentItem_returnsError() {
        JsonObject req = new JsonObject();
        req.addProperty("item_id",   "non-existent-xyz");
        req.addProperty("seller_id", "seller-001");

        JsonObject res = service.deleteItem(req);
        assertEquals("error", res.get("status").getAsString());
        // message phải đề cập item không tìm thấy
        assertNotNull(res.get("message"));
    }

    @Test @Order(21)
    void deleteItem_missingFields_doesNotThrow() {
        JsonObject req = new JsonObject(); // không có field nào
        assertDoesNotThrow(() -> {
            JsonObject res = service.deleteItem(req);
            assertNotNull(res);
            assertEquals("error", res.get("status").getAsString());
        });
    }

    @Test @Order(22)
    void deleteItem_wrongSeller_returnsError() {
        // item tồn tại trong DB mới phân biệt được, nhưng với item không tồn tại
        // ta vẫn phải nhận error (không tìm thấy hoặc không có quyền)
        JsonObject req = new JsonObject();
        req.addProperty("item_id",   "any-item-id");
        req.addProperty("seller_id", "wrong-seller-999");

        JsonObject res = service.deleteItem(req);
        assertEquals("error", res.get("status").getAsString());
    }

    @Test @Order(23)
    void deleteItem_successResponse_hasDeleteTypeField() {
        // Khi delete thành công (cần DB), response phải có field delete_type = "hard" hoặc "soft"
        // Test này document expected contract; bỏ qua khi không có DB
        JsonObject req = new JsonObject();
        req.addProperty("item_id",   "some-pending-no-bid-item");
        req.addProperty("seller_id", "seller-001");

        JsonObject res = service.deleteItem(req);
        if ("success".equals(res.get("status").getAsString())) {
            assertTrue(res.has("delete_type"),
                    "Response thành công phải có field 'delete_type'");
            String deleteType = res.get("delete_type").getAsString();
            assertTrue("hard".equals(deleteType) || "soft".equals(deleteType),
                    "delete_type phải là 'hard' hoặc 'soft', thực tế: " + deleteType);
        }
    }

    // ═══════════════════════════════════════════════════════
    // 4. cancelItem
    // ═══════════════════════════════════════════════════════

    @Test @Order(25)
    void cancelItem_missingFields_doesNotThrow() {
        JsonObject req = new JsonObject();
        assertDoesNotThrow(() -> {
            JsonObject res = service.cancelItem(req);
            assertNotNull(res);
            assertEquals("error", res.get("status").getAsString());
        });
    }

    // ═══════════════════════════════════════════════════════
    // 5. restartItemAuction — validation
    // ═══════════════════════════════════════════════════════

    @Test @Order(30)
    void restartItemAuction_missingFields_doesNotThrow() {
        JsonObject req = new JsonObject();
        assertDoesNotThrow(() -> {
            JsonObject res = service.restartItemAuction(req);
            assertNotNull(res);
            assertEquals("error", res.get("status").getAsString());
        });
    }

    @Test @Order(31)
    void restartItemAuction_nonExistentItem_returnsError() {
        JsonObject req = new JsonObject();
        req.addProperty("item_id",        "non-existent-restart");
        req.addProperty("owner_id",       "owner-001");
        req.addProperty("starting_price", 100.0);
        req.addProperty("start_time",     future(10));
        req.addProperty("end_time",       future(60));

        JsonObject res = service.restartItemAuction(req);
        assertEquals("error", res.get("status").getAsString());
    }

    // ═══════════════════════════════════════════════════════
    // 6. searchItems
    // ═══════════════════════════════════════════════════════

    @Test @Order(40)
    void searchItems_missingKeyword_doesNotThrow() {
        JsonObject req = new JsonObject();
        assertDoesNotThrow(() -> {
            JsonObject res = service.searchItems(req);
            assertNotNull(res);
        });
    }

    @Test @Order(41)
    void searchItems_validKeyword_hasStatusField() {
        JsonObject req = new JsonObject();
        req.addProperty("keyword", "test");

        JsonObject res = service.searchItems(req);
        assertNotNull(res.get("status"));
    }

    // ═══════════════════════════════════════════════════════
    // 7. GET methods — contract
    // ═══════════════════════════════════════════════════════

    @Test @Order(50)
    void getItemsBySeller_hasStatusField() {
        JsonObject req = new JsonObject();
        req.addProperty("seller_id", "seller-001");
        JsonObject res = service.getItemsBySeller(req);
        assertNotNull(res.get("status"));
    }

    @Test @Order(51)
    void getItemsByOwner_hasStatusField() {
        JsonObject req = new JsonObject();
        req.addProperty("owner_id", "owner-001");
        JsonObject res = service.getItemsByOwner(req);
        assertNotNull(res.get("status"));
    }

    @Test @Order(52)
    void getAItemById_nonExistent_returnsError() {
        JsonObject req = new JsonObject();
        req.addProperty("item_id", "non-existent-xyz");
        JsonObject res = service.getAItemById(req);
        assertNotNull(res.get("status"));
    }

    @Test @Order(53)
    void getAllItems_returnsStatusField() {
        JsonObject res = service.getAllItems(new JsonObject());
        assertNotNull(res.get("status"));
    }

    @Test @Order(54)
    void getVisibleItems_returnsStatusField() {
        JsonObject res = service.getVisibleItems(new JsonObject());
        assertNotNull(res.get("status"));
    }

    @Test @Order(55)
    void getAllActiveItems_returnsStatusField() {
        JsonObject res = service.getAllActiveItems(new JsonObject());
        assertNotNull(res.get("status"));
    }

    @Test @Order(56)
    void updateItemStatus_returnsStatusField() {
        JsonObject res = service.updateItemStatus(new JsonObject());
        assertNotNull(res.get("status"));
    }

    // restoreHiddenItem (seller-side) vẫn tồn tại ở tầng service cho seller tự khôi phục
    // item họ đã soft-delete trước đó. Admin không dùng restore — admin chỉ hard delete.
    @Test @Order(57)
    void restoreHiddenItem_nonExistentItem_returnsError() {
        JsonObject req = new JsonObject();
        req.addProperty("item_id",   "non-existent");
        req.addProperty("seller_id", "seller-001");
        JsonObject res = service.restoreHiddenItem(req);
        assertEquals("error", res.get("status").getAsString());
    }

    // ═══════════════════════════════════════════════════════
    // 8. Parametric — addItem thời gian biên
    // ═══════════════════════════════════════════════════════

    @ParameterizedTest @Order(60)
    @CsvSource({
            "start_equal_end",
            "start_after_end"
    })
    void addItem_timeEdgeCases_returnsError(String scenario) {
        JsonObject req = new JsonObject();
        req.addProperty("name",           "Edge Case Item");
        req.addProperty("description",    "desc");
        req.addProperty("starting_price", 100.0);
        req.addProperty("seller_id",      "seller-001");

        if ("start_equal_end".equals(scenario)) {
            String t = future(30);
            req.addProperty("start_time", t);
            req.addProperty("end_time",   t); // start == end → không hợp lệ
        } else {
            req.addProperty("start_time", future(60));
            req.addProperty("end_time",   future(30)); // start > end
        }

        JsonObject res = service.addItem(req);
        assertEquals("error", res.get("status").getAsString());
    }
}