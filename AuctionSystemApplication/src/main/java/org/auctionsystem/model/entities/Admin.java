package org.auctionsystem.model.entities;

import org.auctionsystem.model.enums.UserRole;

/**
 * Quản trị viên — role = 'admin'.
 *
 * Không có cột riêng thêm.
 * balance luôn = 0 vì admin không tham gia giao dịch.
 * rating = NULL trong DB.
 *
 * Tách thành class riêng để giới hạn hành vi:
 *   - Không được bid
 *   - Không được đăng item
 *   - Có thể quản lý user và item
 */
public class Admin extends User {

    public Admin(){
        super();
    }

    public Admin(String id, String name, String username, String password, double balance, String email, String phone, UserRole role) {
        super(id, name, username, password, balance, email, phone, role);
    }
}