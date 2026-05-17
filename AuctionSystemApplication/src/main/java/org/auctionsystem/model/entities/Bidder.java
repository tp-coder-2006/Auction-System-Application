package org.auctionsystem.model.entities;

import org.auctionsystem.model.enums.ItemStatus;
import org.auctionsystem.model.enums.UserRole;

/**
 * Người đấu giá — role = 'bidder'.
 *
 * Không có thêm cột riêng trong DB so với bảng `users`.
 * Class tồn tại để phân biệt hành vi trong business logic:
 *   - Được phép đặt bid
 *   - Không được phép đăng item
 */
public class Bidder extends User {

    public Bidder(){
        super();
    }

    public Bidder(String id, String name, String username, String password, double balance, String email, String phone, UserRole role) {
        super(id, name, username, password, balance, email, phone, role);
    }
}