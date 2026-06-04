package org.auctionsystem.server.DAO;

import org.auctionsystem.model.entities.Bid;
import org.auctionsystem.server.Connectivity.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;

public class BidDAO {
    public boolean insertBid(Connection conn, String itemId, String bidderId, LocalDateTime bidTime, double bidAmount) throws SQLException {
        String sql = "INSERT INTO bids (id, bid_amount, bid_time, bidder_id, item_id) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, java.util.UUID.randomUUID().toString());
            ps.setDouble(2, bidAmount);
            ps.setTimestamp(3, java.sql.Timestamp.valueOf(bidTime));
            ps.setString(4, bidderId);
            ps.setString(5, itemId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateItemPrice(Connection conn, String itemId, double newPrice) throws SQLException {
        String sql = "UPDATE items SET current_highest_price = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, newPrice);
            ps.setString(2, itemId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Anti-sniping: kéo dài end_time lên thêm {@code extendSeconds} giây,
     * nhưng chỉ khi end_time mới lớn hơn end_time hiện tại.
     * Chạy trong cùng connection/transaction với placeBid để tránh race condition.
     *
     * @param conn          connection đang trong transaction (đã lock row item)
     * @param itemId        id của item cần gia hạn
     * @param extendSeconds số giây cần cộng thêm vào end_time hiện tại
     * @return end_time mới sau khi gia hạn
     */
    public LocalDateTime extendEndTime(Connection conn, String itemId, int extendSeconds)
            throws SQLException {
        // Dùng TIMESTAMPADD để đảm bảo tính toán xảy ra hoàn toàn ở DB
        String sql = """
                UPDATE items
                   SET end_time = TIMESTAMPADD(SECOND, ?, end_time)
                 WHERE id = ?
                   AND status = 'active'
                   AND is_active = 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, extendSeconds);
            ps.setString(2, itemId);
            ps.executeUpdate();
        }
        // Đọc lại end_time mới để trả về cho caller (broadcast event)
        String selectSql = "SELECT end_time FROM items WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, itemId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getTimestamp("end_time").toLocalDateTime();
            }
        }
        throw new SQLException("Không đọc được end_time mới sau khi gia hạn cho item: " + itemId);
    }

    /**
     * Tổng bid_amount của các phiên ACTIVE mà bidderId đang dẫn đầu,
     * NGOẠI TRỪ item đang được bid (bid mới sẽ thay thế bid cũ trên item đó).
     * Chạy trong cùng connection/transaction để thấy uncommitted writes,
     * tránh race condition khi nhiều bid diễn ra đồng thời.
     */
    public double getTotalLeadingBids(Connection conn, String bidderId, String excludeItemId)
            throws SQLException {
        String sql = """
                SELECT COALESCE(SUM(b.bid_amount), 0)
                FROM bids b
                JOIN items i ON b.item_id = i.id
                WHERE b.bidder_id = ?
                  AND i.status = 'active'
                  AND i.is_active = 1
                  AND i.id != ?
                  AND b.bid_amount = (
                      SELECT MAX(b2.bid_amount)
                      FROM bids b2
                      WHERE b2.item_id = b.item_id
                  )
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bidderId);
            ps.setString(2, excludeItemId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getDouble(1) : 0.0;
        }
    }

    /**
     * Tất cả bid của 1 bidder (toàn bộ lịch sử, mọi item).
     */
    public ArrayList<Bid> getBidsByBidder(String bidderId) {
        String sql = "SELECT b.*, i.name AS item_name, i.status AS item_status, u.username AS seller_username " +
                "FROM bids b " +
                "LEFT JOIN items i ON b.item_id = i.id " +
                "LEFT JOIN users u ON i.seller_id = u.id " +
                "WHERE b.bidder_id = ?";
        ArrayList<Bid> bids = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bidderId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Bid bid = new Bid();
                bid.setBidderId(rs.getString("bidder_id"));
                bid.setBidAmount(rs.getDouble("bid_amount"));
                bid.setItemId(rs.getString("item_id"));
                bid.setItemName(rs.getString("item_name"));
                bid.setItemStatus(rs.getString("item_status"));
                bid.setSellerUsername(rs.getString("seller_username"));
                bid.setId(rs.getString("id"));
                bid.setBidTime(rs.getTimestamp("bid_time").toLocalDateTime());
                bids.add(bid);
            }
        } catch (Exception e) {
            System.err.println("Lỗi hệ thống: " + e.getMessage());
        }
        return bids;
    }

    /**
     * Bid của 1 bidder trên 1 item cụ thể (lịch sử đặt giá của bidder đó trong phòng đấu giá).
     */
    public ArrayList<Bid> getBidsByBidderAndItem(String bidderId, String itemId) {
        String sql = "SELECT * FROM bids WHERE bidder_id = ? AND item_id = ? ORDER BY bid_time DESC";
        ArrayList<Bid> bids = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bidderId);
            ps.setString(2, itemId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Bid bid = new Bid();
                bid.setId(rs.getString("id"));
                bid.setBidderId(rs.getString("bidder_id"));
                bid.setBidAmount(rs.getDouble("bid_amount"));
                bid.setItemId(rs.getString("item_id"));
                bid.setBidTime(rs.getTimestamp("bid_time").toLocalDateTime());
                bids.add(bid);
            }
        } catch (Exception e) {
            System.err.println("Lỗi hệ thống: " + e.getMessage());
        }
        return bids;
    }

    /**
     * Bid cao nhất của 1 item.
     */
    public Bid getHighestBid(String itemId) {
        String sql = "SELECT * FROM bids WHERE item_id = ? ORDER BY bid_amount DESC LIMIT 1";
        Bid bid = new Bid();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                bid.setId(rs.getString("id"));
                bid.setBidderId(rs.getString("bidder_id"));
                bid.setBidAmount(rs.getDouble("bid_amount"));
                bid.setItemId(rs.getString("item_id"));
                bid.setBidTime(rs.getTimestamp("bid_time").toLocalDateTime());
            } else {
                System.err.println("Không tìm thấy bid nào cho item này!");
                return null;
            }
        } catch (Exception e) {
            System.err.println("Lỗi hệ thống: " + e.getMessage());
            return null;
        }
        return bid;
    }

    /**
     * Các item ACTIVE mà bidder đang dẫn đầu (bid cao nhất là của bidder đó).
     */
    public ArrayList<Bid> getActiveBidsByBidder(String bidderId) {
        String sql = "SELECT b.id, b.bidder_id, b.item_id, b.bid_amount, b.bid_time, " +
                "i.name AS item_name, i.end_time AS item_end_time " +
                "FROM bids b " +
                "JOIN items i ON b.item_id = i.id " +
                "WHERE i.status = 'active' AND i.is_active = 1 " +
                "AND b.bidder_id = ? " +
                "AND b.bid_amount = (SELECT MAX(b2.bid_amount) FROM bids b2 WHERE b2.item_id = b.item_id AND b2.bidder_id = ?) " +
                "AND b.bid_amount = (SELECT MAX(b3.bid_amount) FROM bids b3 WHERE b3.item_id = b.item_id) " +
                "ORDER BY i.end_time ASC";
        ArrayList<Bid> bids = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bidderId);
            ps.setString(2, bidderId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Bid bid = new Bid();
                bid.setId(rs.getString("id"));
                bid.setBidderId(rs.getString("bidder_id"));
                bid.setBidAmount(rs.getDouble("bid_amount"));
                bid.setItemId(rs.getString("item_id"));
                bid.setBidTime(rs.getTimestamp("bid_time").toLocalDateTime());
                bid.setItemName(rs.getString("item_name"));
                bid.setItemEndTime(rs.getTimestamp("item_end_time").toLocalDateTime());
                bids.add(bid);
            }
        } catch (Exception e) {
            System.err.println("Lỗi hệ thống: " + e.getMessage());
        }
        return bids;
    }

    /**
     * Tất cả bid của 1 item không phân biệt phiên (toàn bộ lịch sử đặt giá).
     */
    /**
     * Toàn bộ bid của item qua mọi phiên, JOIN users để lấy username.
     */
    public ArrayList<Bid> getAllBidsByItem(String itemId) {
        String sql = "SELECT b.*, u.username AS bidder_name " +
                "FROM bids b " +
                "JOIN users u ON b.bidder_id = u.id " +
                "WHERE b.item_id = ? " +
                "ORDER BY b.bid_time DESC";
        ArrayList<Bid> bids = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Bid bid = new Bid();
                bid.setId(rs.getString("id"));
                bid.setBidderId(rs.getString("bidder_id"));
                bid.setBidderName(rs.getString("bidder_name"));
                bid.setBidAmount(rs.getDouble("bid_amount"));
                bid.setItemId(rs.getString("item_id"));
                bid.setBidTime(rs.getTimestamp("bid_time").toLocalDateTime());
                bids.add(bid);
            }
        } catch (Exception e) {
            System.err.println("Lỗi hệ thống: " + e.getMessage());
        }
        return bids;
    }

    /**
     * Tất cả bid được đặt trong phiên active HIỆN TẠI của item.
     * Lọc theo start_time – end_time của item khi status = 'active',
     * đảm bảo không lẫn bid từ các phiên active cũ trước đó.
     */
    /**
     * Bid trong phiên hiện tại (hoặc phiên vừa kết thúc) của item.
     * Lọc bid_time nằm trong khoảng start_time..end_time của item
     * — không phụ thuộc vào status nên hoạt động đúng cả khi ENDED.
     * JOIN users để lấy username hiển thị thay vì user id.
     */
    public ArrayList<Bid> getActiveBidsByItem(String itemId) {
        String sql = "SELECT b.*, u.username AS bidder_name " +
                "FROM bids b " +
                "JOIN items i ON b.item_id = i.id " +
                "JOIN users u ON b.bidder_id = u.id " +
                "WHERE b.item_id = ? " +
                "AND b.bid_time >= i.start_time " +
                "AND b.bid_time <= i.end_time " +
                "ORDER BY b.bid_time ASC";
        ArrayList<Bid> bids = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Bid bid = new Bid();
                bid.setId(rs.getString("id"));
                bid.setBidderId(rs.getString("bidder_id"));
                bid.setBidderName(rs.getString("bidder_name"));
                bid.setBidAmount(rs.getDouble("bid_amount"));
                bid.setItemId(rs.getString("item_id"));
                bid.setBidTime(rs.getTimestamp("bid_time").toLocalDateTime());
                bids.add(bid);
            }
        } catch (Exception e) {
            System.err.println("Lỗi hệ thống: " + e.getMessage());
        }
        return bids;
    }

    // ─── THÊM VÀO BidDAO.java ─────────────────────────────────────────────────────
    // Dán method này vào class BidDAO, bên cạnh các method hiện có.

    /**
     * Rà soát tất cả các item mà bidder đã từng đặt bid,
     * trả về bid cao nhất của bidder trên mỗi item cùng trạng thái kết quả:
     * <ul>
     *   <li><b>ongoing</b>  – item đang active, bidder đang dẫn đầu</li>
     *   <li><b>losing</b>   – item đang active, bidder KHÔNG dẫn đầu</li>
     *   <li><b>won</b>      – item đã closed, bidder là người thắng (bid cao nhất)</li>
     *   <li><b>lost</b>     – item đã closed, bidder không thắng</li>
     *   <li><b>cancelled</b>– item bị cancelled</li>
     * </ul>
     * Mỗi item chỉ xuất hiện 1 lần (bid cao nhất của bidder trên item đó).
     *
     * @param bidderId id của bidder cần tra cứu
     * @return danh sách Bid với itemName, itemStatus, bidResultStatus đã được set
     */
    public ArrayList<Bid> getBidResultsByBidder(String bidderId) {
        // Lấy bid cao nhất của bidder trên mỗi item, kèm thông tin item và bid cao nhất tổng thể của item
        String sql = """
                SELECT
                    b.id,
                    b.bidder_id,
                    b.item_id,
                    b.bid_amount,
                    b.bid_time,
                    i.name        AS item_name,
                    i.status      AS item_status,
                    i.end_time    AS item_end_time,
                    (SELECT MAX(b2.bid_amount) FROM bids b2 WHERE b2.item_id = b.item_id) AS item_highest_bid
                FROM bids b
                JOIN items i  ON b.item_id = i.id
                WHERE b.bidder_id = ?
                  AND b.bid_amount = (
                      SELECT MAX(b3.bid_amount)
                      FROM bids b3
                      WHERE b3.item_id = b.item_id
                        AND b3.bidder_id = ?
                  )
                ORDER BY b.bid_time DESC
                """;
        ArrayList<Bid> results = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bidderId);
            ps.setString(2, bidderId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Bid bid = new Bid();
                bid.setId(rs.getString("id"));
                bid.setBidderId(rs.getString("bidder_id"));
                bid.setItemId(rs.getString("item_id"));
                bid.setBidAmount(rs.getDouble("bid_amount"));
                bid.setBidTime(rs.getTimestamp("bid_time").toLocalDateTime());
                bid.setItemName(rs.getString("item_name"));

                String itemStatus    = rs.getString("item_status");
                double myHighestBid  = rs.getDouble("bid_amount");
                double itemHighest   = rs.getDouble("item_highest_bid");
                boolean iAmLeading   = myHighestBid >= itemHighest;

                String resultStatus;
                if ("cancelled".equalsIgnoreCase(itemStatus)) {
                    resultStatus = "cancelled";
                } else if ("active".equalsIgnoreCase(itemStatus)) {
                    resultStatus = iAmLeading ? "ongoing" : "losing";
                } else {
                    // closed / ended
                    resultStatus = iAmLeading ? "won" : "lost";
                }

                bid.setItemStatus(resultStatus);
                results.add(bid);
            }
        } catch (Exception e) {
            System.err.println("Lỗi getBidResultsByBidder: " + e.getMessage());
        }
        return results;
    }

    /**
     * Kiểm tra xem item_id đã từng có ít nhất 1 bid chưa.
     * Dùng để phân biệt hard delete / soft delete trong ItemService.
     *
     * @param itemId id của item cần kiểm tra
     * @return true nếu tồn tại ít nhất 1 bid; false nếu chưa có bid nào hoặc lỗi
     */
    public boolean hasBidForItem(String itemId) {
        String sql = "SELECT 1 FROM bids WHERE item_id = ? LIMIT 1";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.err.println("❌ Lỗi kiểm tra bid của item: " + e.getMessage());
            // An toàn: nếu lỗi, giả định đã có bid → chỉ cho soft delete
            return true;
        }
    }
}