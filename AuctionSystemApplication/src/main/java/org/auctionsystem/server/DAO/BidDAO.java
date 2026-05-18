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

    public ArrayList<Bid> getBidHistory(String bidderId){
        String sql = "SELECT * FROM bids WHERE bidder_id = ?";
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
                bid.setId(rs.getString("id"));
                bid.setBidTime(rs.getTimestamp("bid_time").toLocalDateTime());
                bids.add(bid);
            }
        }catch(Exception e){
            System.err.println("Lỗi hệ thống: "+e.getMessage());
        }
        return bids;
    }

    public ArrayList<Bid> getBidHistoryByItem(String bidderId, String itemId) {
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
}