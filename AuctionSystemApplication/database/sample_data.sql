-- =====================================================
-- Auction System - Sample Data v2.6
-- Password tất cả tài khoản: admin1234
-- Hash BCrypt: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
-- =====================================================

USE
`mydb`;

SET
FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE `seller_ratings`;
TRUNCATE TABLE `transactions`;
TRUNCATE TABLE `item_ownership_history`;
TRUNCATE TABLE `bids`;
TRUNCATE TABLE `items`;
TRUNCATE TABLE `users`;

SET
FOREIGN_KEY_CHECKS = 1;

-- =====================================================
-- 1. USERS
-- =====================================================
INSERT INTO `users` (id, name, username, password, balance, is_active, email, role, phone, rating, rating_count,
                     avatar_url)
VALUES
-- Admin
('u-admin-001', 'Administrator', 'admin',
 '$2a$10$oBbVqXYS8Fb2U2SH/ZO71Oina9TgNYbEEZEIFUuebNytgHDJ4f6rC',
 0, 1, 'admin@auctionsystem.com', 'admin', NULL, NULL, 0, NULL),

-- Sellers
('u-seller-001', 'Nguyen Van An', 'nguyenvanan',
 '$2a$10$CpOhgpJmaU/AjsAPCvo6iOYbcHWRcFmSNthT/s/i4KOJNshsu5twS',
 15000000, 1, 'an.nguyen@email.com', 'seller', '0901234561', 4.8, 5, NULL),

('u-seller-002', 'Tran Thi Bich', 'tranthibich',
 '$2a$10$CpOhgpJmaU/AjsAPCvo6iOYbcHWRcFmSNthT/s/i4KOJNshsu5twS',
 8500000, 1, 'bich.tran@email.com', 'seller', '0901234562', 4.2, 5, NULL),

('u-seller-003', 'Le Hoang Cuong', 'lehoangcuong',
 '$2a$10$CpOhgpJmaU/AjsAPCvo6iOYbcHWRcFmSNthT/s/i4KOJNshsu5twS',
 3200000, 1, 'cuong.le@email.com', 'seller', '0901234563', NULL, 0, NULL),

-- Bidders
('u-bidder-001', 'Pham Minh Dung', 'phamminhdung',
 '$2a$10$SFDBXsAd65m9pXo8S/Q3MOqZIT282GRw7LfR0DFJe.BOqa8mHc0j2',
 25000000, 1, 'dung.pham@email.com', 'bidder', '0901234564', NULL, 0, NULL),

('u-bidder-002', 'Hoang Thi Em', 'hoangthiem',
 '$2a$10$SFDBXsAd65m9pXo8S/Q3MOqZIT282GRw7LfR0DFJe.BOqa8mHc0j2',
 12000000, 1, 'em.hoang@email.com', 'bidder', '0901234565', NULL, 0, NULL),

('u-bidder-003', 'Vu Quoc Phong', 'vuquocphong',
 '$2a$10$SFDBXsAd65m9pXo8S/Q3MOqZIT282GRw7LfR0DFJe.BOqa8mHc0j2',
 5000000, 1, 'phong.vu@email.com', 'bidder', '0901234566', NULL, 0, NULL),

('u-bidder-004', 'Dang Thi Giang', 'dangthigiang',
 '$2a$10$SFDBXsAd65m9pXo8S/Q3MOqZIT282GRw7LfR0DFJe.BOqa8mHc0j2',
 9800000, 1, 'giang.dang@email.com', 'bidder', '0901234567', NULL, 0, NULL),

-- Bidder bị khóa
('u-bidder-005', 'Bui Van Hai', 'buivanhai',
 '$2a$10$SFDBXsAd65m9pXo8S/Q3MOqZIT282GRw7LfR0DFJe.BOqa8mHc0j2',
 1500000, 0, 'hai.bui@email.com', 'bidder', NULL, NULL, 0, NULL);

-- =====================================================
-- 2. ITEMS
-- =====================================================
INSERT INTO `items` (id, name, description, starting_price, current_highest_price,
                     start_time, end_time, status, is_active, seller_id, owner_id, image_url)
VALUES ('item-001', 'iPhone 15 Pro Max 256GB',
        'May moi 100%, con seal. Mau Titan Tu Nhien. Bao hanh Apple 12 thang.',
        25000000, 27500000,
        '2026-05-10 08:00:00', '2026-06-10 20:00:00',
        'active', 1, 'u-seller-001', 'u-seller-001', NULL),

       ('item-002', 'Dong ho Seiko Presage SPB167',
        'Dong ho co Nhat Ban, mat men xanh. Fullbox, su dung 6 thang.',
        12000000, 13200000,
        '2026-05-12 09:00:00', '2026-06-12 18:00:00',
        'active', 1, 'u-seller-002', 'u-seller-002', NULL),

       ('item-003', 'Laptop Dell XPS 15 9530',
        'RAM 32GB DDR5, SSD 1TB NVMe, man hinh OLED 3.5K. Moi 99%.',
        38000000, 40000000,
        '2026-05-14 10:00:00', '2026-06-14 22:00:00',
        'active', 1, 'u-seller-001', 'u-seller-001', NULL),

       ('item-004', 'Tui Hermes Birkin 30 Da Togo',
        'Auth 100%, fullbox nam 2022. Tinh trang xuat sac.',
        120000000, NULL,
        '2026-06-01 10:00:00', '2026-06-15 20:00:00',
        'pending', 1, 'u-seller-001', 'u-seller-001', NULL),

       ('item-005', 'Xe dap the thao Trek FX 3 Disc 2024',
        'Xe dap hybrid moi 100%, khung nhom Alpha Platinum.',
        18000000, NULL,
        '2026-05-28 08:00:00', '2026-06-10 20:00:00',
        'pending', 1, 'u-seller-002', 'u-seller-002', NULL),

       ('item-006', 'Bo loa Bose SoundLink Max',
        'Loa Bluetooth cao cap, chong nuoc IP67, pin 20 gio. Moi 100%.',
        8500000, NULL,
        '2026-05-30 09:00:00', '2026-06-13 21:00:00',
        'pending', 1, 'u-seller-003', 'u-seller-003', NULL),

       ('item-007', 'Sony PlayStation 5 Slim Disc Edition',
        'May moi fullbox, 2 tay cam DualSense, 3 game ban quyen.',
        16000000, 17800000,
        '2026-04-20 08:00:00', '2026-04-27 20:00:00',
        'closed', 1, 'u-seller-002', 'u-bidder-002', NULL),

       ('item-008', 'May anh Sony Alpha A7 IV Body Only',
        'Mirrorless full-frame 33MP, quay 4K/60fps. Su dung 200 shutter count.',
        65000000, 68500000,
        '2026-04-15 09:00:00', '2026-04-22 21:00:00',
        'closed', 1, 'u-seller-001', 'u-bidder-001', NULL),

       ('item-009', 'MacBook Pro 14 M3 Pro (Da huy)',
        'Phat hien lo hang loi ky thuat, seller huy truoc khi bat dau.',
        55000000, NULL,
        '2026-05-01 08:00:00', '2026-05-08 20:00:00',
        'cancelled', 1, 'u-seller-003', 'u-seller-003', NULL),

       ('item-010', 'Camera GoPro Hero 12 (Da xoa)',
        'Item bi soft delete boi seller.',
        5000000, NULL,
        '2026-04-01 08:00:00', '2026-04-08 20:00:00',
        'cancelled', 0, 'u-seller-002', 'u-seller-002', NULL);

-- =====================================================
-- 3. BIDS
-- =====================================================
INSERT INTO `bids` (id, bid_amount, bid_time, bidder_id, item_id)
VALUES
-- item-001 (iPhone)
('bid-001-01', 25500000, '2026-05-10 09:15:00', 'u-bidder-001', 'item-001'),
('bid-001-02', 26000000, '2026-05-11 14:30:00', 'u-bidder-002', 'item-001'),
('bid-001-03', 26500000, '2026-05-12 10:00:00', 'u-bidder-003', 'item-001'),
('bid-001-04', 27000000, '2026-05-13 16:45:00', 'u-bidder-001', 'item-001'),
('bid-001-05', 27500000, '2026-05-14 08:20:00', 'u-bidder-004', 'item-001'),

-- item-002 (Seiko)
('bid-002-01', 12200000, '2026-05-12 11:00:00', 'u-bidder-002', 'item-002'),
('bid-002-02', 12800000, '2026-05-13 09:30:00', 'u-bidder-003', 'item-002'),
('bid-002-03', 13200000, '2026-05-14 17:00:00', 'u-bidder-001', 'item-002'),

-- item-003 (Dell XPS)
('bid-003-01', 38500000, '2026-05-14 11:00:00', 'u-bidder-003', 'item-003'),
('bid-003-02', 39000000, '2026-05-14 13:15:00', 'u-bidder-002', 'item-003'),
('bid-003-03', 39500000, '2026-05-14 15:30:00', 'u-bidder-001', 'item-003'),
('bid-003-04', 40000000, '2026-05-15 08:00:00', 'u-bidder-004', 'item-003'),

-- item-007 (PS5 - closed)
('bid-007-01', 16200000, '2026-04-20 10:00:00', 'u-bidder-001', 'item-007'),
('bid-007-02', 16700000, '2026-04-22 14:20:00', 'u-bidder-003', 'item-007'),
('bid-007-03', 17000000, '2026-04-24 09:45:00', 'u-bidder-001', 'item-007'),
('bid-007-04', 17500000, '2026-04-25 11:10:00', 'u-bidder-004', 'item-007'),
('bid-007-05', 17800000, '2026-04-26 18:30:00', 'u-bidder-002', 'item-007'),

-- item-008 (Sony A7 IV - closed)
('bid-008-01', 65500000, '2026-04-15 10:30:00', 'u-bidder-004', 'item-008'),
('bid-008-02', 66000000, '2026-04-16 09:00:00', 'u-bidder-001', 'item-008'),
('bid-008-03', 67000000, '2026-04-18 14:00:00', 'u-bidder-003', 'item-008'),
('bid-008-04', 68000000, '2026-04-20 11:30:00', 'u-bidder-004', 'item-008'),
('bid-008-05', 68500000, '2026-04-21 16:45:00', 'u-bidder-001', 'item-008');

-- =====================================================
-- 4. ITEM OWNERSHIP HISTORY
-- =====================================================
INSERT INTO `item_ownership_history` (id, item_id, seller_id, buyer_id, sold_price, sold_time)
VALUES ('hist-001', 'item-007', 'u-seller-002', 'u-bidder-002', 17800000, '2026-04-27 20:00:00'),
       ('hist-002', 'item-008', 'u-seller-001', 'u-bidder-001', 68500000, '2026-04-22 21:00:00');

-- =====================================================
-- 5. TRANSACTIONS
-- type:
--   DEPOSIT    : nạp tiền vào tài khoản
--   WITHDRAW   : rút tiền ra
--   BID_DEDUCT : trừ tiền bidder thắng để thanh toán
--   BID_CREDIT : cộng tiền seller khi bán được hàng
-- =====================================================
INSERT INTO `transactions`
(id, user_id, type, amount, balance_before, balance_after, related_item_id, note, created_at)
VALUES

-- ── Nạp tiền ban đầu ──────────────────────────────────────────────────────────
('tx-dep-001', 'u-bidder-001', 'DEPOSIT', 30000000, 0, 30000000, NULL, 'Nap tien lan dau', '2026-04-10 08:00:00'),
('tx-dep-002', 'u-bidder-002', 'DEPOSIT', 20000000, 0, 20000000, NULL, 'Nap tien lan dau', '2026-04-10 09:00:00'),
('tx-dep-003', 'u-bidder-003', 'DEPOSIT', 10000000, 0, 10000000, NULL, 'Nap tien lan dau', '2026-04-11 10:00:00'),
('tx-dep-004', 'u-bidder-004', 'DEPOSIT', 15000000, 0, 15000000, NULL, 'Nap tien lan dau', '2026-04-11 11:00:00'),
('tx-dep-005', 'u-bidder-005', 'DEPOSIT', 3000000, 0, 3000000, NULL, 'Nap tien lan dau', '2026-04-12 07:00:00'),

-- ── Nạp thêm trước phiên đấu giá Sony A7 IV ──────────────────────────────────
('tx-dep-006', 'u-bidder-001', 'DEPOSIT', 20000000, 30000000, 50000000, NULL, 'Nap them truoc khi dau gia Sony A7',
 '2026-04-14 08:00:00'),
('tx-dep-007', 'u-bidder-002', 'DEPOSIT', 5000000, 20000000, 25000000, NULL, 'Nap them', '2026-04-19 15:00:00'),

-- ── Rút tiền ──────────────────────────────────────────────────────────────────
('tx-wit-001', 'u-bidder-005', 'WITHDRAW', 1000000, 3000000, 2000000, NULL, 'Rut tien', '2026-04-13 14:00:00'),

-- ── Kết thúc đấu giá item-008: Sony A7 IV — phamminhdung thắng 68,500,000 ────
-- Trừ tiền bidder thắng (thanh toán)
('tx-bd-008', 'u-bidder-001', 'BID_DEDUCT', 68500000, 50000000, -18500000, 'item-008',
 'Thanh toan dau gia Sony Alpha A7 IV', '2026-04-22 21:05:00'),
-- Cộng tiền cho seller
('tx-bc-008', 'u-seller-001', 'BID_CREDIT', 68500000, 0, 68500000, 'item-008', 'Nhan tien ban Sony Alpha A7 IV',
 '2026-04-22 21:05:00'),

-- ── phamminhdung nạp bù sau khi thanh toán ────────────────────────────────────
('tx-dep-008', 'u-bidder-001', 'DEPOSIT', 50000000, 0, 50000000, NULL, 'Nap bu sau khi thanh toan Sony A7',
 '2026-04-23 09:00:00'),

-- ── Kết thúc đấu giá item-007: PS5 — hoangthiem thắng 17,800,000 ─────────────
-- Trừ tiền bidder thắng (thanh toán)
('tx-bd-007', 'u-bidder-002', 'BID_DEDUCT', 17800000, 25000000, 7200000, 'item-007', 'Thanh toan dau gia PS5 Slim',
 '2026-04-27 20:05:00'),
-- Cộng tiền cho seller
('tx-bc-007', 'u-seller-002', 'BID_CREDIT', 17800000, 0, 17800000, 'item-007', 'Nhan tien ban PS5 Slim',
 '2026-04-27 20:05:00'),

-- ── Rút tiền sau khi bán hàng ─────────────────────────────────────────────────
('tx-wit-002', 'u-seller-001', 'WITHDRAW', 5000000, 68500000, 63500000, NULL, 'Rut tien dinh ky',
 '2026-04-25 10:00:00'),
('tx-wit-003', 'u-seller-002', 'WITHDRAW', 3000000, 17800000, 14800000, NULL, 'Rut tien sau khi ban PS5',
 '2026-05-01 09:00:00'),

-- ── Nạp tiền thêm trước phiên iPhone / Dell XPS ───────────────────────────────
('tx-dep-009', 'u-bidder-002', 'DEPOSIT', 8000000, 7200000, 15200000, NULL, 'Nap them', '2026-04-28 10:00:00'),
('tx-dep-010', 'u-bidder-003', 'DEPOSIT', 5000000, 10000000, 15000000, NULL, 'Nap them', '2026-05-11 08:30:00'),
('tx-dep-011', 'u-bidder-004', 'DEPOSIT', 10000000, 15000000, 25000000, NULL, 'Nap them truoc khi dau gia iPhone',
 '2026-05-09 09:00:00'),
('tx-dep-012', 'u-bidder-001', 'DEPOSIT', 10000000, 50000000, 60000000, NULL, 'Nap them de dau gia iPhone',
 '2026-05-12 10:00:00'),
('tx-dep-013', 'u-bidder-004', 'DEPOSIT', 10000000, 25000000, 35000000, NULL, 'Nap them cho dau gia Dell XPS',
 '2026-05-14 07:30:00'),

-- ── Rút tiền định kỳ của seller ───────────────────────────────────────────────
('tx-wit-004', 'u-seller-001', 'WITHDRAW', 10000000, 63500000, 53500000, NULL, 'Rut tien dinh ky thang 5',
 '2026-05-05 11:00:00'),
('tx-wit-005', 'u-seller-002', 'WITHDRAW', 5000000, 14800000, 9800000, NULL, 'Rut tien dinh ky thang 5',
 '2026-05-06 10:00:00');

-- =====================================================
-- 6. SELLER RATINGS
-- =====================================================
INSERT INTO `seller_ratings`
    (id, bidder_id, seller_id, rating_score, rated_at, updated_at)
VALUES

-- Seller 001 (4.8 / 5)
('sr-001', 'u-bidder-001', 'u-seller-001', 5,
 '2026-04-23 10:00:00', '2026-04-23 10:00:00'),

('sr-002', 'u-bidder-002', 'u-seller-001', 5,
 '2026-04-24 09:15:00', '2026-04-24 09:15:00'),

('sr-003', 'u-bidder-003', 'u-seller-001', 5,
 '2026-04-25 14:20:00', '2026-04-25 14:20:00'),

('sr-004', 'u-bidder-004', 'u-seller-001', 4,
 '2026-04-26 18:30:00', '2026-04-26 18:30:00'),

('sr-005', 'u-bidder-005', 'u-seller-001', 5,
 '2026-04-27 08:45:00', '2026-04-27 08:45:00'),

-- Seller 002 (4.2 / 5)
('sr-006', 'u-bidder-001', 'u-seller-002', 4,
 '2026-04-28 10:00:00', '2026-04-28 10:00:00'),

('sr-007', 'u-bidder-002', 'u-seller-002', 5,
 '2026-04-29 09:00:00', '2026-04-29 09:00:00'),

('sr-008', 'u-bidder-003', 'u-seller-002', 4,
 '2026-04-30 13:15:00', '2026-04-30 13:15:00'),

('sr-009', 'u-bidder-004', 'u-seller-002', 4,
 '2026-05-01 15:30:00', '2026-05-01 15:30:00'),

('sr-010', 'u-bidder-005', 'u-seller-002', 4,
 '2026-05-02 11:20:00', '2026-05-02 11:20:00');

-- =====================================================
-- KIỂM TRA
-- =====================================================
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
FROM `item_ownership_history`

UNION ALL
SELECT 'TRANSACTIONS', COUNT(*)
FROM `transactions`

UNION ALL
SELECT 'SELLER_RATINGS', COUNT(*)
FROM `seller_ratings`;