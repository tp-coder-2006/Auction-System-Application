package org.auctionsystem.server.DAO;

import org.auctionsystem.server.Connectivity.DatabaseConnection;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;
import java.util.UUID;

public class UserDAO {


    // ĐĂNG KÝ tài khoản mới
    // Trả về true nếu thành công, false nếu thất bại

    public boolean registerUser(String name, String username, String password,
                                String email, String role) {
        String sql = "INSERT INTO users (id, name, username, password, balance, is_active, email, role, rating) "
                + "VALUES (?, ?, ?, ?, 0, 1, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
            Double rating = role.equals("seller") ? 0.0 : null;

            stmt.setString(1, UUID.randomUUID().toString());
            stmt.setString(2, name);
            stmt.setString(3, username);
            stmt.setString(4, hashedPassword);
            stmt.setString(5, email);
            stmt.setString(6, role);
            if (rating == null) {
                stmt.setNull(7, Types.DOUBLE);
            } else {
                stmt.setDouble(7, rating);
            }

            stmt.executeUpdate();
            return true;

        } catch (SQLIntegrityConstraintViolationException e) {
            System.err.println("Username hoặc email đã tồn tại: " + e.getMessage());
            return false;
        } catch (SQLException e) {
            System.err.println("Lỗi đăng ký: " + e.getMessage());
            return false;
        }
    }


    // ĐĂNG NHẬP — trả về UserInfo nếu đúng, null nếu sai

    public UserInfo loginUser(String username, String password) {
        String sql = "SELECT id, name, username, password, balance, email, role, rating "
                + "FROM users WHERE username = ? AND is_active = 1";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String storedHash = rs.getString("password");

                // Kiểm tra mật khẩu bằng BCrypt
                if (BCrypt.checkpw(password, storedHash)) {
                    return new UserInfo(
                            rs.getString("id"),
                            rs.getString("name"),
                            rs.getString("username"),
                            rs.getDouble("balance"),
                            rs.getString("email"),
                            rs.getString("role"),
                            (Double) rs.getObject("rating")  // có thể NULL
                    );
                }
            }

        } catch (SQLException e) {
            System.err.println("Lỗi đăng nhập: " + e.getMessage());
        }

        return null; // Sai username hoặc password
    }


    // KIỂM TRA username đã tồn tại chưa (để dùng khi đăng ký)

    public boolean isUsernameExist(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;

        } catch (SQLException e) {
            System.err.println("Lỗi kiểm tra username: " + e.getMessage());
        }

        return false;
    }


    // KIỂM TRA email đã tồn tại chưa (dùng khi đăng ký)

    public boolean isEmailExist(String email) {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;

        } catch (SQLException e) {
            System.err.println("Lỗi kiểm tra email: " + e.getMessage());
        }

        return false;
    }


    // CLASS NỘI BỘ — Tuấn dùng cái này để truyền dữ liệu lên Controller

    public static class UserInfo {
        public final String id;
        public final String name;
        public final String username;
        public final double balance;
        public final String email;
        public final String role;      // "bidder" | "seller" | "admin"
        public final Double rating;    // NULL nếu không phải seller

        public UserInfo(String id, String name, String username,
                        double balance, String email, String role, Double rating) {
            this.id       = id;
            this.name     = name;
            this.username = username;
            this.balance  = balance;
            this.email    = email;
            this.role     = role;
            this.rating   = rating;
        }

        public boolean isBidder() { return "bidder".equals(role); }
        public boolean isSeller() { return "seller".equals(role); }
        public boolean isAdmin()  { return "admin".equals(role);  }
    }
}