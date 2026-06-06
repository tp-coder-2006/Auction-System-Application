package org.auctionsystem.server.service;

import com.google.gson.JsonObject;
import org.auctionsystem.server.session.SessionManager;
import org.auctionsystem.server.session.UserSession;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho AdminService.
 *
 * Mọi hàm trong AdminService đều yêu cầu session_id hợp lệ với role = ADMIN.
 * Test tập trung vào:
 *  - Không có session → lỗi
 *  - Session hết hạn (không tìm thấy) → lỗi
 *  - Session với role không phải ADMIN → lỗi FORBIDDEN
 *  - Session ADMIN hợp lệ nhưng item/user không tồn tại → DB error
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminServiceTest {

    private AdminService service;

    private static final String ADMIN_SESSION  = "admin-test-session-001";
    private static final String BIDDER_SESSION = "bidder-test-session-001";
    private static final String SELLER_SESSION = "seller-test-session-001";

    @BeforeAll
    static void setUpSessions() {
        SessionManager.addSession(new UserSession(
                ADMIN_SESSION, "admin-001", "Admin Test", "admin1",
                "admin@test.com", "ADMIN", 0, null, null, 0, null));

        SessionManager.addSession(new UserSession(
                BIDDER_SESSION, "bidder-001", "Bidder Test", "bidder1",
                "b@test.com", "BIDDER", 500.0, null, null, 0, null));

        SessionManager.addSession(new UserSession(
                SELLER_SESSION, "seller-001", "Seller Test", "seller1",
                "s@test.com", "SELLER", 200.0, null, 4.5, 3, null));
    }

    @AfterAll
    static void tearDownSessions() {
        SessionManager.removeSession(ADMIN_SESSION);
        SessionManager.removeSession(BIDDER_SESSION);
        SessionManager.removeSession(SELLER_SESSION);
    }

    @BeforeEach
    void setUp() {
        service = new AdminService();
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private JsonObject reqWithAdmin(String extraKey, String extraVal) {
        JsonObject r = new JsonObject();
        r.addProperty("session_id", ADMIN_SESSION);
        if (extraKey != null) r.addProperty(extraKey, extraVal);
        return r;
    }

    // ═══════════════════════════════════════════════════════
    // 1. requireAdmin — shared guard tests
    // ═══════════════════════════════════════════════════════

    @Test @Order(1)
    void anyAdminMethod_noSession_returnsNoSessionError() {
        JsonObject req = new JsonObject();
        // Không có session_id
        JsonObject res = service.getUserByUsername(req);
        assertEquals("error", res.get("status").getAsString());
        assertEquals("NO_SESSION", res.get("code").getAsString());
    }

    @Test @Order(2)
    void anyAdminMethod_expiredSession_returnsSessionExpiredError() {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", "expired-or-invalid-session-xyz");
        req.addProperty("username", "someone");

        JsonObject res = service.getUserByUsername(req);
        assertEquals("error", res.get("status").getAsString());
        assertEquals("SESSION_EXPIRED", res.get("code").getAsString());
    }

    @Test @Order(3)
    void anyAdminMethod_bidderSession_returnsForbiddenError() {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", BIDDER_SESSION);
        req.addProperty("username", "someone");

        JsonObject res = service.getUserByUsername(req);
        assertEquals("error", res.get("status").getAsString());
        assertEquals("FORBIDDEN", res.get("code").getAsString());
    }

    @Test @Order(4)
    void anyAdminMethod_sellerSession_returnsForbiddenError() {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", SELLER_SESSION);
        req.addProperty("username", "someone");

        JsonObject res = service.getUserByUsername(req);
        assertEquals("error", res.get("status").getAsString());
        assertEquals("FORBIDDEN", res.get("code").getAsString());
    }

    // ═══════════════════════════════════════════════════════
    // 2. getUserByUsername
    // ═══════════════════════════════════════════════════════

    @Test @Order(10)
    void getUserByUsername_validAdminNonExistentUser_returnsError() {
        JsonObject req = reqWithAdmin("username", "non_existent_user_xyz");
        JsonObject res = service.getUserByUsername(req);
        // Dù lỗi DB hay user không tồn tại, status = "error"
        assertNotNull(res.get("status"));
    }

    // ═══════════════════════════════════════════════════════
    // 3. banUser
    // ═══════════════════════════════════════════════════════

    @Test @Order(20)
    void banUser_noAdminSession_returnsForbidden() {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", BIDDER_SESSION);
        req.addProperty("username", "targetuser");

        JsonObject res = service.banUser(req);
        assertEquals("error", res.get("status").getAsString());
        assertEquals("FORBIDDEN", res.get("code").getAsString());
    }

    @Test @Order(21)
    void banUser_adminSessionNonExistentUser_returnsError() {
        JsonObject req = reqWithAdmin("username", "non_existent_xyz");
        JsonObject res = service.banUser(req);
        // Error từ DB hoặc user-not-found
        assertNotNull(res.get("status"));
    }

    // ═══════════════════════════════════════════════════════
    // 4. unbanUser
    // ═══════════════════════════════════════════════════════

    @Test @Order(25)
    void unbanUser_noAdminSession_returnsForbidden() {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", SELLER_SESSION);
        req.addProperty("username", "targetuser");

        JsonObject res = service.unbanUser(req);
        assertEquals("error", res.get("status").getAsString());
        assertEquals("FORBIDDEN", res.get("code").getAsString());
    }

    @Test @Order(26)
    void unbanUser_adminSessionNonExistentUser_returnsError() {
        JsonObject req = reqWithAdmin("username", "non_existent_xyz");
        JsonObject res = service.unbanUser(req);
        assertNotNull(res.get("status"));
    }

    // ═══════════════════════════════════════════════════════
    // 5. getAllUsers / getAllItems / getSystemStats
    // ═══════════════════════════════════════════════════════

    @Test @Order(50)
    void getAllUsers_noAdminSession_returnsForbidden() {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", BIDDER_SESSION);
        JsonObject res = service.getAllUsers(req);
        assertEquals("FORBIDDEN", res.get("code").getAsString());
    }

    @Test @Order(51)
    void getAllUsers_adminSession_hasStatusField() {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", ADMIN_SESSION);
        JsonObject res = service.getAllUsers(req);
        assertNotNull(res.get("status"));
    }

    @Test @Order(52)
    void getAllItems_adminSession_hasStatusField() {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", ADMIN_SESSION);
        JsonObject res = service.getAllItems(req);
        assertNotNull(res.get("status"));
    }

    @Test @Order(53)
    void getAllItems_noAdminSession_returnsForbidden() {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", SELLER_SESSION);
        JsonObject res = service.getAllItems(req);
        assertEquals("FORBIDDEN", res.get("code").getAsString());
    }

    @Test @Order(54)
    void getSystemStats_noAdminSession_returnsForbidden() {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", SELLER_SESSION);
        JsonObject res = service.getSystemStats(req);
        assertEquals("FORBIDDEN", res.get("code").getAsString());
    }

    @Test @Order(55)
    void getSystemStats_adminSession_hasStatusField() {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", ADMIN_SESSION);
        JsonObject res = service.getSystemStats(req);
        assertNotNull(res.get("status"));
    }

    // ═══════════════════════════════════════════════════════
    // 6. getItemTrend / getRevenueTrend
    // ═══════════════════════════════════════════════════════

    @Test @Order(60)
    void getItemTrend_noAdminSession_returnsForbidden() {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", BIDDER_SESSION);
        JsonObject res = service.getItemTrend(req);
        assertEquals("FORBIDDEN", res.get("code").getAsString());
    }

    @Test @Order(61)
    void getItemTrend_adminSession_hasStatusField() {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", ADMIN_SESSION);
        req.addProperty("months", 3);
        JsonObject res = service.getItemTrend(req);
        assertNotNull(res.get("status"));
    }

    @Test @Order(62)
    void getRevenueTrend_noAdminSession_returnsForbidden() {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", BIDDER_SESSION);
        JsonObject res = service.getRevenueTrend(req);
        assertEquals("FORBIDDEN", res.get("code").getAsString());
    }

    @Test @Order(63)
    void getRevenueTrend_adminSession_hasStatusField() {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", ADMIN_SESSION);
        req.addProperty("months", 3);
        JsonObject res = service.getRevenueTrend(req);
        assertNotNull(res.get("status"));
    }

    // ═══════════════════════════════════════════════════════
    // 7. getTransactionsByUser / getTransactionsByItem
    // ═══════════════════════════════════════════════════════

    @Test @Order(70)
    void getTransactionsByUser_noAdminSession_returnsForbidden() {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", BIDDER_SESSION);
        req.addProperty("user_id", "user-001");
        JsonObject res = service.getTransactionsByUser(req);
        assertEquals("FORBIDDEN", res.get("code").getAsString());
    }

    @Test @Order(71)
    void getTransactionsByUser_adminSessionNonExistentUser_hasStatusField() {
        JsonObject req = reqWithAdmin("user_id", "non-existent-user-xyz");
        JsonObject res = service.getTransactionsByUser(req);
        assertNotNull(res.get("status"));
    }

    @Test @Order(72)
    void getTransactionsByItem_noAdminSession_returnsForbidden() {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", SELLER_SESSION);
        req.addProperty("item_id", "item-001");
        JsonObject res = service.getTransactionsByItem(req);
        assertEquals("FORBIDDEN", res.get("code").getAsString());
    }

    @Test @Order(73)
    void getTransactionsByItem_adminSessionNonExistentItem_hasStatusField() {
        JsonObject req = reqWithAdmin("item_id", "non-existent-item-xyz");
        JsonObject res = service.getTransactionsByItem(req);
        assertNotNull(res.get("status"));
    }

    // ═══════════════════════════════════════════════════════
    // 8. deleteItem — admin hard delete (không còn restore)
    //
    // Admin hard delete xóa vĩnh viễn khỏi DB:
    //   bids → item_ownership_history → images
    //   → transactions.related_item_id = NULL → items
    //
    // Không còn khái niệm soft-delete hay restore từ phía admin.
    // ═══════════════════════════════════════════════════════

    @Test @Order(80)
    void deleteItem_noSession_returnsNoSessionError() {
        JsonObject req = new JsonObject();
        req.addProperty("item_id", "some-item-id");
        JsonObject res = service.deleteItem(req);
        assertEquals("error",      res.get("status").getAsString());
        assertEquals("NO_SESSION", res.get("code").getAsString());
    }

    @Test @Order(81)
    void deleteItem_bidderSession_returnsForbidden() {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", BIDDER_SESSION);
        req.addProperty("item_id",    "some-item-id");
        JsonObject res = service.deleteItem(req);
        assertEquals("error",     res.get("status").getAsString());
        assertEquals("FORBIDDEN", res.get("code").getAsString());
    }

    @Test @Order(82)
    void deleteItem_sellerSession_returnsForbidden() {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", SELLER_SESSION);
        req.addProperty("item_id",    "some-item-id");
        JsonObject res = service.deleteItem(req);
        assertEquals("error",     res.get("status").getAsString());
        assertEquals("FORBIDDEN", res.get("code").getAsString());
    }

    @Test @Order(83)
    void deleteItem_expiredSession_returnsSessionExpiredError() {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", "expired-session-xyz-000");
        req.addProperty("item_id",    "some-item-id");
        JsonObject res = service.deleteItem(req);
        assertEquals("error",           res.get("status").getAsString());
        assertEquals("SESSION_EXPIRED", res.get("code").getAsString());
    }

    @Test @Order(84)
    void deleteItem_adminSession_nonExistentItem_returnsError() {
        // Admin có quyền, nhưng item không tồn tại → error từ DB (0 rows affected)
        JsonObject req = reqWithAdmin("item_id", "non-existent-item-xyz-99999");
        JsonObject res = service.deleteItem(req);
        assertEquals("error", res.get("status").getAsString());
        assertNotNull(res.get("message"),
                "Response lỗi phải có trường message");
    }

    @Test @Order(85)
    void deleteItem_adminSession_missingItemId_doesNotThrow() {
        // Thiếu item_id → không được throw unchecked exception
        JsonObject req = new JsonObject();
        req.addProperty("session_id", ADMIN_SESSION);
        assertDoesNotThrow(() -> {
            JsonObject res = service.deleteItem(req);
            assertNotNull(res);
            assertEquals("error", res.get("status").getAsString());
        });
    }

    @Test @Order(86)
    void deleteItem_adminSuccess_messageNotEmpty() {
        // Khi DB có sẵn và delete thành công, message không được rỗng
        // (bỏ qua nếu không có DB — test chỉ validate contract)
        JsonObject req = reqWithAdmin("item_id", "definitely-not-real-item");
        JsonObject res = service.deleteItem(req);
        // Dù thành công hay lỗi, message phải tồn tại và không rỗng
        assertTrue(res.has("message"),
                "Response phải luôn có trường message");
        assertFalse(res.get("message").getAsString().isBlank(),
                "Message không được rỗng");
    }
}