package org.auctionsystem.server.DAO;

import org.auctionsystem.model.entities.Admin;
import org.auctionsystem.model.entities.Bidder;
import org.auctionsystem.model.entities.Seller;
import org.auctionsystem.model.enums.UserRole;
import org.auctionsystem.server.Connectivity.DatabaseConnection;
import org.mindrot.jbcrypt.BCrypt;
import org.auctionsystem.model.entities.User;

import java.sql.*;
import java.util.UUID;

public class UserDAO {

    // ─── ĐĂNG KÝ ──────────────────────────────────────────────────────────────

    public boolean registerUser(String name, String username, String hashedPassword, String email, String role) {
        if (isUsernameExist(username)) {
            System.err.println("Lỗi: Tên đăng nhập đã tồn tại.");
            return false;
        }

        if (isEmailExist(email)) {
            System.err.println("Lỗi: Email đã tồn tại.");
            return false;
        }

        String sql = "INSERT INTO users (id, name, username, password, email, role, balance, is_active) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, java.util.UUID.randomUUID().toString()); // Tạo ID ngẫu nhiên
            ps.setString(2, name);
            ps.setString(3, username);
            ps.setString(4, hashedPassword);
            ps.setString(5, email);
            ps.setString(6, role.toLowerCase()); // Khớp với ENUM trong MySQL
            ps.setDouble(7, 0.0);                 // Balance mặc định
            ps.setBoolean(8, true);               // is_active mặc định

            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0; // Trả về true nếu chèn thành công ít nhất 1 dòng

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // ─── ĐĂNG NHẬP ────────────────────────────────────────────────────────────

    public User loginUser(String username, String password) {
        String sql = "SELECT id, name, username, password, balance, email, role, rating "
                + "FROM users WHERE username = ? AND is_active = 1";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String storedHash = rs.getString("password");
                if (BCrypt.checkpw(password, storedHash)) {
                    String roleDb = rs.getString("role");

                    if (roleDb.equalsIgnoreCase("seller")) {
                        return new Seller(
                                rs.getString("id"),
                                rs.getString("name"),
                                rs.getString("username"),
                                rs.getString("password"),
                                rs.getDouble("balance"),
                                rs.getString("email"),
                                UserRole.SELLER,
                                (Double) rs.getObject("rating")
                        );
                    } else if (roleDb.equalsIgnoreCase("admin")) {
                        return new Admin(
                                rs.getString("id"),
                                rs.getString("name"),
                                rs.getString("username"),
                                rs.getString("password"),
                                rs.getDouble("balance"),
                                rs.getString("email"),
                                UserRole.ADMIN
                        );
                    } else if (roleDb.equalsIgnoreCase("bidder")) {
                        return new Bidder(
                                rs.getString("id"),
                                rs.getString("name"),
                                rs.getString("username"),
                                rs.getString("password"),
                                rs.getDouble("balance"),
                                rs.getString("email"),
                                UserRole.BIDDER
                        );
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println("Lỗi đăng nhập: " + e.getMessage());
        }

        return null;
    }

    // ─── KIỂM TRA TRÙNG ───────────────────────────────────────────────────────

    public boolean isUsernameExist(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;

        } catch (SQLException e) {
            System.err.println("Lỗi kiểm tra username: " + e.getMessage());
        }
        return false;
    }

    public boolean isEmailExist(String email) {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;

        } catch (SQLException e) {
            System.err.println("Lỗi kiểm tra email: " + e.getMessage());
        }
        return false;
    }

    public User getUserById(String id) {
        String sql = "SELECT id, name, username, password, balance, email, role, rating FROM users WHERE id = ?";
        try(Connection conn = DatabaseConnection.getInstance().getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)){

            stmt.setString(1,id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String roleDb = rs.getString("role");
                if (roleDb.equalsIgnoreCase("seller")) {
                    return new Seller(
                            rs.getString("id"),
                            rs.getString("name"),
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getDouble("balance"),
                            rs.getString("email"),
                            UserRole.SELLER,
                            (Double) rs.getObject("rating")  // ← giữ nguyên NULL nếu chưa có rating
                    );

                } else if (roleDb.equalsIgnoreCase("admin")) {
                    return new Admin(
                            rs.getString("id"),
                            rs.getString("name"),
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getDouble("balance"),
                            rs.getString("email"),
                            UserRole.ADMIN
                    );

                } else if (roleDb.equalsIgnoreCase("bidder")) {
                    return new Bidder(
                            rs.getString("id"),
                            rs.getString("name"),
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getDouble("balance"),
                            rs.getString("email"),
                            UserRole.BIDDER
                    );
                }
            }
        }catch(SQLException e){
            System.err.println("Lỗi truy xuất thông tin người dùng: "+e.getMessage());
        }
        return null;
    }

    public boolean updateBalance(String userId, double amount, Connection conn){
        String sql = "UPDATE users SET balance = balance + ? WHERE id = ? AND balance + ? >= 0";

        try{
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setDouble(1, amount);
            stmt.setString(2, userId);
            stmt.setDouble(3, amount); // kiểm tra không âm
            return stmt.executeUpdate() > 0;
        }catch(SQLException e){
            System.err.println("Không thể cập nhật số dư: "+e.getMessage());
        }
        return false;
    }

    public boolean updatePassword(String userId, String hashedPassword) {
        String sql = "UPDATE users SET password = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, hashedPassword);
            stmt.setString(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Lỗi đổi mật khẩu: " + e.getMessage());
            return false;
        }
    }
}