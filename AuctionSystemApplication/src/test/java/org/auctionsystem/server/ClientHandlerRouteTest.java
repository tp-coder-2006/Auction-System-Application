package org.auctionsystem.server;

import com.google.gson.JsonObject;
import org.auctionsystem.server.handler.*;
import org.auctionsystem.server.session.SessionManager;
import org.auctionsystem.server.session.UserSession;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test tầng Handler / Route trong ClientHandler.
 *
 * Không cần DB, không cần Socket thật.
 * Dùng reflection để gọi route() và requireSession() / requireAdmin() trực tiếp.
 *
 * Kiểm tra:
 *  1. requireSession() — thiếu field, session hết hạn, session hợp lệ
 *  2. requireAdmin()   — không phải ADMIN, đúng ADMIN
 *  3. route()          — action không tồn tại trả lỗi sạch
 *  4. route()          — action cần session mà không có → trả NO_SESSION
 *  5. route()          — action cần admin mà role sai → trả FORBIDDEN
 *  6. route()          — LOGOUT không cần session, luôn trả success
 *  7. route()          — LOGIN / REGISTER không cần session
 *  8. route()          — exception trong service được bắt, không crash
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ClientHandlerRouteTest {

    // ClientHandler cần Socket — dùng fake socket helper
    private ClientHandler handler;
    private Method routeMethod;
    private Method requireSessionMethod;
    private Method requireAdminMethod;

    // Session hợp lệ cho bidder và admin
    private static final String BIDDER_SESSION = "test-session-bidder";
    private static final String ADMIN_SESSION  = "test-session-admin";

    @BeforeEach
    void setUp() throws Exception {
        // Tạo ClientHandler với socket null — chỉ test route(), không gửi data
        handler = new ClientHandler(null);

        // Mở reflection cho các method private
        routeMethod = ClientHandler.class.getDeclaredMethod("route", String.class, JsonObject.class);
        routeMethod.setAccessible(true);

        requireSessionMethod = ClientHandler.class.getDeclaredMethod("requireSession", JsonObject.class);
        requireSessionMethod.setAccessible(true);

        requireAdminMethod = ClientHandler.class.getDeclaredMethod("requireAdmin", JsonObject.class);
        requireAdminMethod.setAccessible(true);

        // Đăng ký session bidder
        SessionManager.addSession(new UserSession(
                BIDDER_SESSION, "user-bidder-01", "Test Bidder", "bidder_test",
                "bidder@test.com", "BIDDER", 1000.0, null, null, 0, null
        ));

        // Đăng ký session admin
        SessionManager.addSession(new UserSession(
                ADMIN_SESSION, "user-admin-01", "Test Admin", "admin_test",
                "admin@test.com", "ADMIN", 0.0, null, null, 0, null
        ));
    }

    @AfterEach
    void tearDown() {
        SessionManager.removeSession(BIDDER_SESSION);
        SessionManager.removeSession(ADMIN_SESSION);
    }

    // ═══════════════════════════════════════════════════════
    // 1. requireSession()
    // ═══════════════════════════════════════════════════════

    @Test @Order(10)
    void requireSession_missingField_returnsNoSession() throws Exception {
        JsonObject req = new JsonObject(); // không có session_id
        JsonObject result = (JsonObject) requireSessionMethod.invoke(handler, req);

        assertNotNull(result, "Phải trả lỗi khi thiếu session_id");
        assertEquals("error", result.get("status").getAsString());
        assertEquals("NO_SESSION", result.get("code").getAsString());
    }

    @Test @Order(11)
    void requireSession_nullSessionId_returnsNoSession() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", (String) null);

        JsonObject result = (JsonObject) requireSessionMethod.invoke(handler, req);
        assertNotNull(result);
        assertEquals("NO_SESSION", result.get("code").getAsString());
    }

    @Test @Order(12)
    void requireSession_expiredSession_returnsExpired() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", "non-existent-session-xyz");

        JsonObject result = (JsonObject) requireSessionMethod.invoke(handler, req);
        assertNotNull(result);
        assertEquals("expired", result.get("status").getAsString());
        assertEquals("SESSION_EXPIRED", result.get("code").getAsString());
    }

    @Test @Order(13)
    void requireSession_validSession_returnsNull() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", BIDDER_SESSION);

        JsonObject result = (JsonObject) requireSessionMethod.invoke(handler, req);
        assertNull(result, "Session hợp lệ phải trả null (không có lỗi)");
    }

    // ═══════════════════════════════════════════════════════
    // 2. requireAdmin()
    // ═══════════════════════════════════════════════════════

    @Test @Order(20)
    void requireAdmin_noSession_returnsNoSession() throws Exception {
        JsonObject req = new JsonObject();
        JsonObject result = (JsonObject) requireAdminMethod.invoke(handler, req);

        assertNotNull(result);
        assertEquals("NO_SESSION", result.get("code").getAsString());
    }

    @Test @Order(21)
    void requireAdmin_bidderRole_returnsForbidden() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", BIDDER_SESSION);

        JsonObject result = (JsonObject) requireAdminMethod.invoke(handler, req);
        assertNotNull(result);
        assertEquals("error", result.get("status").getAsString());
        assertEquals("FORBIDDEN", result.get("code").getAsString());
    }

    @Test @Order(22)
    void requireAdmin_adminRole_returnsNull() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", ADMIN_SESSION);

        JsonObject result = (JsonObject) requireAdminMethod.invoke(handler, req);
        assertNull(result, "Admin hợp lệ phải trả null");
    }

    // ═══════════════════════════════════════════════════════
    // 3. route() — unknown action
    // ═══════════════════════════════════════════════════════

    @Test @Order(30)
    void route_unknownAction_returnsError() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("action", "TOTALLY_FAKE_ACTION_XYZ");

        JsonObject result = (JsonObject) routeMethod.invoke(handler, "TOTALLY_FAKE_ACTION_XYZ", req);
        assertNotNull(result);
        assertEquals("error", result.get("status").getAsString());
        assertTrue(result.get("message").getAsString().contains("TOTALLY_FAKE_ACTION_XYZ"),
                "Message phải chứa tên action không hỗ trợ");
    }

    @Test @Order(31)
    void route_emptyAction_returnsError() throws Exception {
        JsonObject req = new JsonObject();
        JsonObject result = (JsonObject) routeMethod.invoke(handler, "UNKNOWN", req);

        assertNotNull(result);
        assertEquals("error", result.get("status").getAsString());
    }

    // ═══════════════════════════════════════════════════════
    // 4. route() — action cần session mà không có
    // ═══════════════════════════════════════════════════════

    @Test @Order(40)
    void route_getProfile_withoutSession_returnsNoSession() throws Exception {
        JsonObject req = new JsonObject(); // không có session_id
        JsonObject result = (JsonObject) routeMethod.invoke(handler, "GET_PROFILE", req);

        assertNotNull(result);
        assertEquals("NO_SESSION", result.get("code").getAsString());
    }

    @Test @Order(41)
    void route_placeBid_withoutSession_returnsNoSession() throws Exception {
        JsonObject req = new JsonObject();
        JsonObject result = (JsonObject) routeMethod.invoke(handler, "PLACE_BID", req);

        assertNotNull(result);
        assertEquals("NO_SESSION", result.get("code").getAsString());
    }

    @Test @Order(42)
    void route_addItem_withoutSession_returnsNoSession() throws Exception {
        JsonObject req = new JsonObject();
        JsonObject result = (JsonObject) routeMethod.invoke(handler, "ADD_ITEM", req);

        assertNotNull(result);
        assertEquals("NO_SESSION", result.get("code").getAsString());
    }

    @Test @Order(43)
    void route_deposit_withoutSession_returnsNoSession() throws Exception {
        JsonObject req = new JsonObject();
        JsonObject result = (JsonObject) routeMethod.invoke(handler, "DEPOSIT", req);

        assertNotNull(result);
        assertEquals("NO_SESSION", result.get("code").getAsString());
    }

    @Test @Order(44)
    void route_withdraw_withoutSession_returnsNoSession() throws Exception {
        JsonObject req = new JsonObject();
        JsonObject result = (JsonObject) routeMethod.invoke(handler, "WITHDRAW", req);

        assertNotNull(result);
        assertEquals("NO_SESSION", result.get("code").getAsString());
    }

    // ═══════════════════════════════════════════════════════
    // 5. route() — action admin mà role không phải ADMIN
    // ═══════════════════════════════════════════════════════

    @Test @Order(50)
    void route_banUser_bidderRole_returnsForbidden() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", BIDDER_SESSION);
        req.addProperty("user_id", "some-user-id");

        JsonObject result = (JsonObject) routeMethod.invoke(handler, "BAN_USER", req);
        assertNotNull(result);
        assertEquals("FORBIDDEN", result.get("code").getAsString());
    }

    @Test @Order(51)
    void route_getAllUsers_bidderRole_returnsForbidden() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", BIDDER_SESSION);

        JsonObject result = (JsonObject) routeMethod.invoke(handler, "GET_ALL_USERS", req);
        assertNotNull(result);
        assertEquals("FORBIDDEN", result.get("code").getAsString());
    }

    @Test @Order(52)
    void route_getSystemStats_bidderRole_returnsForbidden() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", BIDDER_SESSION);

        JsonObject result = (JsonObject) routeMethod.invoke(handler, "GET_SYSTEM_STATS", req);
        assertNotNull(result);
        assertEquals("FORBIDDEN", result.get("code").getAsString());
    }

    @Test @Order(53)
    void route_adminGetTransactionsByUser_bidderRole_returnsForbidden() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", BIDDER_SESSION);

        JsonObject result = (JsonObject) routeMethod.invoke(handler, "ADMIN_GET_TRANSACTIONS_BY_USER", req);
        assertNotNull(result);
        assertEquals("FORBIDDEN", result.get("code").getAsString());
    }

    // ═══════════════════════════════════════════════════════
    // 6. route() — LOGOUT luôn thành công dù không có session
    // ═══════════════════════════════════════════════════════

    @Test @Order(60)
    void route_logout_withoutSession_returnsSuccess() throws Exception {
        JsonObject req = new JsonObject(); // không có session_id
        JsonObject result = (JsonObject) routeMethod.invoke(handler, "LOGOUT", req);

        assertNotNull(result);
        assertEquals("success", result.get("status").getAsString());
    }

    @Test @Order(61)
    void route_logout_withSession_removesSession() throws Exception {
        // Tạo session tạm
        String tempSession = "temp-logout-session";
        SessionManager.addSession(new UserSession(
                tempSession, "user-temp", "Temp", "temp_user",
                "temp@test.com", "BIDDER", 0.0, null, null, 0, null
        ));

        JsonObject req = new JsonObject();
        req.addProperty("session_id", tempSession);
        JsonObject result = (JsonObject) routeMethod.invoke(handler, "LOGOUT", req);

        assertEquals("success", result.get("status").getAsString());
        assertNull(SessionManager.getSession(tempSession),
                "Session phải bị xóa sau LOGOUT");
    }

    // ═══════════════════════════════════════════════════════
    // 7. route() — LOGIN / REGISTER không cần session
    // ═══════════════════════════════════════════════════════

    @Test @Order(70)
    void route_login_missingCredentials_returnsError() throws Exception {
        JsonObject req = new JsonObject(); // thiếu username/password
        JsonObject result = (JsonObject) routeMethod.invoke(handler, "LOGIN", req);

        assertNotNull(result);
        // Service sẽ trả error vì thiếu field — không crash
        assertEquals("error", result.get("status").getAsString());
    }

    @Test @Order(71)
    void route_register_missingFields_returnsError() throws Exception {
        JsonObject req = new JsonObject(); // thiếu toàn bộ field
        JsonObject result = (JsonObject) routeMethod.invoke(handler, "REGISTER", req);

        assertNotNull(result);
        assertEquals("error", result.get("status").getAsString());
    }

    // ═══════════════════════════════════════════════════════
    // 8. route() — getBidsByBidder phân quyền xem của người khác
    // ═══════════════════════════════════════════════════════

    @Test @Order(80)
    void route_getBidsByBidder_viewOthersBids_returnsForbidden() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", BIDDER_SESSION);
        req.addProperty("bidder_id", "some-other-user-id"); // xem của người khác

        JsonObject result = (JsonObject) routeMethod.invoke(handler, "GET_BIDS_BY_BIDDER", req);
        assertNotNull(result);
        assertEquals("error", result.get("status").getAsString());
        assertTrue(result.get("message").getAsString().contains("quyền"),
                "Phải báo lỗi phân quyền");
    }

    @Test @Order(81)
    void route_getBidsByBidder_adminCanViewOthers_doesNotReturnForbidden() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("session_id", ADMIN_SESSION);
        req.addProperty("bidder_id", "any-bidder-id"); // admin xem của người khác

        JsonObject result = (JsonObject) routeMethod.invoke(handler, "GET_BIDS_BY_BIDDER", req);
        assertNotNull(result);
        // Admin không bị chặn bởi phân quyền — có thể error vì DB không có data,
        // nhưng không phải FORBIDDEN
        assertNotEquals("FORBIDDEN", result.has("code") ? result.get("code").getAsString() : "");
    }

    // ═══════════════════════════════════════════════════════
    // 9. route() — response luôn có field "status"
    // ═══════════════════════════════════════════════════════

    @Test @Order(90)
    void route_anyAction_responseAlwaysHasStatus() throws Exception {
        String[] actions = {
                "LOGIN", "REGISTER", "LOGOUT", "UNKNOWN_XYZ",
                "GET_PROFILE", "PLACE_BID", "BAN_USER"
        };

        for (String action : actions) {
            JsonObject req = new JsonObject();
            JsonObject result = (JsonObject) routeMethod.invoke(handler, action, req);

            assertNotNull(result, "route(" + action + ") không được trả null");
            assertTrue(result.has("status"),
                    "response của action '" + action + "' phải có field 'status'");
        }
    }
}