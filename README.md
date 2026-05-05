# Hệ thống Đấu giá Trực tuyến / Online Auction System

> **Bài tập lớn – Lập trình nâng cao**
> Phát triển hệ thống đấu giá trực tuyến theo kiến trúc Client–Server với JavaFX và Maven.

---

## Mục lục / Table of Contents

- [Giới thiệu / Introduction](#giới-thiệu--introduction)
- [Công nghệ sử dụng / Tech Stack](#công-nghệ-sử-dụng--tech-stack)
- [Cấu trúc thư mục / Folder Structure](#cấu-trúc-thư-mục--folder-structure)
- [Kiến trúc hệ thống / Architecture](#kiến-trúc-hệ-thống--architecture)
- [Cài đặt & Chạy / Setup & Run](#cài-đặt--chạy--setup--run)

---

## Giới thiệu / Introduction

**[VI]** Hệ thống đấu giá trực tuyến cho phép nhiều người dùng cùng tham gia cạnh tranh giá để mua sản phẩm trong một khoảng thời gian xác định. Hệ thống hỗ trợ ba vai trò: **Bidder** (người đấu giá), **Seller** (người bán) và **Admin** (quản trị viên). Dự án áp dụng các nguyên lý OOP, design pattern và xử lý đồng thời (concurrency).

**[EN]** An online bidding platform allowing multiple users to compete on prices for products within a set time window. The system supports three roles: **Bidder**, **Seller**, and **Admin**. The project applies OOP principles, design patterns, and concurrent programming techniques.

---

## Công nghệ sử dụng / Tech Stack

| Thành phần / Component | Công nghệ / Technology |
|---|---|
| Ngôn ngữ / Language | Java 22 |
| Giao diện / UI | JavaFX + FXML |
| Build tool | Maven |
| Kiến trúc / Architecture | Client–Server, MVC |
| Giao tiếp / Communication | REST API hoặc Socket (JSON) |
| Kiểm thử / Testing | JUnit |
| CI/CD | GitHub Actions |
| IDE | IntelliJ IDEA |

---

## Cấu trúc thư mục / Folder Structure

```
Bidding/
├── pom.xml                          # Cấu hình Maven (dependencies, build config)
│                                    # Maven project configuration file
│
├── .gitignore                       # Danh sách file/thư mục bị Git bỏ qua
│                                    # Files and directories ignored by Git
│
├── .mvn/                            # Maven Wrapper – chạy Maven không cần cài sẵn
│                                    # Maven Wrapper for running Maven without local install
│
├── .idea/                           # Cấu hình IntelliJ IDEA (không commit vào production)
│   ├── compiler.xml                 #   Cài đặt trình biên dịch Java
│   ├── encodings.xml                #   Cấu hình encoding file (UTF-8)
│   ├── jarRepositories.xml          #   Danh sách Maven repository
│   ├── misc.xml                     #   Cài đặt dự án tổng quát
│   ├── IntelliLang.xml              #   Cài đặt plugin IntelliLang
│   └── workspace.xml                #   Trạng thái workspace IDE
│                                    # IntelliJ IDEA project configuration (not for production)
│
└── src/
    ├── main/
    │   ├── java/
    │   │   └── org/
    │   │       └── trietpm/         # Package gốc của ứng dụng / Root application package
    │   │           │
    │   │           ├── Main.java    # Điểm khởi động ứng dụng / Application entry point
    │   │           │
    │   │           ├── Model/       # Lớp thực thể & dữ liệu miền (Entity classes)
    │   │           │                # Chứa: User, Bidder, Seller, Admin, Item,
    │   │           │                #        Electronics, Art, Vehicle,
    │   │           │                #        Auction, BidTransaction, ...
    │   │           │                # Contains domain model/entity classes
    │   │           │
    │   │           ├── Controller/  # Xử lý logic điều hướng & tương tác UI
    │   │           │                # Nhận sự kiện từ View, gọi Service
    │   │           │                # Handles UI events and delegates to Service layer
    │   │           │
    │   │           ├── Service/     # Tầng nghiệp vụ (Business Logic Layer)
    │   │           │                # Chứa logic đấu giá, auto-bid, anti-sniping, ...
    │   │           │                # Contains auction logic, auto-bid, anti-sniping rules
    │   │           │
    │   │           ├── Repository/  # Tầng truy cập dữ liệu (Data Access Layer / DAO)
    │   │           │                # Tương tác với database thông qua DAO pattern
    │   │           │                # Database access using DAO pattern
    │   │           │
    │   │           ├── Connectivity/# Quản lý kết nối (Database & Network)
    │   │           │                # Singleton kết nối DB, cấu hình socket/REST client
    │   │           │                # Manages DB connections and socket/REST communication
    │   │           │
    │   │           ├── IGeneric/    # Interfaces & Abstract classes dùng chung
    │   │           │                # Định nghĩa hành vi chung (Generic contracts)
    │   │           │                # Generic interfaces and abstract base classes
    │   │           │
    │   │           └── Global/      # Hằng số, tiện ích & cấu hình toàn cục
    │   │                            # Constants, utility helpers, global config
    │   │                            # Global constants, utilities, and configuration
    │   │
    │   └── resources/
    │       └── org.trietpm/
    │           └── View/            # File FXML định nghĩa giao diện JavaFX
    │               └── Dashboard.fxml  # Màn hình chính / Main dashboard screen
    │                                   # (Sẽ bổ sung thêm các màn hình khác)
    │                                   # (More screens to be added)
    │
    └── test/
        └── java/                    # Unit Test với JUnit
                                     # Kiểm thử logic nghiệp vụ quan trọng
                                     # JUnit tests for critical business logic
```

---

## Kiến trúc hệ thống / Architecture

**[VI]** Hệ thống được xây dựng theo kiến trúc **Client–Server** kết hợp mô hình **MVC**:

```
┌──────────────────────────────┐       ┌──────────────────────────────────────┐
│           CLIENT             │       │               SERVER                 │
│                              │       │                                      │
│  View (FXML)                 │       │  Controller                          │
│     ↕                        │◄─────►│     ↕                                │
│  Controller                  │ JSON  │  Service (Business Logic)            │
│     ↕                        │       │     ↕                                │
│  Model (local state)         │       │  Repository / DAO                    │
│                              │       │     ↕                                │
│                              │       │  Database                            │
└──────────────────────────────┘       └──────────────────────────────────────┘
```

**[EN]** The system follows a **Client–Server** architecture combined with the **MVC** pattern:
- **Client**: JavaFX UI with FXML views, Controller handles user events.
- **Server**: Processes business logic (Service layer), accesses database via Repository/DAO.
- **Communication**: REST API or Socket with JSON payloads; only the server accesses the database.

### Design Patterns áp dụng / Applied Design Patterns

| Pattern | Mục đích / Purpose |
|---|---|
| **Singleton** | Quản lý kết nối DB / auction manager |
| **Factory Method** | Tạo các loại Item (Electronics, Art, Vehicle) |
| **Observer** | Cập nhật realtime giá đấu cho tất cả client |
| **Strategy / Command** | Xử lý các loại bid khác nhau |

---

## Cài đặt & Chạy / Setup & Run

### Yêu cầu / Prerequisites

- Java 22+
- Maven 3.8+

### Chạy ứng dụng / Run

```bash
# Clone repository
git clone https://github.com/<your-username>/Bidding.git
cd Bidding

# Build project
mvn clean install

# Chạy ứng dụng / Run the application
mvn javafx:run
```

### Chạy tests / Run Tests

```bash
mvn test
```

---

## Thành viên nhóm / Team Members

| Tên / Name | MSSV / Student ID | Vai trò / Role |
|---|---|---|
|  |  |  |
|  |  |  |
|  |  |  |
|  |  |  |
