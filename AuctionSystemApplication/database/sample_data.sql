-- =====================================================
-- Auction System - Sample Data (Fixed Version)
-- =====================================================

USE
`mydb`;

-- Tắt kiểm tra khóa ngoại tạm thời để nạp dữ liệu không bị lỗi thứ tự
SET
FOREIGN_KEY_CHECKS = 0;

-- Xóa dữ liệu cũ trong các bảng để đảm bảo không bị trùng lặp (Duplicate)
TRUNCATE TABLE `item_ownership_history`;
TRUNCATE TABLE `bids`;
TRUNCATE TABLE `items`;
TRUNCATE TABLE `users`;

SET
FOREIGN_KEY_CHECKS = 1;

-- =====================================================
-- 1. USERS
-- =====================================================
INSERT INTO `mydb`.`users` (id, name, username, password, balance, is_active, email, phone, role, rating)
VALUES
-- Admin (Chỉ giữ 1 bản duy nhất ở đây)
('u-admin-001', 'Administrator', 'admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 0, 1,
 'admin@auctionsystem.com', NULL, 'admin', NULL),

-- Sellers
('u-seller-001', 'Nguyễn Văn An', 'nguyenvanan', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
 15000000, 1, 'an.nguyen@email.com', '0901111001', 'seller', 4.8),
('u-seller-002', 'Trần Thị Bích', 'tranthibich', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
 8500000, 1, 'bich.tran@email.com', '0901111002', 'seller', 4.2),
('u-seller-003', 'Lê Hoàng Cường', 'lehoangcuong', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
 3200000, 0, 'cuong.le@email.com', '0901111003', 'seller', 3.5),

-- Bidders
('u-bidder-001', 'Phạm Minh Dũng', 'phamminhdung', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
 25000000, 1, 'dung.pham@email.com', '0902222001', 'bidder', NULL),
('u-bidder-002', 'Hoàng Thị Em', 'hoangthiem', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 12000000,
 1, 'em.hoang@email.com', '0902222002', 'bidder', NULL),
('u-bidder-003', 'Vũ Quốc Phong', 'vuquocphong', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
 5000000, 1, 'phong.vu@email.com', '0902222003', 'bidder', NULL),
('u-bidder-004', 'Đặng Thị Giang', 'dangthigiang', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
 9800000, 1, 'giang.dang@email.com', '0902222004', 'bidder', NULL),
('u-bidder-005', 'Bùi Văn Hải', 'buivanhai', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 1500000, 0,
 'hai.bui@email.com', '0902222005', 'bidder', NULL);

-- =====================================================
-- 2. ITEMS
-- =====================================================
INSERT INTO `mydb`.`items` (id, name, description, starting_price, current_highest_price, start_time, end_time, status,
                            seller_id, owner_id)
VALUES ('item-001', 'iPhone 15 Pro Max 256GB', 'Máy mới 100%, còn seal. Màu Titan Tự Nhiên.', 25000000, 27500000,
        '2026-05-10 08:00:00', '2026-05-17 20:00:00', 'active', 'u-seller-001', 'u-seller-001'),
       ('item-002', 'Đồng hồ Seiko Presage SPB167', 'Đồng hồ cơ Nhật, mặt men xanh.', 12000000, 13200000,
        '2026-05-12 09:00:00', '2026-05-19 18:00:00', 'active', 'u-seller-002', 'u-seller-002'),
       ('item-003', 'Laptop Dell XPS 15 9530', 'RAM 32GB, SSD 1TB, màn OLED 3.5K.', 38000000, 40000000,
        '2026-05-14 10:00:00', '2026-05-21 22:00:00', 'active', 'u-seller-001', 'u-seller-001'),
       ('item-004', 'Túi Hermès Birkin 30', 'Auth 100%, fullbox 2022.', 120000000, NULL, '2026-05-20 10:00:00',
        '2026-05-27 20:00:00', 'pending', 'u-seller-001', 'u-seller-001'),
       ('item-006', 'Sony PlayStation 5 Slim', 'Mới fullbox, 2 tay cầm DualSense.', 16000000, 17800000,
        '2026-04-20 08:00:00', '2026-04-27 20:00:00', 'closed', 'u-seller-002', 'u-bidder-002'),
       ('item-007', 'Máy ảnh Sony Alpha A7 IV', 'Full-frame 33MP, 4K/60fps.', 65000000, 68500000, '2026-04-15 09:00:00',
        '2026-04-22 21:00:00', 'closed', 'u-seller-001', 'u-bidder-001');

-- =====================================================
-- 3. BIDS
-- =====================================================
INSERT INTO `mydb`.`bids` (id, bid_amount, bid_time, bidder_id, item_id)
VALUES ('bid-001-01', 25500000, '2026-05-10 09:15:00', 'u-bidder-001', 'item-001'),
       ('bid-001-02', 26000000, '2026-05-11 14:30:00', 'u-bidder-002', 'item-001'),
       ('bid-006-05', 17800000, '2026-04-26 18:30:00', 'u-bidder-002', 'item-006'),
       ('bid-007-05', 68500000, '2026-04-21 16:45:00', 'u-bidder-001', 'item-007');

-- =====================================================
-- 4. ITEM OWNERSHIP HISTORY
-- =====================================================
INSERT INTO `mydb`.`item_ownership_history` (id, item_id, seller_id, buyer_id, sold_price, sold_time)
VALUES ('hist-001', 'item-006', 'u-seller-002', 'u-bidder-002', 17800000, '2026-04-27 20:00:00'),
       ('hist-002', 'item-007', 'u-seller-001', 'u-bidder-001', 68500000, '2026-04-22 21:00:00');

-- KIỂM TRA
SELECT 'USERS' AS `Table`, COUNT(*) AS `Count`
FROM `mydb`.`users`
UNION ALL
SELECT 'ITEMS', COUNT(*)
FROM `mydb`.`items`;