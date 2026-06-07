package org.auctionsystem.server.DAO;

import org.auctionsystem.model.entities.Admin;
import org.auctionsystem.model.entities.Bidder;
import org.auctionsystem.model.entities.Seller;
import org.auctionsystem.model.enums.UserRole;
import org.auctionsystem.server.Connectivity.DatabaseConnection;
import org.auctionsystem.model.entities.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UserDAO {

    // ─── HELPER — map ResultSet sang các Entity ───────────────────────────────

    private Seller mapSeller(ResultSet rs) throws SQLException {
        return new Seller(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("username"),
                rs.getString("password"),
                rs.getDouble("balance"),
                rs.getString("email"),
                rs.getString("phone"),
                UserRole.SELLER,
                rs.getObject("rating") != null ? rs.getDouble("rating") : null,
                rs.getInt("rating_count"),
                rs.getBoolean("is_active"),
                rs.getString("avatar_url") // Bổ sung vào constructor
        );
    }

    private Admin mapAdmin(ResultSet rs) throws SQLException {
        return new Admin(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("username"),
                rs.getString("password"),
                rs.getDouble("balance"),
                rs.getString("email"),
                rs.getString("phone"),
                UserRole.ADMIN,
                rs.getBoolean("is_active"),
                rs.getString("avatar_url") // Bổ sung vào constructor
        );
    }

    private Bidder mapBidder(ResultSet rs) throws SQLException {
        return new Bidder(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("username"),
                rs.getString("password"),
                rs.getDouble("balance"),
                rs.getString("email"),
                rs.getString("phone"),
                UserRole.BIDDER,
                rs.getBoolean("is_active"),
                rs.getString("avatar_url") // Bổ sung vào constructor
        );
    }

    private User mapUser(ResultSet rs) throws SQLException {
        String role = rs.getString("role");
        if (role.equalsIgnoreCase("seller"))      return mapSeller(rs);
        else if (role.equalsIgnoreCase("admin"))  return mapAdmin(rs);
        else                                       return mapBidder(rs);
    }

    // ─── ĐĂNG KÝ ──────────────────────────────────────────────────────────────

    public boolean registerUser(String name, String username, String hashedPassword,
                                String email, String phone, String role) {
        // Giữ nguyên avatar_url là NULL khi đăng ký như bạn mong muốn
        String sql = "INSERT INTO users (id, name, username, password, email, phone, role, balance, is_active, avatar_url) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, name);
            ps.setString(3, username);
            ps.setString(4, hashedPassword);
            ps.setString(5, email);
            ps.setString(6, phone);
            ps.setString(7, role.toLowerCase());
            ps.setDouble(8, 0.0);
            ps.setBoolean(9, true);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                throw new RuntimeException("DUPLICATE_KEY:" + e.getMessage(), e);
            }
            System.err.println("Lỗi đăng ký người dùng: " + e.getMessage());
            return false;
        }
    }

    // ─── KIỂM TRA TRÙNG ───────────────────────────────────────────────────────

    public boolean isUsernameExist(String username) {
        return existsBy("username", username);
    }

    public boolean isEmailExist(String email) {
        return existsBy("email", email);
    }

    public boolean isPhoneExist(String phone) {
        if (phone == null || phone.isBlank()) return false;
        return existsBy("phone", phone);
    }

    private boolean existsBy(String column, String value) {
        if (!column.equals("username") && !column.equals("email") && !column.equals("phone")) {
            throw new IllegalArgumentException("Cột không hợp lệ: " + column);
        }
        String sql = "SELECT COUNT(*) FROM users WHERE " + column + " = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi kiểm tra " + column + ": " + e.getMessage());
        }
        return false;
    }

    // ─── LẤY THÔNG TIN ────────────────────────────────────────────────────────

    private static final String SELECT_ALL =
            "SELECT id, name, username, password, balance, email, phone, " +
                    "role, rating, rating_count, is_active, avatar_url FROM users";

    public User getProfileById(String id) {
        String sql = SELECT_ALL + " WHERE id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return mapUser(rs);
        } catch (SQLException e) {
            System.err.println("Lỗi truy xuất thông tin người dùng: " + e.getMessage());
        }
        return null;
    }

    public User getProfileByUsername(String username) {
        String sql = SELECT_ALL + " WHERE username = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return mapUser(rs);
        } catch (SQLException e) {
            System.err.println("Lỗi truy xuất thông tin người dùng: " + e.getMessage());
        }
        return null;
    }

    /**
     * Lấy toàn bộ user đang hoạt động (is_active = true), loại trừ admin.
     */
    public List<User> getAllActiveUsers() {
        String sql = SELECT_ALL +
                " WHERE is_active = true AND role != 'admin'" +
                " ORDER BY role, name";
        List<User> results = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) results.add(mapUser(rs));
        } catch (SQLException e) {
            System.err.println("Lỗi lấy danh sách user: " + e.getMessage());
        }
        return results;
    }

    /**
     * Tìm kiếm user theo keyword (username, name, email chứa keyword).
     * Trả về tối đa 50 kết quả, loại bỏ admin.
     */
    public List<User> searchByKeyword(String keyword) {
        String pattern = "%" + keyword.toLowerCase() + "%";
        String sql = SELECT_ALL +
                " WHERE (LOWER(username) LIKE ? OR LOWER(name) LIKE ? OR LOWER(email) LIKE ?)" +
                "   AND role != 'admin'" +
                " ORDER BY username LIMIT 50";
        List<User> results = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, pattern);
            stmt.setString(2, pattern);
            stmt.setString(3, pattern);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) results.add(mapUser(rs));
        } catch (SQLException e) {
            System.err.println("Lỗi tìm kiếm người dùng: " + e.getMessage());
        }
        return results;
    }

    // ─── KIỂM TRA TRẠNG THÁI ──────────────────────────────────────────────────

    public boolean isActiveById(String userId, Connection conn) {
        String sql = "SELECT is_active FROM users WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getBoolean("is_active");
        } catch (SQLException e) {
            System.err.println("[UserDAO.isActiveById] Lỗi: " + e.getMessage());
            return false;
        }
    }

    // ─── CẬP NHẬT ─────────────────────────────────────────────────────────────

    public boolean updateBalance(String userId, double amount, Connection conn) {
        String sql = "UPDATE users SET balance = balance + ? WHERE id = ? AND balance + ? >= 0";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setString(2, userId);
            stmt.setDouble(3, amount);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Không thể cập nhật số dư: " + e.getMessage());
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

    /**
     * Cập nhật rating trung bình của seller sau khi bidder đánh giá lần đầu.
     * Công thức: rating_mới = (rating_cũ * rating_count + điểm_mới) / (rating_count + 1)
     */
    public boolean updateRatingInsert(String sellerId, double newRatingScore) {
        String sql = "UPDATE users " +
                "SET rating = (COALESCE(rating, 0) * rating_count + ?) / (rating_count + 1), " +
                "    rating_count = rating_count + 1 " +
                "WHERE id = ? AND role = 'seller'";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, newRatingScore);
            stmt.setString(2, sellerId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Lỗi cập nhật rating (insert): " + e.getMessage());
            return false;
        }
    }

    /**
     * Cập nhật rating trung bình khi bidder SỬA điểm đã đánh giá trước đó.
     * oldScore: điểm cũ của bidder này (để trừ ra khỏi tổng)
     * newScore: điểm mới
     * Công thức: rating_mới = (rating_cũ * rating_count - oldScore + newScore) / rating_count
     */
    public boolean updateRatingEdit(String sellerId, double oldScore, double newScore) {
        String sql = "UPDATE users " +
                "SET rating = GREATEST(1, LEAST(5, " +
                "    (COALESCE(rating, 0) * rating_count - ? + ?) / GREATEST(rating_count, 1))) " +
                "WHERE id = ? AND role = 'seller'";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, oldScore);
            stmt.setDouble(2, newScore);
            stmt.setString(3, sellerId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Lỗi cập nhật rating (edit): " + e.getMessage());
            return false;
        }
    }

    /**
     * Cập nhật toàn bộ thông tin profile bao gồm cả avatar_url.
     */
    public boolean updateProfile(String userId, String name, String email, String phone, String avatarUrl) {
        String sql = "UPDATE users SET name = ?, email = ?, phone = ?, avatar_url = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, phone);
            ps.setString(4, avatarUrl);
            ps.setString(5, userId);

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Lỗi cập nhật profile: " + e.getMessage());
            return false;
        }
    }
}