-- =====================================================
-- Auction System - Sample Data v2.1
-- Password tất cả tài khoản: Test@1234
-- =====================================================

USE `mydb`;

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE `item_ownership_history`;
TRUNCATE TABLE `bids`;
TRUNCATE TABLE `items`;
TRUNCATE TABLE `users`;
SET FOREIGN_KEY_CHECKS = 1;

-- 1. USERS
-- Password "Test@1234" (BCrypt) dùng chung cho tất cả
INSERT INTO `users` (id, name, username, password, balance, is_active, email, phone, role, rating)
VALUES
-- Tài khoản Admin (Chuyển từ init_database sang đây)
('u-admin-001', 'Administrator', 'admin', '$2a$10$QBi1BXmpikjtkZxbQ8Xzc.P1tOoNz9myoDsM/T3K5eUU2dftv4oOi', 0, 1, 'admin@auctionsystem.com', '0123456789', 'admin', NULL),

-- Sellers
('u-seller-001', 'Nguyen Van An', 'nguyenvanan', '$2a$10$kQSdqrgjwiuJ/l30muFhy.OQnD.tlH7bIT9PvqEuaZKkqgyPNWurC', 15000000, 1, 'an.nguyen@email.com', '0901234561', 'seller', 4.8),
('u-seller-002', 'Tran Thị Bich', 'tranthibich', '$2a$10$kQSdqrgjwiuJ/l30muFhy.OQnD.tlH7bIT9PvqEuaZKkqgyPNWurC', 8500000, 1, 'bich.tran@email.com', '0901234562', 'seller', 4.2),

-- Bidders
('u-bidder-001', 'Pham Minh Dung', 'phamminhdung', '$2a$10$kQSdqrgjwiuJ/l30muFhy.OQnD.tlH7bIT9PvqEuaZKkqgyPNWurC', 25000000, 1, 'dung.pham@email.com', '0901234563', 'bidder', NULL),
('u-bidder-002', 'Hoang Thị Em', 'hoangthiem', '$2a$10$kQSdqrgjwiuJ/l30muFhy.OQnD.tlH7bIT9PvqEuaZKkqgyPNWurC', 12000000, 1, 'em.hoang@email.com', '0901234564', 'bidder', NULL),
('u-bidder-003', 'Vu Quoc Phong', 'vuquocphong', '$2a$10$kQSdqrgjwiuJ/l30muFhy.OQnD.tlH7bIT9PvqEuaZKkqgyPNWurC', 5000000, 1, 'phong.vu@email.com', '0901234565', 'bidder', NULL);

-- 2. ITEMS
INSERT INTO `items` (id, name, description, starting_price, current_highest_price, start_time, end_time, status, seller_id, owner_id)
VALUES
    ('item-001', 'iPhone 15 Pro Max 256GB', 'May moi 100%, con seal.', 25000000, 27500000, '2026-05-10 08:00:00', '2026-05-30 20:00:00', 'active', 'u-seller-001', 'u-seller-001'),
    ('item-002', 'Dong ho Seiko Presage SPB167', 'Dong ho co Nhat Ban.', 12000000, 13200000, '2026-05-12 09:00:00', '2026-05-31 18:00:00', 'active', 'u-seller-002', 'u-seller-002'),
    ('item-006', 'Sony PlayStation 5 Slim', 'May moi fullbox.', 16000000, 17800000, '2026-04-20 08:00:00', '2026-04-27 20:00:00', 'closed', 'u-seller-002', 'u-bidder-002'),
    ('item-007', 'May anh Sony Alpha A7 IV', 'Full-frame 33MP.', 65000000, 68500000, '2026-04-15 09:00:00', '2026-04-22 21:00:00', 'closed', 'u-seller-001', 'u-bidder-001');

-- 3. BIDS
INSERT INTO `bids` (id, bid_amount, bid_time, bidder_id, item_id)
VALUES
    ('bid-001-01', 25500000, '2026-05-10 09:15:00', 'u-bidder-001', 'item-001'),
    ('bid-006-04', 17800000, '2026-04-26 18:30:00', 'u-bidder-002', 'item-006'),
    ('bid-007-03', 68500000, '2026-04-21 16:45:00', 'u-bidder-001', 'item-007');

-- 4. OWNERSHIP HISTORY
INSERT INTO `item_ownership_history` (id, item_id, seller_id, buyer_id, sold_price, sold_time)
VALUES
    ('hist-001', 'item-006', 'u-seller-002', 'u-bidder-002', 17800000, '2026-04-27 20:00:00'),
    ('hist-002', 'item-007', 'u-seller-001', 'u-bidder-001', 68500000, '2026-04-22 21:00:00');

-- KIEM TRA
SELECT 'USERS' AS `Table`, COUNT(*) AS `Count` FROM `users`
UNION ALL SELECT 'ITEMS', COUNT(*) FROM `items`;