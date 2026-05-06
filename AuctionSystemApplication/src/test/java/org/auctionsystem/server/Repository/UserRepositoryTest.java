package org.auctionsystem.server.Repository;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Test cho UserRepository.
 *
 * Lưu ý quan trọng: các test này cần kết nối database thật để chạy.
 * Trước khi chạy "mvn test", hãy đảm bảo:
 *   1. MySQL đang chạy trên máy
 *   2. DatabaseConnection.java trỏ đúng host/user/password của máy bạn
 *   3. Database "mydb" đã được khởi tạo bằng init_database.sql
 *
 * Chạy test bằng lệnh: mvn test
 */
class UserRepositoryTest {

    private final UserRepository userRepo = new UserRepository();

    // =========================================================
    // TEST checkLogin
    // =========================================================

    @Test
    void checkLogin_WrongUsername_ShouldReturnFalse() {
        // Username không tồn tại → phải trả về false
        boolean result = userRepo.checkLogin("nguoi_dung_khong_ton_tai", "matkhau123");
        assertFalse(result, "Username không tồn tại phải trả về false");
    }

    @Test
    void checkLogin_EmptyUsername_ShouldReturnFalse() {
        // Username rỗng → phải trả về false, không được crash
        boolean result = userRepo.checkLogin("", "matkhau123");
        assertFalse(result, "Username rỗng phải trả về false");
    }

    // =========================================================
    // TEST registerUser
    // =========================================================

    @Test
    void registerUser_DuplicateUsername_ShouldReturnFalse() {
        // Đăng ký lần đầu → thành công
        String uniqueUsername = "test_user_" + System.currentTimeMillis();
        boolean firstRegister = userRepo.registerUser(uniqueUsername, "matkhau123", "test@test.com", "Test User");
        assertTrue(firstRegister, "Đăng ký lần đầu phải thành công");

        // Đăng ký lần hai cùng username → phải thất bại
        boolean secondRegister = userRepo.registerUser(uniqueUsername, "matkhau456", "test2@test.com", "Test User 2");
        assertFalse(secondRegister, "Username trùng phải trả về false");
    }

    @Test
    void registerUser_ValidData_ShouldReturnTrue() {
        // Dùng timestamp để tạo username duy nhất mỗi lần chạy test
        String uniqueUsername = "junit_test_" + System.currentTimeMillis();
        boolean result = userRepo.registerUser(uniqueUsername, "matkhau123", "junit@test.com", "JUnit Test User");
        assertTrue(result, "Đăng ký với dữ liệu hợp lệ phải thành công");
    }

    // =========================================================
    // TEST isUsernameTaken (gián tiếp qua registerUser)
    // =========================================================

    @Test
    void registerThenLogin_ShouldWorkEndToEnd() {
        // Test luồng hoàn chỉnh: đăng ký xong thì đăng nhập được ngay
        String username = "e2e_test_" + System.currentTimeMillis();
        String password = "matkhau123";

        boolean registered = userRepo.registerUser(username, password, "e2e@test.com", "E2E Test");
        assertTrue(registered, "Đăng ký phải thành công");

        // Đăng nhập với đúng thông tin vừa đăng ký → phải thành công
        boolean loggedIn = userRepo.checkLogin(username, password);
        assertTrue(loggedIn, "Đăng nhập sau đăng ký phải thành công");
    }
}
