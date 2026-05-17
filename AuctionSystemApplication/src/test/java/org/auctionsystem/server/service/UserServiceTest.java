package org.auctionsystem.server.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserService Tests")
class UserServiceTest {

    private final UserService userService = new UserService();

    // ═══════════════════════════════════════════════════════════
    // validatePassword()
    // ═══════════════════════════════════════════════════════════
    @Nested
    @DisplayName("validatePassword()")
    class ValidatePasswordTests {

        @Test
        @DisplayName("Mật khẩu hợp lệ — không có lỗi")
        void validPassword_returnsEmptyList() {
            List<String> errors = UserService.validatePassword("Test1234@");
            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("Mật khẩu null — trả lỗi ngay")
        void nullPassword_returnsError() {
            List<String> errors = UserService.validatePassword(null);
            assertFalse(errors.isEmpty());
        }

        @Test
        @DisplayName("Mật khẩu rỗng — trả lỗi ngay")
        void emptyPassword_returnsError() {
            List<String> errors = UserService.validatePassword("");
            assertFalse(errors.isEmpty());
        }

        @Test
        @DisplayName("Thiếu chữ hoa — có lỗi")
        void missingUppercase_returnsError() {
            List<String> errors = UserService.validatePassword("test1234@");
            assertTrue(errors.stream().anyMatch(e -> e.contains("chữ hoa")));
        }

        @Test
        @DisplayName("Thiếu chữ thường — có lỗi")
        void missingLowercase_returnsError() {
            List<String> errors = UserService.validatePassword("TEST1234@");
            assertTrue(errors.stream().anyMatch(e -> e.contains("chữ thường")));
        }

        @Test
        @DisplayName("Thiếu chữ số — có lỗi")
        void missingDigit_returnsError() {
            List<String> errors = UserService.validatePassword("TestTest@");
            assertTrue(errors.stream().anyMatch(e -> e.contains("chữ số")));
        }

        @Test
        @DisplayName("Thiếu ký tự đặc biệt — có lỗi")
        void missingSpecialChar_returnsError() {
            List<String> errors = UserService.validatePassword("Test12345");
            assertTrue(errors.stream().anyMatch(e -> e.contains("ký tự đặc biệt")));
        }

        @Test
        @DisplayName("Mật khẩu dưới 8 ký tự — có lỗi")
        void tooShortPassword_returnsError() {
            List<String> errors = UserService.validatePassword("Te1@");
            assertTrue(errors.stream().anyMatch(e -> e.contains("8 ký tự")));
        }

        @Test
        @DisplayName("Mật khẩu có khoảng trắng — có lỗi")
        void passwordWithSpace_returnsError() {
            List<String> errors = UserService.validatePassword("Test 123@");
            assertTrue(errors.stream().anyMatch(e -> e.contains("khoảng trắng")));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // isEmailValid()
    // ═══════════════════════════════════════════════════════════
    @Nested
    @DisplayName("isEmailValid()")
    class IsEmailValidTests {

        @Test
        @DisplayName("Email hợp lệ")
        void validEmail_returnsTrue() {
            assertTrue(userService.isEmailValid("user@example.com"));
        }

        @Test
        @DisplayName("Email có subdomain")
        void emailWithSubdomain_returnsTrue() {
            assertTrue(userService.isEmailValid("user@mail.example.com"));
        }

        @Test
        @DisplayName("Email thiếu @")
        void emailWithoutAt_returnsFalse() {
            assertFalse(userService.isEmailValid("userexample.com"));
        }

        @Test
        @DisplayName("Email thiếu domain")
        void emailWithoutDomain_returnsFalse() {
            assertFalse(userService.isEmailValid("user@"));
        }

        @Test
        @DisplayName("Email rỗng")
        void emptyEmail_returnsFalse() {
            assertFalse(userService.isEmailValid(""));
        }

        @Test
        @DisplayName("Email null")
        void nullEmail_returnsFalse() {
            assertFalse(userService.isEmailValid(null));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // hashPassword()
    // ═══════════════════════════════════════════════════════════
    @Nested
    @DisplayName("hashPassword()")
    class HashPasswordTests {

        @Test
        @DisplayName("Hash không trả về chuỗi rỗng")
        void hashPassword_notEmpty() {
            String hash = UserService.hashPassword("Test1234@");
            assertNotNull(hash);
            assertFalse(hash.isBlank());
        }

        @Test
        @DisplayName("Hash khác plaintext")
        void hashPassword_notEqualToPlaintext() {
            String plain = "Test1234@";
            String hash  = UserService.hashPassword(plain);
            assertNotEquals(plain, hash);
        }

        @Test
        @DisplayName("Hai lần hash cùng password → khác nhau (bcrypt dùng salt ngẫu nhiên)")
        void hashPassword_twiceGivesDifferentResult() {
            String hash1 = UserService.hashPassword("Test1234@");
            String hash2 = UserService.hashPassword("Test1234@");
            assertNotEquals(hash1, hash2);
        }

        @Test
        @DisplayName("BCrypt.checkpw xác nhận hash đúng")
        void hashPassword_canBeVerified() {
            String plain = "Test1234@";
            String hash  = UserService.hashPassword(plain);
            assertTrue(org.mindrot.jbcrypt.BCrypt.checkpw(plain, hash));
        }
    }
}