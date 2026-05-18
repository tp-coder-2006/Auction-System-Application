-- =====================================================
-- Auction System - Sample Data v2.1
-- Password tất cả tài khoản: admin1234
-- =====================================================

USE
`mydb`;

SET
FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE `item_ownership_history`;
TRUNCATE TABLE `bids`;
TRUNCATE TABLE `items`;
TRUNCATE TABLE `users`;
SET
FOREIGN_KEY_CHECKS = 1;

-- 1. USERS
INSERT INTO `users` (id, name, username, password, balance, is_active, email, phone, role, rating)
VALUES ('u-admin-001', 'Administrator', 'admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 0, 1,
        'admin@auctionsystem.com', NULL, 'admin', NULL),
       ('u-seller-001', 'Nguyen Van An', 'nguyenvanan', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        15000000, 1, 'an.nguyen@email.com', '0901234561', 'seller', 4.8),
       ('u-seller-002', 'Tran Thi Bich', 'tranthibich', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        8500000, 1, 'bich.tran@email.com', '0901234562', 'seller', 4.2),
       ('u-bidder-001', 'Pham Minh Dung', 'phamminhdung',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 25000000, 1, 'dung.pham@email.com',
        '0901234563', 'bidder', NULL),
       ('u-bidder-002', 'Hoang Thi Em', 'hoangthiem', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        12000000, 1, 'em.hoang@email.com', '0901234564', 'bidder', NULL),
       ('u-bidder-003', 'Vu Quoc Phong', 'vuquocphong', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        5000000, 1, 'phong.vu@email.com', '0901234565', 'bidder', NULL);

-- 2. ITEMS
INSERT INTO `items` (id, name, description, starting_price, current_highest_price, start_time, end_time, status,
                     seller_id, owner_id)
VALUES ('item-001', 'iPhone 15 Pro Max 256GB', 'May moi 100%, con seal. Bao hanh Apple 12 thang.', 25000000, 27500000,
        '2026-05-10 08:00:00', '2026-05-30 20:00:00', 'active', 'u-seller-001', 'u-seller-001'),
       ('item-002', 'Dong ho Seiko Presage SPB167', 'Dong ho co Nhat Ban, mat men xanh. Fullbox.', 12000000, 13200000,
        '2026-05-12 09:00:00', '2026-05-31 18:00:00', 'active', 'u-seller-002', 'u-seller-002'),
       ('item-003', 'Laptop Dell XPS 15 9530', 'RAM 32GB DDR5, SSD 1TB, man hinh OLED 3.5K.', 38000000, 40000000,
        '2026-05-14 10:00:00', '2026-06-01 22:00:00', 'active', 'u-seller-001', 'u-seller-001'),
       ('item-004', 'Tui Hermes Birkin 30', 'Auth 100%, fullbox nam 2022.', 120000000, NULL, '2026-05-25 10:00:00',
        '2026-06-05 20:00:00', 'pending', 'u-seller-001', 'u-seller-001'),
       ('item-005', 'Xe dap Trek FX 3 Disc 2024', 'Xe dap hybrid moi 100%, phanh dia hydraulic.', 18000000, NULL,
        '2026-05-22 08:00:00', '2026-06-02 20:00:00', 'pending', 'u-seller-002', 'u-seller-002'),
       ('item-006', 'Sony PlayStation 5 Slim', 'May moi fullbox, 2 tay cam DualSense.', 16000000, 17800000,
        '2026-04-20 08:00:00', '2026-04-27 20:00:00', 'closed', 'u-seller-002', 'u-bidder-002'),
       ('item-007', 'May anh Sony Alpha A7 IV', 'Full-frame 33MP, quay 4K/60fps.', 65000000, 68500000,
        '2026-04-15 09:00:00', '2026-04-22 21:00:00', 'closed', 'u-seller-001', 'u-bidder-001');

-- 3. BIDS
INSERT INTO `bids` (id, bid_amount, bid_time, bidder_id, item_id)
VALUES ('bid-001-01', 25500000, '2026-05-10 09:15:00', 'u-bidder-001', 'item-001'),
       ('bid-001-02', 26000000, '2026-05-11 14:30:00', 'u-bidder-002', 'item-001'),
       ('bid-001-03', 26500000, '2026-05-12 10:00:00', 'u-bidder-003', 'item-001'),
       ('bid-001-04', 27000000, '2026-05-13 16:45:00', 'u-bidder-001', 'item-001'),
       ('bid-001-05', 27500000, '2026-05-14 08:20:00', 'u-bidder-002', 'item-001'),
       ('bid-002-01', 12200000, '2026-05-12 11:00:00', 'u-bidder-002', 'item-002'),
       ('bid-002-02', 12800000, '2026-05-13 09:30:00', 'u-bidder-003', 'item-002'),
       ('bid-002-03', 13200000, '2026-05-14 17:00:00', 'u-bidder-001', 'item-002'),
       ('bid-003-01', 38500000, '2026-05-14 11:00:00', 'u-bidder-003', 'item-003'),
       ('bid-003-02', 39000000, '2026-05-14 13:15:00', 'u-bidder-002', 'item-003'),
       ('bid-003-03', 40000000, '2026-05-15 08:00:00', 'u-bidder-001', 'item-003'),
       ('bid-006-01', 16200000, '2026-04-20 10:00:00', 'u-bidder-001', 'item-006'),
       ('bid-006-02', 16700000, '2026-04-22 14:20:00', 'u-bidder-003', 'item-006'),
       ('bid-006-03', 17000000, '2026-04-24 09:45:00', 'u-bidder-001', 'item-006'),
       ('bid-006-04', 17800000, '2026-04-26 18:30:00', 'u-bidder-002', 'item-006'),
       ('bid-007-01', 65500000, '2026-04-15 10:30:00', 'u-bidder-003', 'item-007'),
       ('bid-007-02', 66000000, '2026-04-16 09:00:00', 'u-bidder-001', 'item-007'),
       ('bid-007-03', 68500000, '2026-04-21 16:45:00', 'u-bidder-001', 'item-007');

-- 4. OWNERSHIP HISTORY
INSERT INTO `item_ownership_history` (id, item_id, seller_id, buyer_id, sold_price, sold_time)
VALUES ('hist-001', 'item-006', 'u-seller-002', 'u-bidder-002', 17800000, '2026-04-27 20:00:00'),
       ('hist-002', 'item-007', 'u-seller-001', 'u-bidder-001', 68500000, '2026-04-22 21:00:00');

-- KIEM TRA
SELECT 'USERS' AS `Table`, COUNT(*) AS `Count`
FROM `users`
UNION ALL
SELECT 'ITEMS', COUNT(*)
FROM `items`
UNION ALL
SELECT 'BIDS', COUNT(*)
FROM `bids`
UNION ALL
SELECT 'HISTORY', COUNT(*)
FROM `item_ownership_history`;