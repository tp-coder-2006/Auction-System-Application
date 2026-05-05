package org.auctionsystem.server.Repository;

import org.auctionsystem.server.Connectivity.DatabaseConnection;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class UserRepository {

    // Kiểm tra đăng nhập - trả về true nếu đúng, false nếu sai
    public boolean checkLogin(String username, String password) {
        String sql = "SELECT password FROM users WHERE username = ?";

        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);

            // Điền username vào dấu ? - tránh SQL Injection
            stmt.setString(1, username);

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                // Lấy mật khẩu đã mã hóa từ DB ra và so sánh bằng BCrypt
                String hashedPassword = rs.getString("password");
                return BCrypt.checkpw(password, hashedPassword);
            }

        } catch (Exception e) {
            System.err.println("❌ Lỗi khi kiểm tra đăng nhập: " + e.getMessage());
        }

        // Không tìm thấy username → đăng nhập thất bại
        return false;
    }
}
