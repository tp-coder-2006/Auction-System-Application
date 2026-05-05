package org.auctionsystem.server.Repository;

import org.auctionsystem.server.Connectivity.DatabaseConnection;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserRepository {

    /**
     * Kiểm tra đăng nhập.
     * Mỗi lần gọi tự mở và tự đóng connection riêng → an toàn đa luồng.
     */
    public boolean checkLogin(String username, String password) {
        String sql = "SELECT password FROM users WHERE username = ?";

        // try-with-resources: conn tự đóng khi ra khỏi khối try, dù thành công hay lỗi
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String hashedPassword = rs.getString("password");
                // BCrypt so sánh mật khẩu người dùng nhập với mật khẩu đã băm trong DB
                return BCrypt.checkpw(password, hashedPassword);
            }

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi kiểm tra đăng nhập: " + e.getMessage());
        }

        return false;
    }

    /**
     * Đăng ký tài khoản mới.
     * Mật khẩu được băm bằng BCrypt trước khi lưu vào DB.
     */
    public boolean registerUser(String username, String password, String email, String name) {
        // Kiểm tra username đã tồn tại chưa
        if (isUsernameTaken(username)) {
            return false;
        }

        String sql = "INSERT INTO users (id, name, username, password, balance, is_active, email) " +
                     "VALUES (UUID(), ?, ?, ?, 0, 1, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, name);
            stmt.setString(2, username);
            // Băm mật khẩu trước khi lưu - KHÔNG BAO GIỜ lưu mật khẩu thô
            stmt.setString(3, BCrypt.hashpw(password, BCrypt.gensalt()));
            stmt.setString(4, email);

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi đăng ký tài khoản: " + e.getMessage());
        }

        return false;
    }

    /**
     * Kiểm tra xem username đã có người dùng chưa.
     * Dùng nội bộ để tránh trùng tên khi đăng ký.
     */
    private boolean isUsernameTaken(String username) {
        String sql = "SELECT id FROM users WHERE username = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.next(); // Nếu tìm thấy dòng nào → username đã tồn tại

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi kiểm tra username: " + e.getMessage());
        }

        return false;
    }
}
