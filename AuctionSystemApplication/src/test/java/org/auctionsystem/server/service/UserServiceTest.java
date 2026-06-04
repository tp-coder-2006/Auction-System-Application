package org.auctionsystem.server.service;

import com.google.gson.JsonObject;
import org.auctionsystem.model.entities.Seller;
import org.auctionsystem.model.entities.User;
import org.auctionsystem.model.enums.UserRole;
import org.auctionsystem.server.DAO.ItemHistoryDAO;
import org.auctionsystem.server.DAO.UserDAO;
import org.auctionsystem.server.session.SessionManager;
import org.auctionsystem.server.session.UserSession;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho UserService — không cần DB thật.
 * Các hàm phụ thuộc DAO được kiểm tra qua logic thuần.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserServiceTest {

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService();
    }

    // ═══════════════════════════════════════════════════════
    // 1. isEmailValid
    // ═══════════════════════════════════════════════════════

    @Test @Order(1)
    void isEmailValid_validEmail_returnsTrue() {
        assertTrue(service.isEmailValid("user@example.com"));
        assertTrue(service.isEmailValid("user.name+tag@sub.domain.org"));
    }

    @ParameterizedTest @Order(2)
    @ValueSource(strings = {"", "notanemail", "missing@", "@nodomain.com", "space @x.com"})
    void isEmailValid_invalidEmails_returnsFalse(String email) {
        assertFalse(service.isEmailValid(email));
    }

    // ═══════════════════════════════════════════════════════
    // 2. validatePassword
    // ═══════════════════════════════════════════════════════

    @Test @Order(3)
    void validatePassword_validPassword_returnsEmptyList() {
        List<String> errors = UserService.validatePassword("Abcdef1@");
        assertTrue(errors.isEmpty(), "Mật khẩu hợp lệ không được có lỗi");
    }

    @Test @Order(4)
    void validatePassword_nullPassword_returnsError() {
        List<String> errors = UserService.validatePassword(null);
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("trống"));
    }

    @Test @Order(5)
    void validatePassword_tooShort_returnsError() {
        List<String> errors = UserService.validatePassword("Ab1@");
        assertTrue(errors.stream().anyMatch(e -> e.contains("8 ký tự")));
    }

    @Test @Order(6)
    void validatePassword_noUppercase_returnsError() {
        List<String> errors = UserService.validatePassword("abcdef1@");
        assertTrue(errors.stream().anyMatch(e -> e.contains("chữ hoa")));
    }

    @Test @Order(7)
    void validatePassword_noLowercase_returnsError() {
        List<String> errors = UserService.validatePassword("ABCDEF1@");
        assertTrue(errors.stream().anyMatch(e -> e.contains("chữ thường")));
    }

    @Test @Order(8)
    void validatePassword_noDigit_returnsError() {
        List<String> errors = UserService.validatePassword("Abcdefg@");
        assertTrue(errors.stream().anyMatch(e -> e.contains("chữ số")));
    }

    @Test @Order(9)
    void validatePassword_noSpecialChar_returnsError() {
        List<String> errors = UserService.validatePassword("Abcdef12");
        assertTrue(errors.stream().anyMatch(e -> e.contains("ký tự đặc biệt")));
    }

    @Test @Order(10)
    void validatePassword_withWhitespace_returnsError() {
        List<String> errors = UserService.validatePassword("Abc def1@");
        assertTrue(errors.stream().anyMatch(e -> e.contains("khoảng trắng")));
    }

    // ═══════════════════════════════════════════════════════
    // 3. hashPassword
    // ═══════════════════════════════════════════════════════

    @Test @Order(11)
    void hashPassword_differentSaltsEachTime() {
        String h1 = UserService.hashPassword("Abcdef1@");
        String h2 = UserService.hashPassword("Abcdef1@");
        assertNotEquals(h1, h2, "BCrypt phải tạo salt mới mỗi lần");
    }

    @Test @Order(12)
    void hashPassword_resultStartsWithBCryptPrefix() {
        String hash = UserService.hashPassword("Abcdef1@");
        assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2b$"));
    }

    // ═══════════════════════════════════════════════════════
    // 4. registerUser — kiểm tra input validation (không cần DB)
    // ═══════════════════════════════════════════════════════

    @Test @Order(20)
    void registerUser_invalidEmail_returnsErrorStatus() {
        JsonObject req = new JsonObject();
        req.addProperty("username", "testuser");
        req.addProperty("password", "Abcdef1@");
        req.addProperty("name", "Test User");
        req.addProperty("email", "invalid-email");
        req.addProperty("role", "BIDDER");

        JsonObject res = service.registerUser(req);

        assertEquals("error", res.get("status").getAsString());
        assertTrue(res.get("message").getAsString().contains("Email"));
    }

    @Test @Order(21)
    void registerUser_weakPassword_returnsErrorStatus() {
        JsonObject req = new JsonObject();
        req.addProperty("username", "testuser");
        req.addProperty("password", "weak");
        req.addProperty("name", "Test User");
        req.addProperty("email", "test@example.com");
        req.addProperty("role", "BIDDER");

        JsonObject res = service.registerUser(req);

        assertEquals("error", res.get("status").getAsString());
    }

    // ═══════════════════════════════════════════════════════
    // 5. updateRating — validation không cần DB
    // ═══════════════════════════════════════════════════════

    @Test @Order(30)
    void updateRating_noSession_returnsError() {
        JsonObject req = new JsonObject();
        req.addProperty("rating", 4.5);
        req.addProperty("seller_username", "some_seller");
        // Không có session_id

        JsonObject res = service.updateRating(req);
        assertEquals("error", res.get("status").getAsString());
    }

    @Test @Order(31)
    void updateRating_invalidSessionId_returnsError() {
        JsonObject req = new JsonObject();
        req.addProperty("rating", 4.5);
        req.addProperty("seller_username", "some_seller");
        req.addProperty("session_id", "non-existent-session-id");

        JsonObject res = service.updateRating(req);
        assertEquals("error", res.get("status").getAsString());
        assertTrue(res.get("message").getAsString().contains("Phiên đăng nhập"));
    }

    @Test @Order(32)
    void updateRating_ratingBelowOne_returnsError() {
        // Tạo session giả với role BIDDER
        String sid = "test-session-rating-low";
        UserSession session = new UserSession(
                sid, "bidder-001", "Bidder Test", "bidder1",
                "b@b.com", "BIDDER", 1000.0, null, null, 0, null);
        SessionManager.addSession(session);

        JsonObject req = new JsonObject();
        req.addProperty("session_id", sid);
        req.addProperty("rating", 0.5);
        req.addProperty("seller_username", "some_seller");

        JsonObject res = service.updateRating(req);
        assertEquals("error", res.get("status").getAsString());
        assertTrue(res.get("message").getAsString().contains("1 đến 5"));

        SessionManager.removeSession(sid);
    }

    @Test @Order(33)
    void updateRating_ratingAboveFive_returnsError() {
        String sid = "test-session-rating-high";
        UserSession session = new UserSession(
                sid, "bidder-001", "Bidder Test", "bidder1",
                "b@b.com", "BIDDER", 1000.0, null, null, 0, null);
        SessionManager.addSession(session);

        JsonObject req = new JsonObject();
        req.addProperty("session_id", sid);
        req.addProperty("rating", 5.1);
        req.addProperty("seller_username", "some_seller");

        JsonObject res = service.updateRating(req);
        assertEquals("error", res.get("status").getAsString());

        SessionManager.removeSession(sid);
    }

    @Test @Order(34)
    void updateRating_callerIsNotBidder_returnsError() {
        String sid = "test-session-seller-rating";
        // Seller cố đánh giá seller khác
        UserSession session = new UserSession(
                sid, "seller-001", "Seller Test", "seller1",
                "s@s.com", "SELLER", 1000.0, null, 4.0, 2, null);
        SessionManager.addSession(session);

        JsonObject req = new JsonObject();
        req.addProperty("session_id", sid);
        req.addProperty("rating", 4.0);
        req.addProperty("seller_username", "other_seller");

        JsonObject res = service.updateRating(req);
        assertEquals("error", res.get("status").getAsString());
        assertTrue(res.get("message").getAsString().contains("Bidder"));

        SessionManager.removeSession(sid);
    }

    // ═══════════════════════════════════════════════════════
    // 6. updatePassword — validation không cần DB
    // ═══════════════════════════════════════════════════════

    @Test @Order(40)
    void updatePassword_weakNewPassword_returnsError() {
        JsonObject req = new JsonObject();
        req.addProperty("user_id", "user-001");
        req.addProperty("old_password", "OldPass1@");
        req.addProperty("new_password", "weak");

        JsonObject res = service.updatePassword(req);
        assertEquals("error", res.get("status").getAsString());
    }

    // ═══════════════════════════════════════════════════════
    // 7. getMyProfile / getOtherProfile — không cần DB
    // ═══════════════════════════════════════════════════════

    @Test @Order(50)
    void getMyProfile_missingUserId_returnsError() {
        // [SỬA] Expectation rõ ràng: thiếu user_id → error, không throw
        JsonObject req = new JsonObject();
        assertDoesNotThrow(() -> {
            JsonObject res = service.getMyProfile(req);
            assertNotNull(res, "Response không được null");
            assertEquals("error", res.get("status").getAsString(),
                    "Thiếu user_id phải trả về status error");
        });
    }

    @Test @Order(51)
    void getMyProfile_withValidUserIdButNoSession_returnsErrorOrResult() {
        // Không cần session để getMyProfile (chỉ cần user_id)
        // Nếu không có DB → error; nếu có DB nhưng user không tồn tại → error
        JsonObject req = new JsonObject();
        req.addProperty("user_id",    "non-existent-user-xyz");
        req.addProperty("session_id", "any-session");

        assertDoesNotThrow(() -> {
            JsonObject res = service.getMyProfile(req);
            assertNotNull(res);
            assertNotNull(res.get("status"));
        });
    }

    // ═══════════════════════════════════════════════════════
    // 8. updateProfile — validate kích thước avatar
    // ═══════════════════════════════════════════════════════

    @Test @Order(60)
    void updateProfile_avatarOver5MB_returnsErrorWithSizeMessage() {
        // Avatar > 5MB phải bị từ chối TRƯỚC khi chạm DB hay ghi file
        byte[] over5MB = new byte[5 * 1024 * 1024 + 1];
        String encoded = Base64.getEncoder().encodeToString(over5MB);

        JsonObject req = new JsonObject();
        req.addProperty("user_id",    "user-001");
        req.addProperty("name",       "Test User");
        req.addProperty("email",      "test@example.com");
        req.addProperty("image_data", encoded);
        req.addProperty("extension",  "jpg");

        JsonObject res = service.updateProfile(req);
        assertEquals("error", res.get("status").getAsString());
        assertTrue(res.get("message").getAsString().contains("quá lớn"),
                "Avatar > 5MB phải trả về message chứa 'quá lớn'");
    }

    @Test @Order(61)
    void updateProfile_avatar10MB_returnsErrorWithSizeMessage() {
        byte[] tenMB   = new byte[10 * 1024 * 1024];
        String encoded = Base64.getEncoder().encodeToString(tenMB);

        JsonObject req = new JsonObject();
        req.addProperty("user_id",    "user-001");
        req.addProperty("name",       "Test User");
        req.addProperty("email",      "test@example.com");
        req.addProperty("image_data", encoded);
        req.addProperty("extension",  "png");

        JsonObject res = service.updateProfile(req);
        assertEquals("error", res.get("status").getAsString());
        assertTrue(res.get("message").getAsString().contains("quá lớn"),
                "Avatar 10MB phải trả về message chứa 'quá lớn'");
    }

    @Test @Order(62)
    void updateProfile_avatarExactly5MB_notRejectedBySizeCheck() {
        // 5MB chính xác không vượt ngưỡng → lỗi phải là DB (email/user không tồn tại), không phải size
        byte[] exactly5MB = new byte[5 * 1024 * 1024];
        String encoded    = Base64.getEncoder().encodeToString(exactly5MB);

        JsonObject req = new JsonObject();
        req.addProperty("user_id",    "user-001");
        req.addProperty("name",       "Test User");
        req.addProperty("email",      "test@example.com");
        req.addProperty("image_data", encoded);
        req.addProperty("extension",  "jpg");

        JsonObject res = service.updateProfile(req);
        assertEquals("error", res.get("status").getAsString());
        assertFalse(res.get("message").getAsString().contains("quá lớn"),
                "5MB chính xác không được bị từ chối bởi size check");
    }

    @Test @Order(63)
    void updateProfile_invalidAvatarExtension_returnsError() {
        JsonObject req = new JsonObject();
        req.addProperty("user_id",    "user-001");
        req.addProperty("name",       "Test User");
        req.addProperty("email",      "test@example.com");
        req.addProperty("image_data", "dGVzdA=="); // valid base64
        req.addProperty("extension",  "gif");       // không được phép

        JsonObject res = service.updateProfile(req);
        assertEquals("error", res.get("status").getAsString());
        assertTrue(res.get("message").getAsString().toLowerCase().contains("định dạng"),
                "Extension không hợp lệ phải trả về message về định dạng");
    }

    @Test @Order(64)
    void updateProfile_invalidBase64Avatar_returnsError() {
        JsonObject req = new JsonObject();
        req.addProperty("user_id",    "user-001");
        req.addProperty("name",       "Test User");
        req.addProperty("email",      "test@example.com");
        req.addProperty("image_data", "NOT_VALID_BASE64!!!");
        req.addProperty("extension",  "jpg");

        JsonObject res = service.updateProfile(req);
        assertEquals("error", res.get("status").getAsString());
        assertTrue(res.get("message").getAsString().toLowerCase().contains("base64")
                        || res.get("message").getAsString().toLowerCase().contains("ảnh"),
                "Base64 không hợp lệ phải trả về message liên quan đến ảnh");
    }
}