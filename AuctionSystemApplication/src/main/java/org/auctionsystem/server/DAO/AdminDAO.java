package org.auctionsystem.server.DAO;

import org.auctionsystem.model.entities.Admin;
import org.auctionsystem.model.entities.Bidder;
import org.auctionsystem.model.entities.Seller;
import org.auctionsystem.model.entities.User;
import org.auctionsystem.model.enums.UserRole;
import org.auctionsystem.server.Connectivity.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AdminDAO {

    // ══════════════════════════════════════════════════════════════════════════
    //  SECTION 1 — QUẢN LÝ USER
    // ══════════════════════════════════════════════════════════════════════════

    // [CẬP NHẬT] Thêm avatar_url vào SELECT
    private static final String SELECT_USERS =
            "SELECT id, name, username, password, balance, email, phone, " +
                    "role, rating, rating_count, is_active, avatar_url FROM users";

    private User mapUser(ResultSet rs) throws SQLException {
        String role = rs.getString("role");
        String avatarUrl = rs.getString("avatar_url"); // Lấy avatar_url

        if ("seller".equalsIgnoreCase(role)) {
            // Khớp với Constructor Seller(12 tham số)
            return new Seller(
                    rs.getString("id"), rs.getString("name"), rs.getString("username"),
                    rs.getString("password"), rs.getDouble("balance"),
                    rs.getString("email"), rs.getString("phone"), UserRole.SELLER,
                    rs.getObject("rating") != null ? rs.getDouble("rating") : null,
                    rs.getInt("rating_count"), rs.getBoolean("is_active"),
                    avatarUrl
            );
        } else if ("admin".equalsIgnoreCase(role)) {
            // Khớp với Constructor Admin(10 tham số)
            return new Admin(
                    rs.getString("id"), rs.getString("name"), rs.getString("username"),
                    rs.getString("password"), rs.getDouble("balance"),
                    rs.getString("email"), rs.getString("phone"),
                    UserRole.ADMIN, rs.getBoolean("is_active"),
                    avatarUrl
            );
        } else {
            // Khớp với Constructor Bidder(10 tham số)
            return new Bidder(
                    rs.getString("id"), rs.getString("name"), rs.getString("username"),
                    rs.getString("password"), rs.getDouble("balance"),
                    rs.getString("email"), rs.getString("phone"),
                    UserRole.BIDDER, rs.getBoolean("is_active"),
                    avatarUrl
            );
        }
    }

    public List<User> getAllUsers() {
        String sql = SELECT_USERS + " ORDER BY role, name";
        List<User> users = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) users.add(mapUser(rs));
        } catch (SQLException e) {
            System.err.println("❌ [AdminDAO] Lỗi lấy danh sách user: " + e.getMessage());
        }
        return users;
    }

    public User getUserById(String userId) {
        String sql = SELECT_USERS + " WHERE id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return mapUser(rs);
        } catch (SQLException e) {
            System.err.println("❌ [AdminDAO] Lỗi lấy user: " + e.getMessage());
        }
        return null;
    }

    /**
     * Trả về user theo username, kể cả bị ban.
     * Dùng cho các tác vụ tìm kiếm nhanh của Admin.
     */
    public User getUserByUsername(String username) {
        // SELECT_USERS đã bao gồm avatar_url từ các bước trước
        String sql = SELECT_USERS + " WHERE username = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return mapUser(rs); // Sử dụng helper mapUser đã có avatar_url
            }
        } catch (SQLException e) {
            System.err.println("❌ [AdminDAO] Lỗi lấy user theo username: " + e.getMessage());
        }
        return null;
    }

    public boolean setActiveStatus(String userId, boolean isActive) {
        String sql = "UPDATE users SET is_active = ? WHERE id = ? AND role != 'admin'";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, isActive);
            stmt.setString(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ [AdminDAO] Lỗi ban/unban: " + e.getMessage());
            return false;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SECTION 2 — THỐNG KÊ USER (Ranking có thêm Avatar)
    // ══════════════════════════════════════════════════════════════════════════

    public UserStats getUserStats() {
        String sql =
                "SELECT " +
                        "  COUNT(*)                                                          AS total_users, " +
                        "  SUM(CASE WHEN role = 'seller'                       THEN 1 END)  AS total_sellers, " +
                        "  SUM(CASE WHEN role = 'bidder'                       THEN 1 END)  AS total_bidders, " +
                        "  SUM(CASE WHEN role != 'admin' AND is_active = 1     THEN 1 END)  AS active_users, " +
                        "  SUM(CASE WHEN role != 'admin' AND is_active = 0     THEN 1 END)  AS banned_users " +
                        "FROM users";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return new UserStats(
                        rs.getLong("total_users"), rs.getLong("total_sellers"),
                        rs.getLong("total_bidders"), rs.getLong("active_users"),
                        rs.getLong("banned_users")
                );
            }
        } catch (SQLException e) {
            System.err.println("❌ [AdminDAO] Lỗi thống kê user: " + e.getMessage());
        }
        return new UserStats(0, 0, 0, 0, 0);
    }

    public List<SellerRank> getTopSellers(int limit) {
        // [CẬP NHẬT] Lấy thêm u.avatar_url để hiển thị trong bảng xếp hạng
        String sql =
                "SELECT u.id, u.username, u.name, u.rating, u.avatar_url, " +
                        "       COUNT(h.id)       AS total_sold, " +
                        "       SUM(h.sold_price) AS total_revenue " +
                        "FROM item_ownership_history h " +
                        "JOIN users u ON u.id = h.seller_id " +
                        "GROUP BY u.id, u.username, u.name, u.rating, u.avatar_url " +
                        "ORDER BY total_revenue DESC " +
                        "LIMIT ?";

        List<SellerRank> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();
            int rank = 1;
            while (rs.next()) {
                result.add(new SellerRank(
                        rank++, rs.getString("id"), rs.getString("username"),
                        rs.getString("name"),
                        rs.getObject("rating") != null ? rs.getDouble("rating") : null,
                        rs.getInt("total_sold"), rs.getDouble("total_revenue"),
                        rs.getString("avatar_url") // Bổ sung vào Rank
                ));
            }
        } catch (SQLException e) {
            System.err.println("❌ [AdminDAO] Lỗi top sellers: " + e.getMessage());
        }
        return result;
    }

    public List<BidderRank> getTopBidders(int limit) {
        // [CẬP NHẬT] Lấy thêm u.avatar_url
        String sql =
                "SELECT u.id, u.username, u.name, u.avatar_url, " +
                        "       COUNT(DISTINCT b.id)    AS total_bids, " +
                        "       COUNT(DISTINCT h.id)    AS total_wins " +
                        "FROM users u " +
                        "JOIN bids b ON b.bidder_id = u.id " +
                        "LEFT JOIN item_ownership_history h ON h.buyer_id = u.id " +
                        "WHERE u.role = 'bidder' " +
                        "GROUP BY u.id, u.username, u.name, u.avatar_url " +
                        "ORDER BY total_bids DESC " +
                        "LIMIT ?";

        List<BidderRank> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();
            int rank = 1;
            while (rs.next()) {
                result.add(new BidderRank(
                        rank++, rs.getString("id"), rs.getString("username"),
                        rs.getString("name"), rs.getInt("total_bids"),
                        rs.getInt("total_wins"), rs.getString("avatar_url") // Bổ sung vào Rank
                ));
            }
        } catch (SQLException e) {
            System.err.println("❌ [AdminDAO] Lỗi top bidders: " + e.getMessage());
        }
        return result;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SECTION 3 & 4 (GIỮ NGUYÊN) — Thống kê Item và Giao dịch không bị ảnh hưởng
    // ══════════════════════════════════════════════════════════════════════════

    public ItemStats getItemStats() {
        String sql = "SELECT COUNT(*) AS total_items, SUM(CASE WHEN status = 'pending' THEN 1 END) AS pending_items, SUM(CASE WHEN status = 'active' THEN 1 END) AS active_items, SUM(CASE WHEN status = 'closed' THEN 1 END) AS closed_items, SUM(CASE WHEN status = 'cancelled' THEN 1 END) AS cancelled_items, SUM(CASE WHEN is_active = 0 THEN 1 END) AS hidden_items FROM items";
        try (Connection conn = DatabaseConnection.getInstance().getConnection(); PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return new ItemStats(rs.getLong("total_items"), rs.getLong("pending_items"), rs.getLong("active_items"), rs.getLong("closed_items"), rs.getLong("cancelled_items"), rs.getLong("hidden_items"));
        } catch (SQLException e) { System.err.println("Lỗi ItemStats: " + e.getMessage()); }
        return new ItemStats(0, 0, 0, 0, 0, 0);
    }

    public List<MonthCount> getItemCountByMonth(int months) {
        String sql = "SELECT DATE_FORMAT(start_time, '%Y-%m') AS month, COUNT(*) AS count FROM items WHERE start_time >= DATE_SUB(NOW(), INTERVAL ? MONTH) GROUP BY month ORDER BY month ASC";
        List<MonthCount> res = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) { stmt.setInt(1, months); ResultSet rs = stmt.executeQuery(); while (rs.next()) res.add(new MonthCount(rs.getString("month"), rs.getLong("count"))); } catch (SQLException e) { System.err.println("Lỗi MonthCount: " + e.getMessage()); }
        return res;
    }

    public TransactionStats getTransactionStats() {
        String sql = "SELECT COUNT(*) AS total_transactions, COALESCE(SUM(sold_price), 0) AS total_revenue, COALESCE(AVG(sold_price), 0) AS avg_sold_price, COALESCE(MAX(sold_price), 0) AS max_sold_price, COALESCE(MIN(sold_price), 0) AS min_sold_price FROM item_ownership_history";
        try (Connection conn = DatabaseConnection.getInstance().getConnection(); PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) { if (rs.next()) return new TransactionStats(rs.getLong("total_transactions"), rs.getDouble("total_revenue"), rs.getDouble("avg_sold_price"), rs.getDouble("max_sold_price"), rs.getDouble("min_sold_price")); } catch (SQLException e) { System.err.println("Lỗi TransStats: " + e.getMessage()); }
        return new TransactionStats(0, 0, 0, 0, 0);
    }

    public List<MonthRevenue> getRevenueByMonth(int months) {
        String sql = "SELECT DATE_FORMAT(sold_time, '%Y-%m') AS month, COUNT(*) AS transactions, SUM(sold_price) AS revenue FROM item_ownership_history WHERE sold_time >= DATE_SUB(NOW(), INTERVAL ? MONTH) GROUP BY month ORDER BY month ASC";
        List<MonthRevenue> res = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) { stmt.setInt(1, months); ResultSet rs = stmt.executeQuery(); while (rs.next()) res.add(new MonthRevenue(rs.getString("month"), rs.getLong("transactions"), rs.getDouble("revenue"))); } catch (SQLException e) { System.err.println("Lỗi RevenueByMonth: " + e.getMessage()); }
        return res;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  INNER CLASSES (Bổ sung avatarUrl vào Rank)
    // ══════════════════════════════════════════════════════════════════════════

    public static class SellerRank {
        public final int rank;
        public final String id, username, name, avatarUrl;
        public final Double rating;
        public final int totalSold;
        public final double totalRevenue;

        public SellerRank(int rank, String id, String username, String name, Double rating, int totalSold, double totalRevenue, String avatarUrl) {
            this.rank = rank; this.id = id; this.username = username; this.name = name;
            this.rating = rating; this.totalSold = totalSold; this.totalRevenue = totalRevenue;
            this.avatarUrl = avatarUrl;
        }
    }

    public static class BidderRank {
        public final int rank;
        public final String id, username, name, avatarUrl;
        public final int totalBids, totalWins;

        public BidderRank(int rank, String id, String username, String name, int totalBids, int totalWins, String avatarUrl) {
            this.rank = rank; this.id = id; this.username = username; this.name = name;
            this.totalBids = totalBids; this.totalWins = totalWins; this.avatarUrl = avatarUrl;
        }
    }

    // Các Inner Class khác (UserStats, ItemStats, TransStats, MonthCount, MonthRevenue) giữ nguyên...
    public static class UserStats { public final long totalUsers, totalSellers, totalBidders, activeUsers, bannedUsers; public UserStats(long t, long s, long b, long a, long ba) { this.totalUsers=t; this.totalSellers=s; this.totalBidders=b; this.activeUsers=a; this.bannedUsers=ba; } }
    public static class ItemStats { public final long totalItems, pendingItems, activeItems, closedItems, cancelledItems, hiddenItems; public ItemStats(long t, long p, long a, long c, long ca, long h) { this.totalItems=t; this.pendingItems=p; this.activeItems=a; this.closedItems=c; this.cancelledItems=ca; this.hiddenItems=h; } }
    public static class TransactionStats { public final long totalTransactions; public final double totalRevenue, avgSoldPrice, maxSoldPrice, minSoldPrice; public TransactionStats(long t, double r, double a, double m, double mi) { this.totalTransactions=t; this.totalRevenue=r; this.avgSoldPrice=a; this.maxSoldPrice=m; this.minSoldPrice=mi; } }
    public static class MonthCount { public final String month; public final long count; public MonthCount(String m, long c) { this.month=m; this.count=c; } }
    public static class MonthRevenue { public final String month; public final long transactions; public final double revenue; public MonthRevenue(String m, long t, double r) { this.month=m; this.transactions=t; this.revenue=r; } }
}