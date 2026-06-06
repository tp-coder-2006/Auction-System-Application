package org.auctionsystem.server.DAO;

import org.auctionsystem.server.Connectivity.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ImageDAO — Quản lý metadata hình ảnh trong database.
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  Tại sao cần ImageDAO?                                                  │
 * │                                                                         │
 * │  Nếu không có ImageDAO:                                                 │
 * │    - ImageService ghi file lên disk KHÔNG biết file cũ là gì để xóa.   │
 * │    - Không thể dọn dẹp file mồ côi (orphan) khi user đổi ảnh liên tục. │
 * │    - Nếu DB update thất bại sau khi file đã ghi → disk có rác.         │
 * │    - Không audit được "ảnh nào đang được dùng, ảnh nào bỏ".            │
 * │                                                                         │
 * │  Với ImageDAO:                                                          │
 * │    1. Tra cứu đường dẫn cũ → ImageService xóa file cũ trước khi ghi.  │
 * │    2. Ghi metadata vào bảng images sau khi file đã tồn tại trên disk.  │
 * │    3. Scan orphan (file trong DB nhưng không còn được FK trỏ vào).     │
 * │    4. Toàn bộ thao tác file ↔ DB đi qua một điểm duy nhất.            │
 * │                                                                         │
 * │  Schema bảng images (SQL ở cuối file):                                 │
 * │    id          VARCHAR(36) PK                                           │
 * │    file_path   VARCHAR(255) UNIQUE  — đường dẫn tương đối              │
 * │    owner_type  ENUM('avatar','item') — phân loại                       │
 * │    owner_id    VARCHAR(36)           — user_id hoặc item_id            │
 * │    created_at  DATETIME DEFAULT NOW()                                   │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * SQL tạo bảng (chạy một lần trên DB):
 * <pre>
 * CREATE TABLE IF NOT EXISTS images (
 *     id          VARCHAR(36)  NOT NULL PRIMARY KEY,
 *     file_path   VARCHAR(255) NOT NULL UNIQUE,
 *     owner_type  ENUM('avatar','item') NOT NULL,
 *     owner_id    VARCHAR(36)  NOT NULL,
 *     created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
 * );
 * </pre>
 */
public class ImageDAO {

    // ─────────────────────────────────────────────────────────────────────────
    //  GHI METADATA
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Đăng ký một file ảnh mới vào DB sau khi đã ghi thành công lên disk.
     * Gọi sau writeFile() trong ImageService — không gọi trước.
     *
     * @param id        UUID của bản ghi (sinh từ ImageService)
     * @param filePath  Đường dẫn tương đối: "avatars/uuid.jpg" hoặc "items/uuid.png"
     * @param ownerType "avatar" hoặc "item"
     * @param ownerId   user_id (cho avatar) hoặc item_id (cho ảnh sản phẩm)
     * @return true nếu insert thành công
     */
    public boolean registerImage(String id, String filePath, String ownerType, String ownerId) {
        String sql = "INSERT INTO images (id, file_path, owner_type, owner_id) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id);
            ps.setString(2, filePath);
            ps.setString(3, ownerType);
            ps.setString(4, ownerId);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("[ImageDAO.registerImage] Lỗi: " + e.getMessage());
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  TRA CỨU ĐỂ XÓA ẢNH CŨ
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lấy đường dẫn file avatar hiện tại của một user.
     * Dùng trong ImageService trước khi ghi avatar mới:
     *   String oldPath = imageDAO.getCurrentAvatarPath(userId);
     *   if (oldPath != null) deleteFile(oldPath);   // xóa file cũ trên disk
     *
     * @param userId  ID của user
     * @return  Đường dẫn tương đối ("avatars/uuid.jpg"), hoặc null nếu chưa có
     */
    public String getCurrentAvatarPath(String userId) {
        String sql = "SELECT file_path FROM images WHERE owner_type = 'avatar' AND owner_id = ? "
                + "ORDER BY created_at DESC LIMIT 1";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("file_path") : null;
            }

        } catch (SQLException e) {
            System.err.println("[ImageDAO.getCurrentAvatarPath] Lỗi: " + e.getMessage());
            return null;
        }
    }

    /**
     * Lấy đường dẫn file ảnh hiện tại của một sản phẩm.
     *
     * @param itemId  ID của sản phẩm
     * @return  Đường dẫn tương đối ("items/uuid.png"), hoặc null nếu chưa có
     */
    public String getCurrentItemImagePath(String itemId) {
        String sql = "SELECT file_path FROM images WHERE owner_type = 'item' AND owner_id = ? "
                + "ORDER BY created_at DESC LIMIT 1";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("file_path") : null;
            }

        } catch (SQLException e) {
            System.err.println("[ImageDAO.getCurrentItemImagePath] Lỗi: " + e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  XÓA METADATA
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Xóa bản ghi metadata của một file ảnh.
     * Gọi SAU KHI đã xóa file vật lý trên disk thành công.
     *
     * @param filePath  Đường dẫn tương đối cần xóa metadata
     * @return true nếu xóa thành công
     */
    public boolean deleteImageRecord(String filePath) {
        String sql = "DELETE FROM images WHERE file_path = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, filePath);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("[ImageDAO.deleteImageRecord] Lỗi: " + e.getMessage());
            return false;
        }
    }

    /**
     * Xóa tất cả bản ghi ảnh của một owner (dùng khi xóa user hoặc xóa item).
     *
     * @param ownerType  "avatar" hoặc "item"
     * @param ownerId    user_id hoặc item_id
     * @return Danh sách file_path đã bị xóa metadata (để caller xóa file vật lý)
     */
    public List<String> deleteAllImagesByOwner(String ownerType, String ownerId) {
        List<String> deletedPaths = new ArrayList<>();

        String selectSql = "SELECT file_path FROM images WHERE owner_type = ? AND owner_id = ?";
        String deleteSql = "DELETE FROM images WHERE owner_type = ? AND owner_id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {

            // Lấy danh sách path trước
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setString(1, ownerType);
                ps.setString(2, ownerId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        deletedPaths.add(rs.getString("file_path"));
                    }
                }
            }

            // Xóa metadata
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setString(1, ownerType);
                ps.setString(2, ownerId);
                ps.executeUpdate();
            }

        } catch (SQLException e) {
            System.err.println("[ImageDAO.deleteAllImagesByOwner] Lỗi: " + e.getMessage());
        }

        return deletedPaths;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  KIỂM TRA TỒN TẠI
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Kiểm tra một file_path đã được đăng ký trong DB chưa.
     * Dùng để tránh đăng ký trùng (dù UUID đã đảm bảo uniqueness).
     *
     * @param filePath  Đường dẫn tương đối cần kiểm tra
     * @return true nếu đã tồn tại
     */
    public boolean existsByFilePath(String filePath) {
        String sql = "SELECT 1 FROM images WHERE file_path = ? LIMIT 1";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, filePath);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            System.err.println("[ImageDAO.existsByFilePath] Lỗi: " + e.getMessage());
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ORPHAN SCAN (dọn dẹp)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lấy danh sách các file_path có metadata trong DB nhưng owner không còn tồn tại.
     *
     * Dùng cho tác vụ maintenance định kỳ (ví dụ chạy AuctionScheduler 1 lần/ngày):
     * <pre>
     *   List<String> orphans = imageDAO.findOrphanAvatars();
     *   for (String path : orphans) {
     *       new File("auction_images/" + path).delete();
     *       imageDAO.deleteImageRecord(path);
     *   }
     * </pre>
     *
     * @return Danh sách file_path của avatar mà user tương ứng không còn trong bảng users
     */
    public List<String> findOrphanAvatars() {
        List<String> orphans = new ArrayList<>();
        String sql = "SELECT i.file_path FROM images i "
                + "LEFT JOIN users u ON i.owner_id = u.id "
                + "WHERE i.owner_type = 'avatar' AND u.id IS NULL";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                orphans.add(rs.getString("file_path"));
            }

        } catch (SQLException e) {
            System.err.println("[ImageDAO.findOrphanAvatars] Lỗi: " + e.getMessage());
        }

        return orphans;
    }

    /**
     * Tương tự findOrphanAvatars() nhưng cho ảnh sản phẩm.
     *
     * @return Danh sách file_path của item image mà item tương ứng không còn trong bảng items
     */
    public List<String> findOrphanItemImages() {
        List<String> orphans = new ArrayList<>();
        String sql = "SELECT i.file_path FROM images i "
                + "LEFT JOIN items it ON i.owner_id = it.id "
                + "WHERE i.owner_type = 'item' AND it.id IS NULL";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                orphans.add(rs.getString("file_path"));
            }

        } catch (SQLException e) {
            System.err.println("[ImageDAO.findOrphanItemImages] Lỗi: " + e.getMessage());
        }

        return orphans;
    }
}