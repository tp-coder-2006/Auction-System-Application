package org.auctionsystem.server.DAO;

import org.auctionsystem.server.Connectivity.DatabaseConnection;

import java.sql.*;
import java.util.UUID;

/**
 * DAO cho bảng seller_ratings.
 * Mỗi cặp (bidder_id, seller_id) chỉ có tối đa 1 dòng —
 * bidder có thể sửa điểm nhưng không thể tạo thêm đánh giá mới.
 */
public class RatingDAO {

    /**
     * Kiểm tra bidder đã đánh giá seller này chưa.
     * @return điểm đã đánh giá (1–5), hoặc -1 nếu chưa đánh giá lần nào.
     */
    public int getExistingRating(String bidderId, String sellerId) {
        String sql = "SELECT rating_score FROM seller_ratings WHERE bidder_id = ? AND seller_id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bidderId);
            ps.setString(2, sellerId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("rating_score");
        } catch (SQLException e) {
            System.err.println("❌ [RatingDAO] getExistingRating lỗi: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Ghi mới 1 đánh giá (lần đầu tiên bidder đánh giá seller này).
     */
    public boolean insertRating(String bidderId, String sellerId, int score) {
        String sql = "INSERT INTO seller_ratings (id, bidder_id, seller_id, rating_score) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, bidderId);
            ps.setString(3, sellerId);
            ps.setInt(4, score);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ [RatingDAO] insertRating lỗi: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cập nhật điểm đánh giá đã có (bidder sửa điểm cũ).
     */
    public boolean updateRatingScore(String bidderId, String sellerId, int newScore) {
        String sql = "UPDATE seller_ratings SET rating_score = ?, updated_at = NOW() " +
                "WHERE bidder_id = ? AND seller_id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newScore);
            ps.setString(2, bidderId);
            ps.setString(3, sellerId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ [RatingDAO] updateRatingScore lỗi: " + e.getMessage());
            return false;
        }
    }
}
