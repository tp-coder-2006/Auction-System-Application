package org.auctionsystem.client.event;

/**
 * EventType — Hằng số các loại sự kiện real-time server gửi xuống client.
 */
public final class EventType {
    private EventType() {}

    /** Server broadcast khi có bid mới được đặt thành công */
    public static final String BID_PLACED         = "BID_PLACED";

    /** Server broadcast khi 1 item chuyển sang trạng thái ACTIVE */
    public static final String ITEM_STARTED       = "ITEM_STARTED";

    /** Server broadcast khi phiên đấu giá kết thúc & thanh toán xong */
    public static final String AUCTION_SETTLED    = "AUCTION_SETTLED";

    /** Server broadcast khi 1 item bị hủy */
    public static final String ITEM_CANCELLED     = "ITEM_CANCELLED";

    /**
     * Server broadcast khi anti-sniping kích hoạt:
     * có bid được đặt trong ≤ 10 giây cuối → end_time tự động +30 giây.
     *
     * Payload JSON:
     *   item_id      — id của item bị gia hạn
     *   item_name    — tên item
     *   new_end_time — end_time mới (ISO LocalDateTime, vd: "2025-06-01T20:00:30")
     *   extended_by  — số giây đã gia hạn (luôn = 30)
     */
    public static final String END_TIME_EXTENDED  = "END_TIME_EXTENDED";

    /**
     * Server broadcast khi seller/admin cập nhật start_time hoặc end_time của item.
     *
     * Payload JSON:
     *   item_id      — id của item bị thay đổi
     *   item_name    — tên item
     *   start_time   — start_time mới (định dạng "yyyy-MM-dd HH:mm:ss")
     *   end_time     — end_time mới (định dạng "yyyy-MM-dd HH:mm:ss")
     */
    public static final String ITEM_TIME_UPDATED  = "ITEM_TIME_UPDATED";

    /**
     * Server broadcast khi seller cập nhật thông tin item (tên, giá, thời gian, ảnh).
     *
     * Payload JSON:
     *   item_id        — id item
     *   name           — tên mới
     *   starting_price — giá khởi điểm mới
     *   start_time     — yyyy-MM-dd HH:mm:ss
     *   end_time       — yyyy-MM-dd HH:mm:ss
     *   image_url      — path ảnh mới (nếu có thay đổi)
     */
    public static final String ITEM_UPDATED       = "ITEM_UPDATED";

    /**
     * Server broadcast khi seller thêm item mới thành công.
     *
     * Payload JSON:
     *   item_id               — id item mới
     *   name                  — tên item
     *   status                — luôn là "PENDING"
     *   starting_price        — giá khởi điểm
     *   current_highest_price — bằng starting_price lúc mới tạo
     *   start_time            — yyyy-MM-dd HH:mm:ss
     *   end_time              — yyyy-MM-dd HH:mm:ss
     *   seller_id             — id seller
     *   image_url             — path ảnh (nếu có)
     */
    public static final String ITEM_ADDED         = "ITEM_ADDED";

    /**
     * Server broadcast khi admin/seller xóa item.
     * Hard delete: xóa hẳn khỏi DB. Soft delete: is_active = 0.
     * Cả hai đều cần xóa item khỏi bảng phía client ngay lập tức.
     *
     * Payload JSON:
     *   item_id     — id của item bị xóa
     *   delete_type — "hard" hoặc "soft"
     */
    public static final String ITEM_DELETED       = "ITEM_DELETED";

    /**
     * Server broadcast khi seller đăng lại item đã CANCELLED → PENDING.
     *
     * Payload JSON:
     *   item_id        — id của item
     *   name           — tên item
     *   starting_price — giá khởi điểm mới
     *   start_time     — thời gian bắt đầu mới (yyyy-MM-dd HH:mm:ss)
     *   end_time       — thời gian kết thúc mới (yyyy-MM-dd HH:mm:ss)
     *   seller_id      — id của seller
     */
    public static final String ITEM_RELISTED      = "ITEM_RELISTED";

    /** Admin dashboard cần refresh thống kê */
    public static final String ADMIN_STATS_UPDATE = "ADMIN_STATS_UPDATE";

    /** Balance của user đã thay đổi */
    public static final String BALANCE_UPDATED    = "BALANCE_UPDATED";

    /** Hoàn tiền bid cũ */
    public static final String BID_CREDIT         = "BID_CREDIT";

    /** Trừ tiền khi đặt bid */
    public static final String BID_DEDUCT         = "BID_DEDUCT";

    /**
     * Server gửi trực tiếp đến client khi admin ban tài khoản của họ.
     *
     * Payload JSON:
     *   message — lý do / thông báo ban (vd: "Tài khoản của bạn đã bị khóa bởi quản trị viên.")
     */
    public static final String BANNED             = "BANNED";

    /**
     * Server gửi trực tiếp đến client khi tài khoản đăng nhập ở thiết bị/phiên khác,
     * khiến phiên hiện tại bị kết thúc cưỡng bức.
     *
     * Payload JSON:
     *   message — thông báo (vd: "Tài khoản của bạn vừa đăng nhập ở nơi khác.")
     */
    public static final String KICKED             = "KICKED";
}