# Hướng dẫn tích hợp — Admin Stats Screens

## Các file mới / cập nhật

### 1. FXML (thay thế file cũ, đặt vào thư mục View)
```
src/main/resources/org/auctionsystem/client/View/
  ├── Admin_Dashboard.fxml          ← THAY THẾ file cũ
  └── Admin_Stats_Detail.fxml       ← FILE MỚI
```

### 2. Java Controllers (thay thế / thêm mới, đặt vào thư mục Admin)
```
src/main/java/org/auctionsystem/client/Controller/Admin/
  ├── Controller_Admin_Dashboard.java        ← THAY THẾ file cũ
  └── Controller_Admin_Stats_Detail.java     ← FILE MỚI
```

### 3. CSS (thêm vào cuối style.css HOẶC thêm vào FXML)
```
src/main/resources/org/auctionsystem/CSS/
  └── admin_stats_additions.css     ← THÊM NỘI DUNG vào cuối style.css
```

---

## Tóm tắt thay đổi

### Admin_Dashboard (cập nhật)
**Trước:** Chỉ hiển thị 3 con số (total_users, total_items, active_items)

**Sau:** Hiển thị 10 con số chia 3 nhóm:
- 👥 Người dùng: Tổng | Người bán | Người đặt giá | Hoạt động | Bị khóa
- 🏷️ Sản phẩm: Tổng | Đang đấu giá | Chờ duyệt | Đã đóng/Hủy
- 💰 Giao dịch: Tổng giao dịch | Tổng doanh thu

Thêm nút **"📊 Thống kê chi tiết"** điều hướng sang màn hình mới.

### Admin_Stats_Detail (MỚI)
Màn hình thống kê đầy đủ, nhận realtime từ `ADMIN_STATS_UPDATE`:

| Mục | Dữ liệu hiển thị |
|-----|-----------------|
| Người dùng | 5 card: total, sellers, bidders, active, banned |
| Sản phẩm | 6 card: total, pending, active, closed, cancelled, hidden |
| Giao dịch | 5 card: count, totalRevenue, avgPrice, maxPrice, minPrice |
| Top 5 Sellers | TableView: rank, tên, đã bán, doanh thu, rating |
| Top 5 Bidders | TableView: rank, tên, tổng bid, thắng, win% |
| Xu hướng Item | Bar chart 6 tháng (số item đăng mới) |
| Xu hướng Doanh thu | Bar chart 6 tháng (doanh thu + số giao dịch) |

---

## Luồng dữ liệu

```
AdminStatsScheduler (server)
  │
  ├── Periodic (30s): broadcastToAdmins(ADMIN_STATS_UPDATE)
  └── Event-driven: notifyStatsChanged() → debounce 500ms → push
         │
         ▼
EventDispatcher (client) → onStatsUpdate(payload)
         │
         ├── Controller_Admin_Dashboard.onStatsUpdate()
         │     └── applySystemStats(payload.data.system_stats)
         │           ├── user_stats        → 5 labels
         │           ├── item_stats        → 4 labels
         │           └── transaction_stats → 2 labels
         │
         └── Controller_Admin_Stats_Detail.onStatsUpdate()
               ├── applySystemStats()     → tất cả labels + 2 TableViews
               ├── renderItemTrend()      → bar chart item_trend
               └── renderRevenueTrend()   → bar chart revenue_trend
```

---

## Server actions cần hỗ trợ (đã có sẵn trong AdminService.java)

| Action | Dùng tại | Mô tả |
|--------|----------|-------|
| `GET_SYSTEM_STATS` | Cả 2 màn hình | user_stats, item_stats, transaction_stats, top_sellers, top_bidders |
| `GET_ITEM_TREND`   | Stats Detail | item_trend (6 tháng, có tham số `months`) |
| `GET_REVENUE_TREND`| Stats Detail | revenue_trend (6 tháng, có tham số `months`) |
| `ADMIN_STATS_UPDATE` (event) | Cả 2 màn hình | Server push realtime gồm cả 3 loại trên |
