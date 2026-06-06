package org.auctionsystem.server.service;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho ItemHistoryService.
 *
 * Test tập trung vào:
 *  - Contract response: luôn có field "status"
 *  - Missing field → không throw, trả về error
 *  - Non-existent ID → trả về success + mảng rỗng (hoặc error nếu không có DB)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ItemHistoryServiceTest {

    private ItemHistoryService service;

    @BeforeEach
    void setUp() {
        service = new ItemHistoryService();
    }

    // ═══════════════════════════════════════════════════════
    // getHistoryBySeller
    // ═══════════════════════════════════════════════════════

    @Test @Order(1)
    void getHistoryBySeller_missingField_doesNotThrow() {
        assertDoesNotThrow(() -> {
            JsonObject res = service.getHistoryBySeller(new JsonObject());
            assertNotNull(res);
            assertNotNull(res.get("status"));
        });
    }

    @Test @Order(2)
    void getHistoryBySeller_nonExistentSeller_hasStatusField() {
        JsonObject req = new JsonObject();
        req.addProperty("seller_id", "non-existent-seller-xyz");

        JsonObject res = service.getHistoryBySeller(req);
        assertNotNull(res.get("status"),
                "Response phải có field 'status' dù seller không tồn tại");
    }

    // ═══════════════════════════════════════════════════════
    // getHistoryByBuyer
    // ═══════════════════════════════════════════════════════

    @Test @Order(3)
    void getHistoryByBuyer_missingField_doesNotThrow() {
        assertDoesNotThrow(() -> {
            JsonObject res = service.getHistoryByBuyer(new JsonObject());
            assertNotNull(res);
            assertNotNull(res.get("status"));
        });
    }

    @Test @Order(4)
    void getHistoryByBuyer_nonExistentBuyer_hasStatusField() {
        JsonObject req = new JsonObject();
        req.addProperty("buyer_id", "non-existent-buyer-xyz");

        JsonObject res = service.getHistoryByBuyer(req);
        assertNotNull(res.get("status"));
    }

    // ═══════════════════════════════════════════════════════
    // getHistoryByItem
    // ═══════════════════════════════════════════════════════

    @Test @Order(5)
    void getHistoryByItem_missingField_doesNotThrow() {
        assertDoesNotThrow(() -> {
            JsonObject res = service.getHistoryByItem(new JsonObject());
            assertNotNull(res);
            assertNotNull(res.get("status"));
        });
    }

    @Test @Order(6)
    void getHistoryByItem_nonExistentItem_hasStatusField() {
        JsonObject req = new JsonObject();
        req.addProperty("item_id", "non-existent-item-xyz");

        JsonObject res = service.getHistoryByItem(req);
        assertNotNull(res.get("status"));
    }
}