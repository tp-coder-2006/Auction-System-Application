package org.auctionsystem.server.DAO;

import org.auctionsystem.model.entities.Item;
import org.auctionsystem.model.enums.ItemStatus;
import org.auctionsystem.server.Connectivity.DatabaseConnection;
import org.auctionsystem.server.service.ImageService;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.UUID;

public class ItemDAO {

    // ─── HELPER — map ResultSet sang Item ─────────────────────────────────────

    private Item mapRow(ResultSet rs) throws SQLException {
        Item item = new Item();
        item.setId(rs.getString("id"));
        item.setName(rs.getString("name"));
        item.setDescription(rs.getString("description"));
        item.setStartingPrice(rs.getDouble("starting_price"));
        item.setCurrentHighestPrice((Double) rs.getObject("current_highest_price"));
        item.setStartTime(rs.getTimestamp("start_time").toLocalDateTime());
        item.setEndTime(rs.getTimestamp("end_time").toLocalDateTime());
        item.setStatus(ItemStatus.valueOf(rs.getString("status").toUpperCase()));
        item.setActive(rs.getBoolean("is_active"));
        item.setSellerId(rs.getString("seller_id"));
        try { item.setSellerUsername(rs.getString("seller_username")); } catch (Exception ignored) {}
        try { item.setOwnerUsername(rs.getString("owner_username"));   } catch (Exception ignored) {}
        item.setOwnerId(rs.getString("owner_id"));
        item.setImageUrl(rs.getString("image_url"));
        return item;
    }

    // [SỬA] Thêm is_active và image_url vào SELECT — tất cả query đều lọc is_active = 1
    private static final String SELECT_COLUMNS =
            "SELECT i.id, i.name, i.description, i.starting_price, i.current_highest_price, " +
                    "i.start_time, i.end_time, i.status, i.is_active, i.seller_id, i.owner_id, i.image_url, " +
                    "u.username AS seller_username, o.username AS owner_username " +
                    "FROM items i " +
                    "LEFT JOIN users u ON i.seller_id = u.id " +
                    "LEFT JOIN users o ON i.owner_id  = o.id";

    // ─── THÊM SẢN PHẨM ────────────────────────────────────────────────────────

    public String addItem(String name, String description, double startingPrice,
                          String startTime, String endTime, String sellerId, String imageUrl) {
        // [CẬP NHẬT] image_url được nhận từ tham số thay vì mặc định NULL
        String sql = "INSERT INTO items "
                + "(id, name, description, starting_price, current_highest_price, "
                + "start_time, end_time, status, is_active, seller_id, owner_id, image_url) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'pending', 1, ?, ?, ?)";

        String itemId = UUID.randomUUID().toString();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, itemId);
            stmt.setString(2, name);
            stmt.setString(3, description);
            stmt.setDouble(4, startingPrice);
            stmt.setNull(5, Types.DOUBLE);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            stmt.setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse(startTime, fmt)));
            stmt.setTimestamp(7, Timestamp.valueOf(LocalDateTime.parse(endTime, fmt)));
            stmt.setString(8, sellerId);
            stmt.setString(9, sellerId);  // owner_id ban đầu = seller_id
            stmt.setString(10, imageUrl); // Có thể truyền NULL nếu chưa có ảnh

            return stmt.executeUpdate() > 0 ? itemId : null;

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi thêm sản phẩm: " + e.getMessage());
            return null;
        }
    }

    // ─── SỬA SẢN PHẨM ─────────────────────────────────────────────────────────

    public boolean updateItem(String itemId, String name, String description,
                              double startingPrice, String startTime, String endTime,
                              String sellerId, String imageUrl) {
        // [CẬP NHẬT] Thêm image_url vào câu lệnh UPDATE
        String sql = "UPDATE items "
                + "SET name = ?, description = ?, starting_price = ?, "
                + "current_highest_price = NULL, start_time = ?, end_time = ?, image_url = ? "
                + "WHERE id = ? AND seller_id = ? AND status = 'pending' AND is_active = 1";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, name);
            stmt.setString(2, description);
            stmt.setDouble(3, startingPrice);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            stmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.parse(startTime, fmt)));
            stmt.setTimestamp(5, Timestamp.valueOf(LocalDateTime.parse(endTime, fmt)));
            stmt.setString(6, imageUrl);
            stmt.setString(7, itemId);
            stmt.setString(8, sellerId);

            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi sửa sản phẩm: " + e.getMessage());
            return false;
        }
    }

    // ─── CẬP NHẬT IMAGE_URL SAU KHI UPLOAD ẢNH ────────────────────────────────

    /**
     * Cập nhật image_url cho item sau khi ảnh đã được ghi thành công.
     * Dùng trong luồng addItem: INSERT item (image_url=null) → upload ảnh → gọi hàm này.
     */
    public boolean updateImageUrl(String itemId, String imageUrl) {
        String sql = "UPDATE items SET image_url = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, imageUrl);
            stmt.setString(2, itemId);
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi cập nhật image_url: " + e.getMessage());
            return false;
        }
    }

    // ─── HARD DELETE — chỉ khi status = 'pending' VÀ chưa từng có bid ────────
    //
    // Điều kiện:
    //   1. status = 'pending'  (item chưa bao giờ được kích hoạt đấu giá)
    //   2. is_active = 1       (chưa bị soft delete)
    //   3. Không có row nào trong bảng bids trỏ đến item này (kiểm tra ở tầng Service)
    //
    // Lưu ý: việc check "chưa từng có bid" được thực hiện trong ItemService
    // (gọi BidDAO.hasBidForItem) trước khi gọi method này, vì ItemDAO không nên
    // phụ thuộc trực tiếp vào BidDAO.
    public boolean hardDeleteItem(String itemId, String sellerId) {
        String sql = "DELETE FROM items "
                + "WHERE id = ? AND seller_id = ? AND status = 'pending' AND is_active = 1";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, itemId);
            stmt.setString(2, sellerId);

            int rows = stmt.executeUpdate();
            if (rows == 0)
                System.err.println("⚠️ Không thể xóa cứng: chỉ được xóa item ở trạng thái pending chưa có bid.");
            return rows > 0;

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi xóa cứng sản phẩm: " + e.getMessage());
            return false;
        }
    }

    // ─── SOFT DELETE — khi item đã từng có bid (mọi status cho phép) ─────────
    //
    // Điều kiện (áp dụng cho các status hợp lệ để ẩn):
    //   - status IN ('pending', 'cancelled', 'closed')  → đã kết thúc vòng đời
    //   - status = 'active' KHÔNG được phép (đang đấu giá)
    //   - is_active = 1 (chưa bị ẩn trước đó)
    //
    // Seller chỉ được soft delete item của chính mình.
    public boolean softDeleteItem(String itemId, String sellerId) {
        String sql = "UPDATE items SET is_active = 0 "
                + "WHERE id = ? AND seller_id = ? AND is_active = 1 "
                + "AND status IN ('pending', 'cancelled', 'closed')";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, itemId);
            stmt.setString(2, sellerId);

            int rows = stmt.executeUpdate();
            if (rows == 0)
                System.err.println("⚠️ Không thể xóa mềm: item đang active hoặc không thuộc quyền sở hữu.");
            return rows > 0;

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi xóa mềm sản phẩm: " + e.getMessage());
            return false;
        }
    }


    // ─── HỦY SẢN PHẨM ─────────────────────────────────────────────────────────

    public boolean cancelItem(String itemId, String sellerId) {
        String sql = "UPDATE items SET status = 'cancelled' "
                + "WHERE id = ? AND seller_id = ? AND is_active = 1 " // [MỚI] thêm is_active
                + "AND (status = 'pending' OR (status = 'active' AND current_highest_price IS NULL))";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, itemId);
            stmt.setString(2, sellerId);

            int rows = stmt.executeUpdate();
            if (rows == 0)
                System.err.println("⚠️ Không thể hủy: đã có người đặt giá hoặc không thuộc quyền sở hữu.");
            return rows > 0;

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi hủy sản phẩm: " + e.getMessage());
            return false;
        }
    }

    public boolean cancelItem(String itemId) {
        String sql = "UPDATE items SET status = 'cancelled' WHERE id = ? AND is_active = 1"; // [MỚI]

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, itemId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi hủy sản phẩm: " + e.getMessage());
            return false;
        }
    }

    // ─── CẬP NHẬT OWNER SAU KHI THANH TOÁN ───────────────────────────────────

    public boolean updateOwner(String itemId, String newOwnerId) {
        String sql = "UPDATE items SET owner_id = ?, status = 'closed' WHERE id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newOwnerId);
            ps.setString(2, itemId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Lỗi cập nhật owner: " + e.getMessage());
            return false;
        }
    }

    /**
     * Overload nhận Connection ngoài — dùng khi cần tham gia vào transaction đang mở.
     */
    public boolean updateOwner(Connection conn, String itemId, String newOwnerId) {
        String sql = "UPDATE items SET owner_id = ?, status = 'closed' WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newOwnerId);
            ps.setString(2, itemId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Lỗi cập nhật owner (conn): " + e.getMessage());
            return false;
        }
    }

    // ─── TÁI KHỞI ĐỘNG ĐẤU GIÁ ───────────────────────────────────────────────

    public boolean restartItemAuction(String itemId, String requesterId,
                                      double newStartingPrice,
                                      String newStartTime, String newEndTime) {
        // Giữ nguyên image_url cũ khi tái đấu giá (thường người dùng không đổi ảnh khi đăng lại)
        String checkSql  = "SELECT owner_id FROM items WHERE id = ? AND status IN ('closed', 'cancelled') AND is_active = 1";
        String updateSql = "UPDATE items SET "
                + "seller_id = ?, owner_id = ?, "
                + "starting_price = ?, current_highest_price = NULL, "
                + "start_time = ?, end_time = ?, status = 'pending' "
                + "WHERE id = ? AND owner_id = ? AND status IN ('closed', 'cancelled') AND is_active = 1";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {

            checkStmt.setString(1, itemId);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                String ownerId = rs.getString("owner_id");
                if (ownerId.equals(requesterId)) {
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setString(1, ownerId);
                        updateStmt.setString(2, ownerId);
                        updateStmt.setDouble(3, newStartingPrice);
                        DateTimeFormatter fmtR = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        updateStmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.parse(newStartTime, fmtR)));
                        updateStmt.setTimestamp(5, Timestamp.valueOf(LocalDateTime.parse(newEndTime, fmtR)));
                        updateStmt.setString(6, itemId);
                        updateStmt.setString(7, ownerId);
                        return updateStmt.executeUpdate() > 0;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Lỗi tái khởi động đấu giá: " + e.getMessage());
        }
        return false;
    }

    // ─── CẬP NHẬT TRẠNG THÁI TỰ ĐỘNG ─────────────────────────────────────────

    public boolean autoUpdateItemStatuses() {
        // [MỚI] Thêm is_active = 1 để không cập nhật item đã soft delete
        String sqlActive = "UPDATE items SET status = 'active' WHERE status = 'pending' AND start_time <= NOW() AND is_active = 1";
        String sqlClosed = "UPDATE items SET status = 'closed' WHERE status = 'active' AND end_time <= NOW() AND is_active = 1";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false);
            int total = stmt.executeUpdate(sqlActive);
            total    += stmt.executeUpdate(sqlClosed);
            conn.commit();
            return total > 0;
        } catch (SQLException e) {
            System.err.println("❌ Lỗi tự động cập nhật trạng thái: " + e.getMessage());
            return false;
        }
    }

    // ─── TRUY VẤN ─────────────────────────────────────────────────────────────

    public Item getAItemById(String itemId) {
        // [MỚI] Thêm is_active = 1 — không trả về item đã soft delete
        String sql = SELECT_COLUMNS + " WHERE i.id = ? AND i.is_active = 1";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi lấy item: " + e.getMessage());
        }
        return null;
    }

    public ArrayList<Item> getItemsBySeller(String sellerId) {
        String sql = SELECT_COLUMNS + " WHERE i.seller_id = ? AND i.is_active = 1 ORDER BY i.end_time DESC";
        return queryList(sql, sellerId);
    }

    public ArrayList<Item> getItemsByOwner(String ownerId) {
        String sql = SELECT_COLUMNS + " WHERE i.owner_id = ? ORDER BY i.end_time DESC";
        return queryList(sql, ownerId);
    }

    public ArrayList<Item> getAllItems() {
        String sql = SELECT_COLUMNS + " ORDER BY i.end_time DESC";
        ArrayList<Item> items = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) items.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi lấy tất cả sản phẩm: " + e.getMessage());
        }
        return items;
    }

    /**
     * Tất cả item có is_active = 1 (chưa bị soft delete), mọi status.
     */
    public ArrayList<Item> getVisibleItems() {
        String sql = SELECT_COLUMNS + " WHERE i.is_active = 1 ORDER BY i.end_time DESC";
        ArrayList<Item> items = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) items.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi lấy visible items: " + e.getMessage());
        }
        return items;
    }

    public ArrayList<Item> getActiveItems() {
        // [MỚI] Thêm is_active = 1
        String sql = SELECT_COLUMNS + " WHERE i.status = 'active' AND i.is_active = 1 ORDER BY i.end_time ASC";
        ArrayList<Item> items = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) items.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi lấy item active: " + e.getMessage());
        }
        return items;
    }

    public ArrayList<Item> getHiddenItemsBySeller(String sellerId) {
        String sql = SELECT_COLUMNS + " WHERE i.seller_id = ? AND i.is_active = 0 ORDER BY i.end_time DESC";
        return queryList(sql, sellerId);
    }

    public boolean restoreHiddenItem(String itemId, String sellerId) {
        String sql = "UPDATE items SET is_active = 1 WHERE id = ? AND seller_id = ? AND is_active = 0";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, itemId);
            stmt.setString(2, sellerId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Lỗi khôi phục item: " + e.getMessage());
            return false;
        }
    }

    /**
     * Admin hard-delete toàn bộ item khỏi DB — không giới hạn trạng thái.
     *
     * Thứ tự xóa trong một transaction:
     *   1. bids                      (FK → items, ON DELETE cascade chưa có → xóa tay)
     *   2. item_ownership_history    (FK → items)
     *   3. transactions.related_item_id → SET NULL  (FK nullable, giữ lại lịch sử tài chính)
     *   4. images (owner_type='item') → xóa metadata (file vật lý do caller xử lý)
     *   5. items                     (bảng chính)
     *
     * Vì hệ thống dùng ký quỹ ảo (balance không bị trừ khi đặt bid,
     * chỉ trừ/cộng khi settle), nên không cần hoàn tiền khi xóa.
     *
     * @return true nếu xóa thành công
     */
    public boolean adminHardDeleteItem(String itemId) {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getInstance().getConnection();
            conn.setAutoCommit(false);

            // 0. Lấy image_url trước khi xóa để xóa file ảnh vật lý sau
            String imageUrl = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT image_url FROM items WHERE id = ?")) {
                ps.setString(1, itemId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) imageUrl = rs.getString("image_url");
            }

            // 1. Xóa tất cả bids của item này
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM bids WHERE item_id = ?")) {
                ps.setString(1, itemId);
                ps.executeUpdate();
            }

            // 2. Xóa lịch sử sở hữu
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM item_ownership_history WHERE item_id = ?")) {
                ps.setString(1, itemId);
                ps.executeUpdate();
            }

            // 3. Nullify related_item_id trong transactions (giữ lại lịch sử tài chính)
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE transactions SET related_item_id = NULL WHERE related_item_id = ?")) {
                ps.setString(1, itemId);
                ps.executeUpdate();
            }

            // 4. Xóa metadata ảnh của item trong bảng images
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM images WHERE owner_type = 'item' AND owner_id = ?")) {
                ps.setString(1, itemId);
                ps.executeUpdate();
            }

            // 5. Xóa item chính
            int rows;
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM items WHERE id = ?")) {
                ps.setString(1, itemId);
                rows = ps.executeUpdate();
            }

            conn.commit();

            // Xóa file ảnh vật lý sau khi DB commit thành công
            if (imageUrl != null && !imageUrl.isEmpty()) {
                ImageService.deleteFileQuietly("auction_images/" + imageUrl);
            }

            return rows > 0;

        } catch (SQLException e) {
            System.err.println("❌ Lỗi admin hard-delete item: " + e.getMessage());
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { /* ignore */ }
            }
            return false;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ex) { /* ignore */ }
            }
        }
    }

    public ArrayList<Item> searchItems(String keyword) {
        String sql = SELECT_COLUMNS + " WHERE i.name LIKE ? AND i.is_active = 1 ORDER BY i.end_time ASC";
        ArrayList<Item> items = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "%" + keyword + "%");
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) items.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("❌ Lỗi tìm kiếm item: " + e.getMessage());
        }
        return items;
    }



    // ─── HELPER ───────────────────────────────────────────────────────────────

    private ArrayList<Item> queryList(String sql, String param) {
        ArrayList<Item> items = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, param);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) items.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("❌ Lỗi truy vấn danh sách: " + e.getMessage());
        }
        return items;
    }
}