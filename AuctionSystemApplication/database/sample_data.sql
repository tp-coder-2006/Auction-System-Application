-- =======================================================
-- DỮ LIỆU MẪU - HỆ THỐNG ĐẤU GIÁ
-- Chạy file này trong MySQL Workbench sau khi đã chạy init_database.sql
-- =======================================================

USE mydb;

-- =======================================================
-- BƯỚC 1: TẠO TÀI KHOẢN NGƯỜI DÙNG
-- Mật khẩu của tất cả tài khoản mẫu đều là: 12345678
-- (Đã được băm bằng BCrypt)
-- =======================================================

INSERT INTO users (id, name, username, password, balance, is_active, email) VALUES

-- Tài khoản Admin
('user-admin-001',
 'Quản trị viên',
 'admin',
 '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
 0, 1, 'admin@auction.com'),

-- Tài khoản Seller
('user-seller-001',
 'Nguyễn Văn An',
 'seller01',
 '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
 50000000, 1, 'seller01@gmail.com'),

-- Tài khoản Bidder 1
('user-bidder-001',
 'Trần Thị Bình',
 'bidder01',
 '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
 100000000, 1, 'bidder01@gmail.com'),

-- Tài khoản Bidder 2
('user-bidder-002',
 'Lê Văn Cường',
 'bidder02',
 '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
 200000000, 1, 'bidder02@gmail.com');


-- =======================================================
-- BƯỚC 2: PHÂN QUYỀN VAI TRÒ
-- =======================================================

-- Đánh dấu admin vào bảng admins
INSERT INTO admins (user_id) VALUES ('user-admin-001');

-- Đánh dấu seller vào bảng sellers
INSERT INTO sellers (user_id, rating) VALUES ('user-seller-001', 5.0);

-- Đánh dấu bidder vào bảng bidders
INSERT INTO bidders (user_id) VALUES ('user-bidder-001');
INSERT INTO bidders (user_id) VALUES ('user-bidder-002');


-- =======================================================
-- BƯỚC 3: THÊM SẢN PHẨM MẪU
-- Dùng seller-001 làm người bán cho tất cả sản phẩm
-- =======================================================

INSERT INTO items (id, name, description, starting_price, current_highest_price, start_time, end_time, status, seller_id) VALUES

-- Sản phẩm đang mở (OPEN) - chưa có ai đặt giá
('item-001',
 'iPhone 15 Pro Max 256GB',
 'Máy mới 100%, chưa active, màu Titan Tự Nhiên. Còn đầy đủ hộp và phụ kiện.',
 25000000, 25000000,
 NOW(),
 DATE_ADD(NOW(), INTERVAL 7 DAY),
 'OPEN', 'user-seller-001'),

('item-002',
 'Laptop Dell XPS 15 2024',
 'RAM 32GB, SSD 1TB, RTX 4070. Dùng được 3 tháng, còn bảo hành 21 tháng.',
 35000000, 35000000,
 NOW(),
 DATE_ADD(NOW(), INTERVAL 3 DAY),
 'OPEN', 'user-seller-001'),

('item-003',
 'Đồng hồ Rolex Submariner',
 'Rolex Submariner Date 116610LN, sản xuất 2021, full box & papers.',
 350000000, 350000000,
 NOW(),
 DATE_ADD(NOW(), INTERVAL 14 DAY),
 'OPEN', 'user-seller-001'),

-- Sản phẩm đang chạy (RUNNING) - đã có người đặt giá
('item-004',
 'Toyota Camry 2.5Q 2023',
 'Xe lướt 8.000km, biển Hà Nội, 1 chủ từ mới, nội thất nguyên zin.',
 950000000, 980000000,
 DATE_SUB(NOW(), INTERVAL 1 DAY),
 DATE_ADD(NOW(), INTERVAL 5 DAY),
 'RUNNING', 'user-seller-001'),

('item-005',
 'Tranh sơn dầu "Hà Nội mùa thu"',
 'Họa sĩ Nguyễn Minh Phước, vẽ năm 2020, kích thước 80x120cm, có chứng nhận.',
 15000000, 17500000,
 DATE_SUB(NOW(), INTERVAL 2 DAY),
 DATE_ADD(NOW(), INTERVAL 2 DAY),
 'RUNNING', 'user-seller-001'),

-- Sản phẩm đã kết thúc (FINISHED) - để test lịch sử đấu giá
('item-006',
 'PlayStation 5 Standard Edition',
 'Máy mới seal, kèm 2 tay cầm và 3 game bản cứng.',
 12000000, 14500000,
 DATE_SUB(NOW(), INTERVAL 10 DAY),
 DATE_SUB(NOW(), INTERVAL 3 DAY),
 'FINISHED', 'user-seller-001');


-- =======================================================
-- BƯỚC 4: THÊM LỊCH SỬ ĐẶT GIÁ MẪU
-- Để màn hình Bidding History có dữ liệu mà test
-- =======================================================

INSERT INTO bids (id, bid_amount, bid_time, bidder_id, item_id) VALUES

-- bidder01 đặt giá cho item-004 (Toyota Camry)
('bid-001', 960000000, DATE_SUB(NOW(), INTERVAL 20 HOUR), 'user-bidder-001', 'item-004'),
('bid-002', 975000000, DATE_SUB(NOW(), INTERVAL 10 HOUR), 'user-bidder-001', 'item-004'),

-- bidder02 đặt giá cao hơn cho item-004
('bid-003', 980000000, DATE_SUB(NOW(), INTERVAL 5 HOUR),  'user-bidder-002', 'item-004'),

-- bidder01 đặt giá cho item-005 (Tranh)
('bid-004', 16000000, DATE_SUB(NOW(), INTERVAL 30 HOUR), 'user-bidder-001', 'item-005'),
('bid-005', 17500000, DATE_SUB(NOW(), INTERVAL 15 HOUR), 'user-bidder-001', 'item-005'),

-- bidder01 tham gia item-006 (PS5 - đã kết thúc)
('bid-006', 13000000, DATE_SUB(NOW(), INTERVAL 8 DAY),  'user-bidder-001', 'item-006'),
('bid-007', 14500000, DATE_SUB(NOW(), INTERVAL 6 DAY),  'user-bidder-001', 'item-006');


-- =======================================================
-- KIỂM TRA KẾT QUẢ
-- Chạy các lệnh này để xác nhận dữ liệu đã được thêm đúng
-- =======================================================

SELECT '=== USERS ===' AS '';
SELECT id, name, username, balance, email FROM users;

SELECT '=== ITEMS ===' AS '';
SELECT id, name, starting_price, current_highest_price, status, end_time FROM items;

SELECT '=== BIDS ===' AS '';
SELECT b.id, b.bid_amount, b.bid_time, u.username, i.name AS item_name
FROM bids b
         JOIN users u ON b.bidder_id = u.id
         JOIN items i ON b.item_id = i.id
ORDER BY b.bid_time DESC;