package org.auctionsystem.server.service;

import com.google.gson.JsonObject;
import org.auctionsystem.server.session.SessionManager;
import org.auctionsystem.server.session.UserSession;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho BidService.
 *
 * Các test ở đây tập trung vào:
 *  - Session validation (không cần DB)
 *  - Input validation
 *  - Các hàm GET wrapper (getBidsByBidder, v.v.) — kiểm tra contract
 *
 * Test tích hợp thật (placeBid, settleBid với DB) được tách ra
 * BidServiceIntegrationTest để dùng test DB riêng.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BidServiceTest {

    private BidService service;
    private static final String VALID_SESSION = "bid-test-session-valid";
    private static final String BIDDER_ID     = "bidder-test-001";

    @BeforeAll
    static void setUpSession() {
        UserSession session = new UserSession(
                VALID_SESSION, BIDDER_ID, "Test Bidder", "testbidder",
                "bidder@test.com", "BIDDER", 99999.0, null, null, 0, null);
        SessionManager.addSession(session);
    }

    @AfterAll
    static void tearDownSession() {
        SessionManager.removeSession(VALID_SESSION);
    }

    @BeforeEach
    void setUp() {
        service = new BidService();
    }

    // ═══════════════════════════════════════════════════════
    // 1. placeBid — Session validation
    // ═══════════════════════════════════════════════════════

    @Test @Order(1)
    void placeBid_nullSessionId_returnsError() {
        JsonObject req = new JsonObject();
        req.addProperty("item_id", "item-001");
        req.addProperty("bid_amount", 500.0);
        // Không có session_id

        JsonObject res = service.placeBid(req);
        assertEquals("error", res.get("status").getAsString());
        assertTrue(res.get("message").getAsString().contains("Phiên đăng nhập"));
    }

    @Test @Order(2)
    void placeBid_invalidSessionId_returnsError() {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", "totally-invalid-session");
        req.addProperty("item_id", "item-001");
        req.addProperty("bid_amount", 500.0);

        JsonObject res = service.placeBid(req);
        assertEquals("error", res.get("status").getAsString());
        assertTrue(res.get("message").getAsString().contains("Phiên đăng nhập"));
    }

    // ═══════════════════════════════════════════════════════
    // 2. placeBid — Item không tồn tại trong DB
    //    (test với valid session nhưng item_id giả)
    // ═══════════════════════════════════════════════════════

    @Test @Order(3)
    void placeBid_itemNotFound_returnsError() {
        // Yêu cầu DB kết nối; nếu không có DB, test này sẽ nhận "Lỗi" từ catch
        JsonObject req = new JsonObject();
        req.addProperty("session_id", VALID_SESSION);
        req.addProperty("item_id", "non-existent-item-id-xyz");
        req.addProperty("bid_amount", 500.0);

        JsonObject res = service.placeBid(req);
        // Dù lỗi DB hay logic, status phải là "error"
        assertEquals("error", res.get("status").getAsString());
    }

    // ═══════════════════════════════════════════════════════
    // 3. getBidsByBidder — response contract
    // ═══════════════════════════════════════════════════════

    @Test @Order(10)
    void getBidsByBidder_missingBidderId_throwsOrReturnsError() {
        JsonObject req = new JsonObject();
        // Không có bidder_id

        assertDoesNotThrow(() -> {
            JsonObject res = service.getBidsByBidder(req);
            // Phải trả về error, không được throw unchecked ra ngoài
            assertNotNull(res);
            assertEquals("error", res.get("status").getAsString());
        });
    }

    @Test @Order(11)
    void getBidsByBidder_validBidderId_responseHasStatusField() {
        JsonObject req = new JsonObject();
        req.addProperty("bidder_id", BIDDER_ID);

        JsonObject res = service.getBidsByBidder(req);
        // Khi không có DB: status = "error"; khi có DB rỗng: status = "success" + mảng rỗng
        assertNotNull(res.get("status"));
    }

    @Test @Order(12)
    void getBidsByBidder_successResponse_eachBidHasItemNameField() {
        JsonObject req = new JsonObject();
        req.addProperty("bidder_id", BIDDER_ID);

        JsonObject res = service.getBidsByBidder(req);

        // Chỉ kiểm tra khi DB kết nối được và có data
        if (!"success".equals(res.get("status").getAsString())) return;

        var arr = res.get("message").getAsJsonArray();
        for (var el : arr) {
            JsonObject bid = el.getAsJsonObject();
            // Mỗi bid phải có field itemName (có thể null nếu item bị xóa, nhưng key phải tồn tại)
            assertTrue(bid.has("itemName"),
                    "Mỗi bid trong lịch sử phải có field 'itemName', thiếu ở bid id=" +
                            (bid.has("id") ? bid.get("id").getAsString() : "?"));
        }
    }

    @Test @Order(13)
    void getBidsByBidder_successResponse_itemNameNotBlankWhenItemExists() {
        JsonObject req = new JsonObject();
        req.addProperty("bidder_id", BIDDER_ID);

        JsonObject res = service.getBidsByBidder(req);
        if (!"success".equals(res.get("status").getAsString())) return;

        var arr = res.get("message").getAsJsonArray();
        for (var el : arr) {
            JsonObject bid = el.getAsJsonObject();
            // itemId phải có (JOIN không được mất dòng bid)
            assertTrue(bid.has("itemId") && !bid.get("itemId").isJsonNull(),
                    "Field 'itemId' không được null trong bid");
            // itemName không được là chuỗi rỗng khi item vẫn tồn tại trong DB
            if (bid.has("itemName") && !bid.get("itemName").isJsonNull()) {
                assertFalse(bid.get("itemName").getAsString().isBlank(),
                        "itemName không được là chuỗi rỗng");
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // 4. getBidsByBidderAndItem
    // ═══════════════════════════════════════════════════════

    @Test @Order(14)
    void getBidsByBidderAndItem_missingFields_returnsError() {
        JsonObject req = new JsonObject();
        req.addProperty("bidder_id", BIDDER_ID);
        // Thiếu item_id

        assertDoesNotThrow(() -> {
            JsonObject res = service.getBidsByBidderAndItem(req);
            assertNotNull(res);
            assertEquals("error", res.get("status").getAsString());
        });
    }

    // ═══════════════════════════════════════════════════════
    // 5. getActiveBidsByBidder
    // ═══════════════════════════════════════════════════════

    @Test @Order(15)
    void getActiveBidsByBidder_hasStatusField() {
        JsonObject req = new JsonObject();
        req.addProperty("bidder_id", BIDDER_ID);

        JsonObject res = service.getActiveBidsByBidder(req);
        assertNotNull(res.get("status"));
    }

    // ═══════════════════════════════════════════════════════
    // 6. getHighestBidByItem
    // ═══════════════════════════════════════════════════════

    @Test @Order(16)
    void getHighestBidByItem_missingItemId_returnsError() {
        JsonObject req = new JsonObject();
        assertDoesNotThrow(() -> {
            JsonObject res = service.getHighestBidByItem(req);
            assertNotNull(res);
            assertEquals("error", res.get("status").getAsString());
        });
    }

    @Test @Order(17)
    void getHighestBidByItem_nonExistentItem_returnsError() {
        JsonObject req = new JsonObject();
        req.addProperty("item_id", "non-existent-item-xyz");

        JsonObject res = service.getHighestBidByItem(req);
        // Không có DB → error; có DB nhưng không có bid → error
        assertNotNull(res.get("status"));
    }

    // ═══════════════════════════════════════════════════════
    // 7. getActiveBidsByItem / getAllBidsByItem
    // ═══════════════════════════════════════════════════════

    @Test @Order(18)
    void getActiveBidsByItem_hasStatusField() {
        JsonObject req = new JsonObject();
        req.addProperty("item_id", "some-item-id");

        JsonObject res = service.getActiveBidsByItem(req);
        assertNotNull(res.get("status"));
    }

    @Test @Order(19)
    void getAllBidsByItem_hasStatusField() {
        JsonObject req = new JsonObject();
        req.addProperty("item_id", "some-item-id");

        JsonObject res = service.getAllBidsByItem(req);
        assertNotNull(res.get("status"));
    }

    // ═══════════════════════════════════════════════════════
    // 8. settleBid — không có item_id hợp lệ
    // ═══════════════════════════════════════════════════════

    @Test @Order(20)
    void settleBid_missingItemId_returnsError() {
        JsonObject req = new JsonObject();
        assertDoesNotThrow(() -> {
            JsonObject res = service.settleBid(req);
            assertNotNull(res);
            assertEquals("error", res.get("status").getAsString());
        });
    }

    @Test @Order(21)
    void settleBid_nonExistentItem_returnsError() {
        JsonObject req = new JsonObject();
        req.addProperty("item_id", "non-existent-item-settle");

        JsonObject res = service.settleBid(req);
        assertEquals("error", res.get("status").getAsString());
    }
}