-- =====================================================
-- Auction System - Sample Data
-- Phiên bản: 2.1
-- =====================================================

USE `mydb`;

-- =====================================================
-- 1. USERS
-- Password đều là "Test@1234" hash bằng BCrypt
-- =====================================================
INSERT INTO `mydb`.`users` (id, name, username, password, balance, is_active, email, role, rating) VALUES

-- Admin
('u-admin-001', 'Administrator', 'admin',
 '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
 0, 1, 'admin@auctionsystem.com', 'admin', NULL),

-- Sellers
('u-seller-001', 'Nguyễn Văn An',  'nguyenvanan',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 15000000, 1, 'an.nguyen@email.com',  'seller', 4.8),
('u-seller-002', 'Trần Thị Bích',  'trантhibich',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',  8500000, 1, 'bich.tran@email.com',  'seller', 4.2),
('u-seller-003', 'Lê Hoàng Cường', 'lehoangcuong', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',  3200000, 0, 'cuong.le@email.com',   'seller', 3.5),

-- Bidders
('u-bidder-001', 'Phạm Minh Dũng', 'phamminhdung', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 25000000, 1, 'dung.pham@email.com',  'bidder', NULL),
('u-bidder-002', 'Hoàng Thị Em',   'hoangthiem',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 12000000, 1, 'em.hoang@email.com',   'bidder', NULL),
('u-bidder-003', 'Vũ Quốc Phong',  'vuquocphong',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',  5000000, 1, 'phong.vu@email.com',   'bidder', NULL),
('u-bidder-004', 'Đặng Thị Giang', 'dangthigiang', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',  9800000, 1, 'giang.dang@email.com', 'bidder', NULL),
('u-bidder-005', 'Bùi Văn Hải',    'buivanhai',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',  1500000, 0, 'hai.bui@email.com',    'bidder', NULL);

-- =====================================================
-- 2. ITEMS
-- owner_id ban đầu = seller_id
-- Sau khi closed thành công → owner_id = buyer
-- =====================================================
INSERT INTO `mydb`.`items`
(id, name, description, starting_price, current_highest_price, start_time, end_time, status, seller_id, owner_id)
VALUES

-- ACTIVE — đang diễn ra
('item-001',
 'iPhone 15 Pro Max 256GB',
 'Máy mới 100%, còn seal. Màu Titan Tự Nhiên. Bảo hành Apple 12 tháng.',
 25000000, 27500000,
 '2026-05-10 08:00:00', '2026-05-17 20:00:00',
 'active', 'u-seller-001', 'u-seller-001'),

('item-002',
 'Đồng hồ Seiko Presage SPB167',
 'Đồng hồ cơ Nhật, mặt men xanh. Fullbox, bảo hành 18 tháng.',
 12000000, 13200000,
 '2026-05-12 09:00:00', '2026-05-19 18:00:00',
 'active', 'u-seller-002', 'u-seller-002'),

('item-003',
 'Laptop Dell XPS 15 9530',
 'RAM 32GB, SSD 1TB, màn OLED 3.5K. Mới 99%, đủ phụ kiện.',
 38000000, 40000000,
 '2026-05-14 10:00:00', '2026-05-21 22:00:00',
 'active', 'u-seller-001', 'u-seller-001'),

-- PENDING — chưa bắt đầu
('item-004',
 'Túi Hermès Birkin 30',
 'Auth 100%, fullbox 2022. Không vết xước. Kèm bill mua tại Pháp.',
 120000000, NULL,
 '2026-05-20 10:00:00', '2026-05-27 20:00:00',
 'pending', 'u-seller-001', 'u-seller-001'),

('item-005',
 'Xe đạp Trek FX 3 Disc 2024',
 'Mới 100%, khung nhôm, phanh đĩa Shimano. Màu xanh navy.',
 18000000, NULL,
 '2026-05-18 08:00:00', '2026-05-25 20:00:00',
 'pending', 'u-seller-002', 'u-seller-002'),

-- CLOSED — đã kết thúc, thanh toán thành công
-- owner_id = người thắng
('item-006',
 'Sony PlayStation 5 Slim',
 'Mới fullbox, 2 tay cầm DualSense, 3 game bản quyền. BH Sony 12T.',
 16000000, 17800000,
 '2026-04-20 08:00:00', '2026-04-27 20:00:00',
 'closed', 'u-seller-002', 'u-bidder-002'),  -- u-bidder-002 thắng

('item-007',
 'Máy ảnh Sony Alpha A7 IV',
 'Full-frame 33MP, 4K/60fps. 200 shutter count, như mới.',
 65000000, 68500000,
 '2026-04-15 09:00:00', '2026-04-22 21:00:00',
 'closed', 'u-seller-001', 'u-bidder-001'),  -- u-bidder-001 thắng

-- CANCELLED — bị hủy
('item-008',
 'Bose SoundLink Flex (Lô lỗi - đã hủy)',
 'Lô hàng phát hiện lỗi kỹ thuật, seller hủy trước khi bắt đầu.',
 3500000, NULL,
 '2026-05-05 08:00:00', '2026-05-12 20:00:00',
 'cancelled', 'u-seller-003', 'u-seller-003');

-- =====================================================
-- 3. BIDS
-- =====================================================
INSERT INTO `mydb`.`bids` (id, bid_amount, bid_time, bidder_id, item_id) VALUES

-- item-001 (iPhone — active)
('bid-001-01', 25500000, '2026-05-10 09:15:00', 'u-bidder-001', 'item-001'),
('bid-001-02', 26000000, '2026-05-11 14:30:00', 'u-bidder-002', 'item-001'),
('bid-001-03', 26500000, '2026-05-12 10:00:00', 'u-bidder-003', 'item-001'),
('bid-001-04', 27000000, '2026-05-13 16:45:00', 'u-bidder-001', 'item-001'),
('bid-001-05', 27500000, '2026-05-14 08:20:00', 'u-bidder-004', 'item-001'),

-- item-002 (Seiko — active)
('bid-002-01', 12200000, '2026-05-12 11:00:00', 'u-bidder-002', 'item-002'),
('bid-002-02', 12800000, '2026-05-13 09:30:00', 'u-bidder-004', 'item-002'),
('bid-002-03', 13200000, '2026-05-14 17:00:00', 'u-bidder-001', 'item-002'),

-- item-003 (Dell XPS — active)
('bid-003-01', 38500000, '2026-05-14 11:00:00', 'u-bidder-003', 'item-003'),
('bid-003-02', 39000000, '2026-05-14 13:15:00', 'u-bidder-002', 'item-003'),
('bid-003-03', 39500000, '2026-05-14 15:30:00', 'u-bidder-001', 'item-003'),
('bid-003-04', 40000000, '2026-05-15 08:00:00', 'u-bidder-004', 'item-003'),

-- item-006 (PS5 — closed, u-bidder-002 thắng)
('bid-006-01', 16200000, '2026-04-20 10:00:00', 'u-bidder-001', 'item-006'),
('bid-006-02', 16700000, '2026-04-22 14:20:00', 'u-bidder-003', 'item-006'),
('bid-006-03', 17000000, '2026-04-24 09:45:00', 'u-bidder-001', 'item-006'),
('bid-006-04', 17500000, '2026-04-25 11:10:00', 'u-bidder-005', 'item-006'),
('bid-006-05', 17800000, '2026-04-26 18:30:00', 'u-bidder-002', 'item-006'),

-- item-007 (Sony A7 IV — closed, u-bidder-001 thắng)
('bid-007-01', 65500000, '2026-04-15 10:30:00', 'u-bidder-004', 'item-007'),
('bid-007-02', 66000000, '2026-04-16 09:00:00', 'u-bidder-001', 'item-007'),
('bid-007-03', 67000000, '2026-04-18 14:00:00', 'u-bidder-003', 'item-007'),
('bid-007-04', 68000000, '2026-04-20 11:30:00', 'u-bidder-004', 'item-007'),
('bid-007-05', 68500000, '2026-04-21 16:45:00', 'u-bidder-001', 'item-007');

-- =====================================================
-- 4. ITEM OWNERSHIP HISTORY
-- Chỉ ghi các phiên đấu giá closed thành công
-- =====================================================
INSERT INTO `mydb`.`item_ownership_history` (id, item_id, seller_id, buyer_id, sold_price, sold_time) VALUES

-- PS5: u-seller-002 bán cho u-bidder-002
('hist-001', 'item-006', 'u-seller-002', 'u-bidder-002', 17800000, '2026-04-27 20:00:00'),

-- Sony A7 IV: u-seller-001 bán cho u-bidder-001
('hist-002', 'item-007', 'u-seller-001', 'u-bidder-001', 68500000, '2026-04-22 21:00:00');

-- =====================================================
-- KIỂM TRA DỮ LIỆU
-- =====================================================
SELECT 'USERS'             AS `Table`, COUNT(*) AS `Count` FROM `mydb`.`users`
UNION ALL
SELECT 'ITEMS',                        COUNT(*)            FROM `mydb`.`items`
UNION ALL
SELECT 'BIDS',                         COUNT(*)            FROM `mydb`.`bids`
UNION ALL
SELECT 'OWNERSHIP HISTORY',            COUNT(*)            FROM `mydb`.`item_ownership_history`;