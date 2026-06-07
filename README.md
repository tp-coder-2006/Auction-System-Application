# 🏷️ Auction System Application

<div align="center">

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![JavaFX](https://img.shields.io/badge/JavaFX-25.0.2-blue?logo=java)
![MySQL](https://img.shields.io/badge/MySQL-8.x-4479A1?logo=mysql&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-3.8+-C71A36?logo=apachemaven&logoColor=white)
![CI](https://img.shields.io/badge/CI-GitHub%20Actions-2088FF?logo=githubactions&logoColor=white)

</div>

Ứng dụng đấu giá trực tuyến theo thời gian thực, xây dựng bằng **Java 21**, **JavaFX 25** và **MySQL 8**. Hệ thống sử dụng kiến trúc **client-server qua TCP socket tùy chỉnh**, truyền dữ liệu bằng JSON, hỗ trợ ba vai trò: **Bidder**, **Seller** và **Admin**.

---

## Mục lục

- [Tính năng](#tính-năng)
- [Kiến trúc hệ thống](#kiến-trúc-hệ-thống)
- [Công nghệ sử dụng](#công-nghệ-sử-dụng)
- [Cấu trúc dự án](#cấu-trúc-dự-án)
- [Cơ sở dữ liệu](#cơ-sở-dữ-liệu)
- [Yêu cầu hệ thống](#yêu-cầu-hệ-thống)
- [Cài đặt và cấu hình](#cài-đặt-và-cấu-hình)
- [Chạy ứng dụng](#chạy-ứng-dụng)
- [Kiểm thử](#kiểm-thử)
- [CI/CD](#cicd)
- [Video demo](#video-demo)
- [Báo cáo PDF](#báo-cáo-pdf)
- [Lưu ý kỹ thuật](#lưu-ý-kỹ-thuật)
- [Đóng góp](#đóng-góp)

---

## Tính năng

### 🔨 Bidder — Người đặt giá

- Đăng ký / Đăng nhập với quản lý phiên theo vai trò
- Duyệt và tìm kiếm vật phẩm (lọc theo tên, trạng thái, khoảng giá)
- Vào phòng đấu giá, đặt giá theo thời gian thực
- **Đặt giá tự động (Auto-bid):** thiết lập mức giá tối đa, hệ thống tự động đặt giá thay
- **Chống snipe (Anti-sniping):** bid trong 10 giây cuối tự động gia hạn thêm 30 giây
- Xem lịch sử đặt giá và biểu đồ giá theo thời gian
- Ví điện tử: nạp / rút tiền, xem toàn bộ lịch sử giao dịch
- Xem hồ sơ cá nhân và hồ sơ công khai của người dùng khác
- Đánh giá seller sau khi mua hàng thành công (1–5 sao, có thể cập nhật)
- Nhận thông báo thời gian thực (thay đổi số dư, kết quả đấu giá, tài khoản bị khóa)

### 🛒 Seller — Người bán

- Đăng vật phẩm đấu giá mới (tên, mô tả, giá khởi điểm, lịch, hình ảnh)
- Chỉnh sửa hoặc hủy vật phẩm đang chờ trước khi phiên bắt đầu
- Đăng lại vật phẩm đã hủy với giá và lịch mới
- Xem chi tiết vật phẩm và trạng thái đấu giá trực tiếp
- Tự động nhận tiền khi phiên đấu giá kết thúc thành công
- Xem lịch sử bán hàng và toàn bộ lịch sử giao dịch
- Ví điện tử: nạp / rút tiền
- Xem hồ sơ cá nhân và hồ sơ của bidder

### 🛡️ Admin — Quản trị viên

- Dashboard thời gian thực: tổng người dùng, vật phẩm, doanh thu, phiên đang diễn ra
- Quản lý người dùng: xem tất cả tài khoản, khóa / mở khóa
- Quản lý vật phẩm: theo dõi mọi vật phẩm ở mọi trạng thái, xóa cứng / xóa mềm
- Quản lý tài chính: xem toàn bộ giao dịch trong hệ thống
- Nhận cập nhật thống kê tự động mỗi 30 giây từ server
- Trang thống kê chi tiết kèm biểu đồ

---

## Kiến trúc hệ thống

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENT  (JavaFX)                         │
│  FXML Views ←→ Controllers ←→ ServerConnection (TCP Socket)     │
│                        ↕ EventDispatcher                        │
│              BalanceWatcher │ BanWatcher │ NotificationManager   │
└────────────────────────────┬────────────────────────────────────┘
                             │  JSON qua TCP (cổng 8888)
┌────────────────────────────▼────────────────────────────────────┐
│                        SERVER  (Java)                           │
│  AuctionServer → ClientHandler (1 thread mỗi client)            │
│                       ↓ route(action)                           │
│       Handlers: User │ Item │ Bid │ Transaction │ Admin │ ...    │
│                       ↓                                         │
│       Services: UserService │ ItemService │ BidService │ ...     │
│                       ↓                                         │
│       DAOs:    UserDAO │ ItemDAO │ BidDAO │ TransactionDAO │ ... │
│                       ↓                                         │
│       HikariCP Connection Pool → MySQL (mydb)                   │
│                                                                 │
│  Nền: AuctionScheduler (poll 200ms — PENDING→ACTIVE→CLOSED)     │
│       AdminStatsScheduler (push 30s đến admin đang online)      │
└─────────────────────────────────────────────────────────────────┘
```

### Giao thức truyền thông

Mọi tin nhắn là **JSON phân cách bằng dòng mới** qua kết nối TCP liên tục. Mỗi request có trường `action`; server định tuyến và trả về response cùng `action` và `status` (`success` / `error`).

**Request từ client:**
```json
{ "action": "PLACE_BID", "request_id": "uuid", "item_id": "...", "bid_amount": 500000 }
```

**Response từ server:**
```text
{ "action": "PLACE_BID", "request_id": "uuid", "status": "success", "data": { ... } }
```

**Sự kiện server đẩy xuống** (broadcast đến tất cả client, không cần request):
```json
{ "event": "BID_PLACED", "item_id": "...", "bid_amount": 500000, "bidder_id": "..." }
```

### Hệ thống sự kiện thời gian thực

| Sự kiện | Mô tả |
|---|---|
| `BID_PLACED` | Có bid mới được chấp nhận |
| `ITEM_STARTED` | Vật phẩm chuyển PENDING → ACTIVE |
| `AUCTION_SETTLED` | Phiên kết thúc, thanh toán xong |
| `ITEM_CANCELLED` | Vật phẩm bị hủy (hết giờ không có bid, hoặc hủy thủ công) |
| `END_TIME_EXTENDED` | Anti-sniping: gia hạn thêm 30 giây |
| `ITEM_UPDATED` | Seller cập nhật thông tin vật phẩm |
| `ITEM_ADDED` | Vật phẩm mới được đăng |
| `ITEM_DELETED` | Vật phẩm bị xóa — admin luôn hard delete; seller hard delete nếu chưa có bid, soft delete nếu đã có bid |
| `ITEM_RELISTED` | Vật phẩm đã hủy được đăng lại |
| `BALANCE_UPDATED` | Số dư tài khoản thay đổi |
| `BID_DEDUCT` | Trừ tiền người thắng đấu giá |
| `BID_CREDIT` | Cộng tiền seller khi bán được hàng |
| `ADMIN_STATS_UPDATE` | Dashboard admin cần refresh thống kê |
| `BANNED` | Tài khoản bị admin khóa |
| `KICKED` | Tài khoản đăng nhập từ nơi khác, phiên hiện tại bị kết thúc |

---

## Công nghệ sử dụng

| Thành phần | Công nghệ | Phiên bản |
|---|---|---|
| Ngôn ngữ | Java | 21 |
| Giao diện | JavaFX + FXML | 25.0.2 |
| Mạng | TCP Socket tùy chỉnh | — |
| Serialization JSON | Gson | 2.10.1 |
| Cơ sở dữ liệu | MySQL | 8.x |
| Connection Pool | HikariCP | 5.1.0 |
| JDBC Driver | MySQL Connector/J | 8.3.0 |
| Mã hóa mật khẩu | jBCrypt | 0.4 |
| Xác thực đầu vào | Apache Commons Validator | 1.8.0 |
| Build | Maven | 3.8+ |
| Kiểm thử | JUnit Jupiter + Mockito | 5.10.0 / 5.4.0 |
| CI/CD | GitHub Actions | — |

---

## Cấu trúc dự án

```
Auction-System-Application/
├── .github/workflows/
│   └── build.yml                          # CI/CD: build + test tự động
│
├── auction_images/                        # Ảnh lưu trên server
│   ├── avatars/                           # Ảnh đại diện người dùng
│   └── items/                             # Ảnh vật phẩm đấu giá
│
└── AuctionSystemApplication/
    ├── database/
    │   ├── init_database.sql              # Tạo schema (chạy một lần)
    │   └── sample_data.sql               # Dữ liệu mẫu (tùy chọn)
    │
    ├── pom.xml
    │
    └── src/
        ├── main/java/org/auctionsystem/
        │   │
        │   ├── model/                     # Các lớp dùng chung client & server
        │   │   ├── entities/
        │   │   │   ├── User.java
        │   │   │   ├── Bidder.java
        │   │   │   ├── Seller.java
        │   │   │   ├── Admin.java
        │   │   │   ├── Item.java
        │   │   │   ├── Bid.java
        │   │   │   ├── Transaction.java
        │   │   │   └── ItemHistory.java
        │   │   └── enums/
        │   │       ├── UserRole.java      # BIDDER, SELLER, ADMIN
        │   │       ├── ItemStatus.java    # PENDING, ACTIVE, CLOSED, CANCELLED
        │   │       └── TransactionType.java
        │   │
        │   ├── client/
        │   │   ├── Main.java              # Entry point JavaFX
        │   │   ├── Connectivity/
        │   │   │   ├── ServerConnection.java   # Quản lý kết nối TCP + request/response
        │   │   │   └── ImageClient.java        # Upload/download ảnh qua socket
        │   │   ├── session/
        │   │   │   └── UserSession.java        # Singleton: thông tin người dùng đăng nhập
        │   │   ├── event/
        │   │   │   ├── EventDispatcher.java    # Pub/sub cho sự kiện real-time
        │   │   │   ├── EventType.java          # Hằng số tên 16 loại sự kiện
        │   │   │   ├── BalanceWatcher.java     # Lắng nghe BALANCE_UPDATED
        │   │   │   ├── BanWatcher.java         # Lắng nghe BANNED / KICKED
        │   │   │   └── NotificationManager.java
        │   │   └── Controller/
        │   │       ├── Controller_Login.java
        │   │       ├── Controller_Register.java
        │   │       ├── Scene_Utils.java        # Tiện ích chuyển màn hình
        │   │       ├── BidPriceChartBuilder.java
        │   │       ├── Admin/                  # 5 controller cho Admin
        │   │       ├── Bidder/                 # 12 controller cho Bidder
        │   │       └── Seller/                 # 10 controller cho Seller
        │   │
        │   └── server/
        │       ├── AuctionServer.java          # Entry point server (cổng 8888)
        │       ├── ClientHandler.java          # Thread mỗi client, định tuyến action
        │       ├── ConnectedClientRegistry.java
        │       ├── AuctionScheduler.java       # Poll 200ms: PENDING→ACTIVE→CLOSED
        │       ├── AdminStatsScheduler.java    # Push 30s: thống kê đến admin online
        │       ├── Connectivity/
        │       │   └── DatabaseConnection.java # Singleton HikariCP (pool max 20)
        │       ├── session/
        │       ├── util/
        │       ├── DAO/                        # 8 DAO classes
        │       ├── handler/                    # 8 handler classes
        │       └── service/                    # 8 service classes
        │
        ├── main/resources/org/auctionsystem/
        │   ├── CSS/                           # 15 stylesheet JavaFX
        │   ├── Icon/                          # Ảnh mặc định, icon sao
        │   └── client/View/                   # 29 file FXML layout
        │
        └── test/java/org/auctionsystem/
            └── server/service/                # Unit + integration tests
```

---

## Cơ sở dữ liệu

Tên database: **`mydb`** — gồm 6 bảng chính.

```
users
├── items          (seller_id, owner_id → users.id)
│   ├── bids       (bidder_id → users.id, item_id → items.id)
│   └── item_ownership_history  (seller_id, buyer_id → users.id)
├── transactions   (user_id → users.id, related_item_id → items.id)
├── images         (lưu metadata ảnh avatar và ảnh vật phẩm)
└── seller_ratings (bidder_id, seller_id → users.id)
```

### Vòng đời trạng thái vật phẩm

```
PENDING ──(đến start_time)──► ACTIVE ──(đến end_time, có bid)──► CLOSED
   │                             │
   │                             └──(hết giờ, không có bid)──► CANCELLED
   │
   └──(seller hủy thủ công)──► CANCELLED ──(đăng lại)──► PENDING
```

### Chi tiết các bảng

**`users`** — Tài khoản người dùng

| Cột | Kiểu | Mô tả |
|---|---|---|
| `id` | VARCHAR(36) | UUID |
| `name` | VARCHAR(45) | Họ tên hiển thị |
| `username` | VARCHAR(45) | Duy nhất |
| `password` | VARCHAR(255) | Mã hóa bcrypt |
| `email` | VARCHAR(100) | Duy nhất |
| `phone` | VARCHAR(20) | Số điện thoại (nullable) |
| `balance` | DOUBLE | Số dư ví |
| `is_active` | TINYINT(1) | 0 = bị khóa |
| `role` | ENUM | `bidder`, `seller`, `admin` |
| `rating` | DOUBLE | Điểm trung bình (chỉ seller, nullable) |
| `rating_count` | INT | Số lần được đánh giá |
| `avatar_url` | VARCHAR(255) | Đường dẫn ảnh đại diện (nullable) |

**`items`** — Vật phẩm đấu giá

| Cột | Kiểu | Mô tả |
|---|---|---|
| `id` | VARCHAR(36) | UUID |
| `name` | VARCHAR(255) | Tên vật phẩm |
| `description` | TEXT | Mô tả (nullable) |
| `starting_price` | DOUBLE | Giá khởi điểm |
| `current_highest_price` | DOUBLE | Giá cao nhất hiện tại, NULL nếu chưa có bid |
| `start_time` | DATETIME | Thời điểm bắt đầu phiên đấu giá |
| `end_time` | DATETIME | Thời điểm kết thúc (có thể bị gia hạn bởi anti-sniping) |
| `status` | ENUM | `pending`, `active`, `closed`, `cancelled` |
| `is_active` | TINYINT(1) | 0 = soft delete bởi seller (chỉ khi đã có bid và status không phải `active`) |
| `seller_id` | VARCHAR(36) | FK → `users.id`, người đăng bán |
| `owner_id` | VARCHAR(36) | FK → `users.id`, ban đầu = seller, sau khi closed = buyer |
| `image_url` | VARCHAR(255) | Đường dẫn ảnh tương đối (nullable) |

**`bids`** — Lịch sử các lần đặt giá

| Cột | Kiểu | Mô tả |
|---|---|---|
| `id` | VARCHAR(36) | UUID |
| `bid_amount` | DOUBLE | Số tiền đặt giá |
| `bid_time` | DATETIME | Thời điểm đặt giá |
| `bidder_id` | VARCHAR(36) | FK → `users.id` |
| `item_id` | VARCHAR(36) | FK → `items.id` |

**`item_ownership_history`** — Lịch sử mua bán vật phẩm

| Cột | Kiểu | Mô tả |
|---|---|---|
| `id` | VARCHAR(36) | UUID |
| `item_id` | VARCHAR(36) | FK → `items.id` |
| `seller_id` | VARCHAR(36) | FK → `users.id` |
| `buyer_id` | VARCHAR(36) | FK → `users.id` |
| `sold_price` | DOUBLE | Giá bán thực tế |
| `sold_time` | DATETIME | Thời điểm chốt giao dịch |

**`transactions`** — Lịch sử biến động số dư

| Cột | Kiểu | Mô tả |
|---|---|---|
| `id` | VARCHAR(36) | UUID |
| `user_id` | VARCHAR(36) | FK → `users.id` |
| `type` | ENUM | `DEPOSIT`, `WITHDRAW`, `BID_DEDUCT`, `BID_CREDIT` |
| `amount` | DOUBLE | Số tiền (luôn dương, chiều +/- suy từ `type`) |
| `balance_before` | DOUBLE | Số dư trước giao dịch |
| `balance_after` | DOUBLE | Số dư sau giao dịch |
| `related_item_id` | VARCHAR(36) | FK → `items.id`, NULL nếu không liên quan đấu giá |
| `note` | VARCHAR(255) | Ghi chú (nullable) |
| `created_at` | DATETIME | Thời điểm tạo |

**`images`** — Metadata file ảnh trên disk

| Cột | Kiểu | Mô tả |
|---|---|---|
| `id` | VARCHAR(36) | UUID |
| `file_path` | VARCHAR(255) | Đường dẫn tương đối, duy nhất (vd: `avatars/uuid.jpg`) |
| `owner_type` | ENUM | `avatar` (owner_id là user_id) hoặc `item` (owner_id là item_id) |
| `owner_id` | VARCHAR(36) | ID của user hoặc item sở hữu ảnh |
| `created_at` | DATETIME | Thời điểm upload |

**`seller_ratings`** — Đánh giá seller sau giao dịch

| Cột | Kiểu | Mô tả |
|---|---|---|
| `id` | VARCHAR(36) | UUID |
| `bidder_id` | VARCHAR(36) | FK → `users.id` |
| `seller_id` | VARCHAR(36) | FK → `users.id` |
| `rating_score` | TINYINT | Điểm 1–5 |
| `rated_at` | DATETIME | Thời điểm đánh giá lần đầu |
| `updated_at` | DATETIME | Thời điểm cập nhật điểm gần nhất |

Mỗi cặp `(bidder_id, seller_id)` có duy nhất 1 dòng (UNIQUE KEY); bidder có thể cập nhật điểm bất cứ lúc nào.

---

## Yêu cầu hệ thống

| Yêu cầu | Phiên bản |
|---|---|
| JDK | **21** (khuyến nghị Eclipse Temurin) |
| Maven | 3.8 trở lên |
| MySQL | 8.x |
| IDE | IntelliJ IDEA (khuyến nghị) |

> **Lưu ý:** JavaFX 25 không cần cài tay — Maven tự tải khi build.

---

## Cài đặt và cấu hình

### 1. Clone repository

```bash
git clone -b main https://github.com/tp-coder-2006/Auction-System-Application.git
cd Auction-System-Application/AuctionSystemApplication
```

### 2. Tạo database

**Bước 1 — Mở kết nối vào MySQL:**

```bash
mysql -u root -p
```

**Bước 2 — Sau khi nhập mật khẩu và thấy chữ `mysql>`, gõ lệnh reset database:**

```sql
DROP DATABASE IF EXISTS mydb;
CREATE DATABASE mydb;
USE mydb;
```

**Bước 3 — Nạp file cấu hình (init_database):**

```bash
source <đường-dẫn-tuyệt-đối>/AuctionSystemApplication/database/init_database.sql
```

**Bước 4 — Nạp dữ liệu mẫu (tùy chọn):**

```bash
source <đường-dẫn-tuyệt-đối>/AuctionSystemApplication/database/sample_data.sql
```

> **Lưu ý:** Trong lệnh `source` dùng dấu gạch chéo xuôi `/` và **không** cần để đường dẫn trong dấu ngoặc kép.

### 3. Cấu hình kết nối database

Mở file `src/main/java/org/auctionsystem/server/Connectivity/DatabaseConnection.java` và sửa thông tin đăng nhập MySQL:

```java
private static final String URL      = "jdbc:mysql://localhost:3306/mydb"
        + "?useSSL=false&serverTimezone=Asia%2FHo_Chi_Minh&allowPublicKeyRetrieval=true";
private static final String USER     = "root";        // ← đổi thành username của bạn
private static final String PASSWORD = "12345678";    // ← đổi thành password của bạn
```

### 4. Build dự án

```bash
mvn clean install -DskipTests
```

---

## Chạy ứng dụng

> ⚠️ **Quan trọng:** Phải khởi động **Server trước**, sau đó mới chạy Client.

### Khởi động Server

```bash
mvn exec:java -Dexec.mainClass="org.auctionsystem.server.AuctionServer"
```

Hoặc chạy `AuctionServer.main()` trực tiếp từ IntelliJ. Server lắng nghe tại **cổng 8888**.

Output mong đợi:

```
[AuctionScheduler] Đã khởi động real-time.
[AdminStatsScheduler] Đã khởi động...
🚀 AuctionServer đang lắng nghe cổng 8888...
✅ HikariCP pool khởi tạo thành công (max=20).
```

> `AuctionScheduler` và `AdminStatsScheduler` khởi động trước, sau đó server mở cổng 8888. HikariCP pool in thông báo khi lần đầu tiên có kết nối DB được thiết lập (do `DatabaseConnection` là singleton lazy-init).

### Khởi động Client

```bash
mvn javafx:run
```

Hoặc chạy `Main.main()` từ IntelliJ. Client kết nối đến `localhost:8888` theo mặc định.

> 💡 **Chạy nhiều client cùng lúc:** Tạo thêm Run Configuration trong IntelliJ để mô phỏng nhiều người đặt giá đồng thời.

---

## Kiểm thử

Bộ test chia thành **unit test** (không cần DB) và **integration test** (cần MySQL đang chạy, đánh tag `@Tag("integration")`).

### Chỉ chạy unit test

```bash
mvn test -Dgroups="!integration"
```

### Chạy integration test

Khởi động MySQL trước, sau đó:

```bash
mvn test -Dgroups="integration"
```

### Chạy toàn bộ

```bash
mvn test
```

Báo cáo kết quả tại `target/surefire-reports/`.

### Danh sách test

| Class | Nội dung |
|---|---|
| `UserServiceTest` | Đăng ký, đăng nhập, khóa/mở khóa, cập nhật hồ sơ |
| `ItemServiceTest` | Vòng đời vật phẩm, tìm kiếm, sửa, hủy, đăng lại |
| `BidServiceTest` | Xác thực bid, auto-bid, bảo vệ đồng thời |
| `TransactionServiceTest` | Nạp, rút, trừ/hoàn tiền đặt giá |
| `AdminServiceTest` | Thống kê, quản lý người dùng và vật phẩm |
| `ImageServiceTest` | Upload/download ảnh |
| `ItemHistoryServiceTest` | Lịch sử mua bán |
| `SessionAndModelTest` | Quản lý phiên và model |
| `AuctionSchedulerTest` | Chuyển trạng thái PENDING→ACTIVE→CLOSED |
| `AdminStatsSchedulerTest` | Cập nhật thống kê admin |
| `ClientHandlerRouteTest` | Định tuyến action đúng handler |
| `ConcurrencyTest` | Race condition khi nhiều bid đồng thời |
| `SessionManagerTest` | Server-side session registry |
| `UserSessionServerTest` | Server-side session model |
| `UserSessionClientTest` | Client-side session singleton |
| `AuctionIntegrationTest` | End-to-end với MySQL thật (`@Tag("integration")`) |

---

## CI/CD

GitHub Actions tự động chạy khi push hoặc tạo Pull Request vào nhánh `main` / `master`.

### Job 1 — Build & Unit Tests

Chạy song song trên **3 hệ điều hành** (Ubuntu, Windows, macOS):

```bash
mvn clean package -DskipTests
mvn test -Dgroups="!integration"
```

Cấu hình `fail-fast: false` — 1 OS thất bại không dừng 2 OS còn lại.

### Job 2 — Integration Tests

Chỉ chạy trên **Ubuntu** sau khi Job 1 pass trên cả 3 OS, với MySQL 8.0 chạy trong service container:

```bash
mysql < database/init_database.sql
mvn test -Dgroups="integration"
```

Báo cáo test được upload thành artifact sau mỗi lần chạy.

---

## Video demo

> 🎬 Xem bản trình bày và demo giao diện ứng dụng tại Canva:

[**👉 Xem Video Demo**](https://www.canva.com/design/DAHL3DHYBuM/n_nX3f0LhZ3ILfqRGvIR_g/edit)

---

## Báo cáo PDF

> 📄 Tài liệu báo cáo chi tiết của dự án:

[**👉 Xem Báo cáo PDF**](https://drive.google.com/file/d/1Wt-1mg-f1aPPw-R2QI6yMic5fKsK_UFV/view)

---

## Lưu ý kỹ thuật

**HikariCP connection pool** (tối đa 20 kết nối) thay cho `DriverManager`, tránh tạo mới kết nối TCP mỗi request và cạn kiệt slot kết nối MySQL khi nhiều client đồng thời.

**`SELECT ... FOR UPDATE`** trong `AuctionScheduler` ngăn race condition khi nhiều thread cùng xử lý một vật phẩm. Bước cancel và activate dùng transaction riêng biệt để tránh rollback chéo.

**Gson serialize enum thành chữ hoa** — mọi so sánh `ItemStatus` phía client phải dùng chữ hoa (`"ACTIVE"`, không phải `"active"`).

**An toàn JavaFX UI thread** — mọi tác vụ mạng chạy trên background thread; cập nhật giao diện bọc trong `Platform.runLater()`.

**`AuctionScheduler` poll 200ms** — 2 thread riêng biệt cho activate (PENDING→ACTIVE) và settle (ACTIVE→CLOSED), mỗi vòng sleep 200ms.

**Anti-sniping** — bid trong 10 giây cuối tự động kéo dài `end_time` thêm 30 giây và broadcast `END_TIME_EXTENDED` đến toàn bộ client.

**Cột TableView động** — cột có giá trị tính toán theo thời gian (đếm ngược) phải dùng `setCellFactory` với `updateItem()` thay vì `setCellValueFactory`, vì `SimpleStringProperty` bị cache và không tự cập nhật.

---

## Đóng góp

Pull requests luôn được chào đón! Để đóng góp:

1. Fork repository
2. Tạo nhánh tính năng (`git checkout -b feature/ten-tinh-nang`)
3. Commit thay đổi (`git commit -m 'feat: thêm tính năng X'`)
4. Push lên nhánh (`git push origin feature/ten-tinh-nang`)
5. Mở Pull Request

Vui lòng đảm bảo tất cả unit test đều pass trước khi tạo PR:

```bash
mvn test -Dgroups="!integration"
```