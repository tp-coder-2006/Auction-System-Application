package org.auctionsystem.server.Repository;

import org.auctionsystem.server.Connectivity.DatabaseConnection;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * BidRepository - Tầng truy cập dữ liệu cho bảng bids.
 *
 * Mỗi lần người dùng đặt giá thành công, một bản ghi sẽ được lưu vào đây.
 * BidRepository cũng phối hợp với ItemRepository để cập nhật giá cao nhất.
 */
public class BidRepository {

    private final ItemRepository itemRepository = new ItemRepository();

    /**
     * Lưu một lần đặt giá vào database, đồng thời cập nhật giá cao nhất của sản phẩm.
     *
     * Luồng xử lý:
     *   1. Kiểm tra giá mới có cao hơn giá hiện tại không
     *   2. Lưu bản ghi bid vào bảng bids
     *   3. Cập nhật current_highest_price trong bảng items
     *
     * @param bidderId  UUID của người đặt giá (từ bảng bidders)
     * @param itemId    UUID của sản phẩm
     * @param bidAmount Số tiền đặt giá
     * @return          true nếu đặt giá thành công, false nếu giá không hợp lệ hoặc có lỗi
     */
    public boolean saveBid(String bidderId, String itemId, double bidAmount) {
        // Bước 1: Kiểm tra giá mới có cao hơn giá hiện tại không
        JsonObject item = itemRepository.getItemById(itemId);
        if (item == null) {
            System.err.println("❌ Không tìm thấy sản phẩm: " + itemId);
            return false;
        }

        double currentPrice = item.get("currentPrice").getAsDouble();
        double startingPrice = item.get("startingPrice").getAsDouble();

        // Giá đặt phải cao hơn cả giá khởi điểm lẫn giá hiện tại
        double minimumRequired = Math.max(currentPrice, startingPrice);
        if (bidAmount <= minimumRequired) {
            System.err.println("❌ Giá đặt " + bidAmount + " không cao hơn giá hiện tại " + minimumRequired);
            return false;
        }

        // Bước 2: Lưu bản ghi bid vào bảng bids
        // UUID() là hàm MySQL tự tạo ID, NOW() lấy thời gian hiện tại của server
        String sql = "INSERT INTO bids (id, bid_amount, bid_time, bidder_id, item_id) " +
                "VALUES (UUID(), ?, NOW(), ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDouble(1, bidAmount);
            stmt.setString(2, bidderId);
            stmt.setString(3, itemId);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected == 0) return false;

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi lưu bid: " + e.getMessage());
            return false;
        }

        // Bước 3: Cập nhật giá cao nhất trong bảng items
        return itemRepository.updateCurrentPrice(itemId, bidAmount);
    }

    /**
     * Lấy lịch sử đặt giá của một người dùng.
     * Dùng cho trang "Lịch sử mua hàng" (Controller_Bidding_History).
     *
     * @param bidderId  UUID của người dùng cần xem lịch sử
     * @return          JsonArray chứa danh sách các lần đặt giá, sắp xếp mới nhất trước
     */
    public JsonArray getBidHistoryByBidder(String bidderId) {
        JsonArray history = new JsonArray();

        // JOIN với bảng items để lấy luôn tên sản phẩm, không cần query thêm lần nữa
        String sql = "SELECT b.id, b.bid_amount, b.bid_time, i.name AS item_name, i.status " +
                "FROM bids b " +
                "JOIN items i ON b.item_id = i.id " +
                "WHERE b.bidder_id = ? " +
                "ORDER BY b.bid_time DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, bidderId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                JsonObject bid = new JsonObject();
                bid.addProperty("bidId",     rs.getString("id"));
                bid.addProperty("amount",    rs.getDouble("bid_amount"));
                bid.addProperty("bidTime",   rs.getString("bid_time"));
                bid.addProperty("itemName",  rs.getString("item_name"));
                bid.addProperty("status",    rs.getString("status"));
                history.add(bid);
            }

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi lấy lịch sử đặt giá: " + e.getMessage());
        }

        return history;
    }
}
