package org.auctionsystem.server.service;

import com.google.gson.Gson;
import org.auctionsystem.client.event.EventType;
import org.auctionsystem.server.util.GsonConfig;
import com.google.gson.JsonObject;
import org.auctionsystem.model.entities.Bid;
import org.auctionsystem.model.entities.Item;
import org.auctionsystem.model.enums.ItemStatus;
import org.auctionsystem.model.enums.TransactionType;
import org.auctionsystem.server.AdminStatsScheduler;
import org.auctionsystem.server.ConnectedClientRegistry;
import org.auctionsystem.server.Connectivity.DatabaseConnection;
import org.auctionsystem.server.DAO.BidDAO;
import org.auctionsystem.server.DAO.ItemDAO;
import org.auctionsystem.server.DAO.ItemHistoryDAO;
import org.auctionsystem.server.DAO.TransactionDAO;
import org.auctionsystem.server.session.SessionManager;
import org.auctionsystem.server.session.UserSession;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;

public class BidService {

    private final Gson gson = GsonConfig.create();

    // ─── 1. ĐẶT GIÁ (PLACE BID) ──────────────────────────────────────────────
    public JsonObject placeBid(JsonObject request) {
        JsonObject response = new JsonObject();

        String sessionId = request.has("session_id") ? request.get("session_id").getAsString() : null;
        UserSession session = (sessionId != null) ? SessionManager.getSession(sessionId) : null;
        if (session == null) {
            response.addProperty("status", "error");
            response.addProperty("message", "Phiên đăng nhập không hợp lệ!");
            return response;
        }
        String bidderId = session.getUserId();

        String itemId    = request.get("item_id").getAsString();
        double bidAmount = request.get("bid_amount").getAsDouble();
        String itemName  = "";
        String itemImg   = "";

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Khóa hàng (Lock item row) và lấy thông tin đầy đủ bao gồm image_url
                String lockSql = """
                    SELECT id, name, status, starting_price, current_highest_price,
                           end_time, seller_id, image_url
                    FROM items
                    WHERE id = ? AND is_active = 1
                    FOR UPDATE
                    """;
                Item item = null;
                try (PreparedStatement ps = conn.prepareStatement(lockSql)) {
                    ps.setString(1, itemId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        item = new Item();
                        item.setId(rs.getString("id"));
                        itemName = rs.getString("name");
                        itemImg  = rs.getString("image_url");
                        item.setStatus(ItemStatus.valueOf(rs.getString("status").toUpperCase()));
                        item.setStartingPrice(rs.getDouble("starting_price"));
                        item.setCurrentHighestPrice((Double) rs.getObject("current_highest_price"));
                        item.setEndTime(rs.getTimestamp("end_time").toLocalDateTime());
                        item.setSellerId(rs.getString("seller_id"));
                    }
                }

                if (item == null) {
                    conn.rollback();
                    response.addProperty("status", "error");
                    response.addProperty("message", "Không tìm thấy sản phẩm!");
                    return response;
                }

                // Kiểm tra logic trạng thái và thời gian
                if (item.getStatus() != ItemStatus.ACTIVE || !LocalDateTime.now().isBefore(item.getEndTime())) {
                    conn.rollback();
                    response.addProperty("status", "error");
                    response.addProperty("message", "Phiên đấu giá không khả dụng hoặc đã kết thúc!");
                    return response;
                }

                double threshold = (item.getCurrentHighestPrice() != null) ? item.getCurrentHighestPrice() : item.getStartingPrice();
                if (bidAmount <= threshold) {
                    conn.rollback();
                    response.addProperty("status", "error");
                    response.addProperty("message", "Giá đặt phải cao hơn giá hiện tại!");
                    return response;
                }

                if (bidderId.equals(item.getSellerId())) {
                    conn.rollback();
                    response.addProperty("status", "error");
                    response.addProperty("message", "Bạn không thể đặt giá cho sản phẩm của mình!");
                    return response;
                }

                // Shill bid check - Kiểm tra người đang dẫn đầu giá hiện tại
                String shillCheckSql = "SELECT bidder_id FROM bids WHERE item_id = ? ORDER BY bid_amount DESC LIMIT 1 FOR UPDATE";
                try (PreparedStatement ps = conn.prepareStatement(shillCheckSql)) {
                    ps.setString(1, itemId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next() && bidderId.equals(rs.getString("bidder_id"))) {
                        conn.rollback();
                        response.addProperty("status", "error");
                        response.addProperty("message", "Bạn đang dẫn đầu giá — không thể tự đẩy giá!");
                        return response;
                    }
                }

                // Lock user row + Kiểm tra số dư khả dụng
                String userLockSql = "SELECT balance FROM users WHERE id = ? FOR UPDATE";
                double userBalance = 0;
                try (PreparedStatement ps = conn.prepareStatement(userLockSql)) {
                    ps.setString(1, bidderId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        userBalance = rs.getDouble("balance");
                    }
                }

                BidDAO bidDAO = new BidDAO();
                double totalLeading = bidDAO.getTotalLeadingBids(conn, bidderId, itemId);
                if (userBalance < totalLeading + bidAmount) {
                    conn.rollback();
                    response.addProperty("status", "error");
                    response.addProperty("message", "Số dư không đủ để ký quỹ cho lượt đặt giá này!");
                    return response;
                }

                // Ghi dữ liệu
                bidDAO.insertBid(conn, itemId, bidderId, LocalDateTime.now(), bidAmount);
                bidDAO.updateItemPrice(conn, itemId, bidAmount);

                // ── ANTI-SNIPING ─────────────────────────────────────────────
                // Nếu bid được đặt trong vòng 10 giây cuối → gia hạn thêm 30 giây
                final int SNIPE_WINDOW_SECONDS = 10;
                final int EXTEND_SECONDS       = 30;
                boolean endTimeExtended = false;
                LocalDateTime newEndTime = item.getEndTime();

                long secondsLeft = java.time.Duration.between(LocalDateTime.now(), item.getEndTime()).getSeconds();
                if (secondsLeft >= 0 && secondsLeft <= SNIPE_WINDOW_SECONDS) {
                    newEndTime      = bidDAO.extendEndTime(conn, itemId, EXTEND_SECONDS);
                    endTimeExtended = true;
                }
                // ─────────────────────────────────────────────────────────────

                conn.commit();

                response.addProperty("status", "success");
                response.addProperty("message", "Đặt giá thành công!");

                // Broadcast Real-time Event
                JsonObject event = new JsonObject();
                event.addProperty("event", EventType.BID_PLACED);
                event.addProperty("item_id",    itemId);
                event.addProperty("item_name",  itemName);
                event.addProperty("item_image", itemImg);
                event.addProperty("bidder_id",  bidderId);
                event.addProperty("bidder_name", session.getName());
                event.addProperty("bid_amount", bidAmount);
                event.addProperty("bid_time",   LocalDateTime.now().toString());
                event.addProperty("end_time",   newEndTime.toString());
                ConnectedClientRegistry.broadcastAll(event);

                // Broadcast thêm event riêng để client cập nhật đồng hồ đếm ngược
                if (endTimeExtended) {
                    JsonObject extendEvent = new JsonObject();
                    extendEvent.addProperty("event", EventType.END_TIME_EXTENDED);
                    extendEvent.addProperty("item_id",      itemId);
                    extendEvent.addProperty("item_name",    itemName);
                    extendEvent.addProperty("new_end_time", newEndTime.toString());
                    extendEvent.addProperty("extended_by",  EXTEND_SECONDS);
                    ConnectedClientRegistry.broadcastAll(extendEvent);
                }

            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi: " + e.getMessage());
        }
        return response;
    }

    // ─── 2. QUYẾT TOÁN (SETTLE BID) ──────────────────────────────────────────
    public JsonObject settleBid(JsonObject request) {
        JsonObject response = new JsonObject();
        BidDAO bidDAO             = new BidDAO();
        ItemDAO itemDAO           = new ItemDAO();
        ItemHistoryDAO historyDAO = new ItemHistoryDAO();

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                String itemId = request.get("item_id").getAsString();

                String lockItemSql = "SELECT id, name, status, seller_id, image_url FROM items WHERE id = ? AND is_active = 1 FOR UPDATE";
                Item item = null;
                try (PreparedStatement ps = conn.prepareStatement(lockItemSql)) {
                    ps.setString(1, itemId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        item = new Item();
                        item.setId(rs.getString("id"));
                        item.setName(rs.getString("name"));
                        item.setStatus(ItemStatus.valueOf(rs.getString("status").toUpperCase()));
                        item.setSellerId(rs.getString("seller_id"));
                        item.setImageUrl(rs.getString("image_url"));
                    }
                }

                if (item == null || item.getStatus() == ItemStatus.CLOSED) {
                    conn.rollback();
                    response.addProperty("status", "error");
                    response.addProperty("message", "Sản phẩm không hợp lệ hoặc đã thanh toán.");
                    return response;
                }

                Bid finalBid = bidDAO.getHighestBid(itemId);
                if (finalBid == null) {
                    conn.rollback();
                    response.addProperty("status", "error");
                    response.addProperty("message", "Không tìm thấy người thắng cuộc.");
                    return response;
                }

                String buyerId  = finalBid.getBidderId();
                String sellerId = item.getSellerId();
                double amount   = finalBid.getBidAmount();

                TransactionDAO txDAO = new TransactionDAO();
                double buyerBefore  = txDAO.getBalanceById(buyerId, conn);
                double sellerBefore  = txDAO.getBalanceById(sellerId, conn);

                if (txDAO.updateBalance(buyerId, -amount, conn) && txDAO.updateBalance(sellerId, +amount, conn)) {
                    txDAO.insertTransaction(conn, buyerId, TransactionType.BID_DEDUCT, amount, buyerBefore, buyerBefore - amount, itemId, "Thanh toán thắng đấu giá");
                    txDAO.insertTransaction(conn, sellerId, TransactionType.BID_CREDIT, amount, sellerBefore, sellerBefore + amount, itemId, "Nhận tiền bán sản phẩm");

                    itemDAO.updateOwner(conn, itemId, buyerId);
                    historyDAO.addHistory(conn, itemId, sellerId, buyerId, amount);

                    conn.commit();

                    // [NEW] Broadcast AUCTION_SETTLED với đầy đủ thông tin
                    // buyer/seller cần để UI cập nhật số dư và trạng thái item
                    JsonObject event = new JsonObject();
                    event.addProperty("event", EventType.AUCTION_SETTLED);
                    event.addProperty("item_id",   itemId);
                    event.addProperty("item_name", item.getName());
                    event.addProperty("amount",    amount);
                    event.addProperty("buyer_id",  buyerId);
                    event.addProperty("seller_id", sellerId);
                    if (item.getImageUrl() != null) {
                        event.addProperty("image_url", item.getImageUrl());
                    }
                    ConnectedClientRegistry.broadcastAll(event);

                    // [NEW] Broadcast BID_DEDUCT chỉ đến buyer — thông báo trừ tiền
                    JsonObject deductEvent = new JsonObject();
                    deductEvent.addProperty("event", EventType.BID_DEDUCT);
                    deductEvent.addProperty("item_id",   itemId);
                    deductEvent.addProperty("item_name", item.getName());
                    deductEvent.addProperty("amount",    amount);
                    deductEvent.addProperty("balance",   buyerBefore - amount);
                    ConnectedClientRegistry.sendTo(
                            SessionManager.findSessionIdByUserId(buyerId), deductEvent);

                    // [NEW] Broadcast BID_CREDIT chỉ đến seller — thông báo nhận tiền
                    JsonObject creditEvent = new JsonObject();
                    creditEvent.addProperty("event", EventType.BID_CREDIT);
                    creditEvent.addProperty("item_id",   itemId);
                    creditEvent.addProperty("item_name", item.getName());
                    creditEvent.addProperty("amount",    amount);
                    creditEvent.addProperty("balance",   sellerBefore + amount);
                    ConnectedClientRegistry.sendTo(
                            SessionManager.findSessionIdByUserId(sellerId), creditEvent);

                    // [NEW] Trigger admin stats
                    AdminStatsScheduler.notifyStatsChanged();

                    response.addProperty("status", "success");
                    response.addProperty("message", "Thanh toán thành công!");
                } else {
                    conn.rollback();
                    itemDAO.cancelItem(itemId);
                    response.addProperty("status", "error");
                    response.addProperty("message", "Số dư không đủ, phiên đấu giá bị hủy.");
                }
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return response;
    }

    // ─── 3. CÁC HÀM TRUY VẤN (GET METHODS) ───────────────────────────────────

    public JsonObject getBidsByBidder(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            ArrayList<Bid> bids = new BidDAO().getBidsByBidder(request.get("bidder_id").getAsString());
            response.addProperty("status", "success");
            response.add("message", gson.toJsonTree(bids).getAsJsonArray());
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", e.getMessage());
        }
        return response;
    }

    public JsonObject getBidsByBidderAndItem(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            ArrayList<Bid> bids = new BidDAO().getBidsByBidderAndItem(
                    request.get("bidder_id").getAsString(),
                    request.get("item_id").getAsString());
            response.addProperty("status", "success");
            response.add("message", gson.toJsonTree(bids).getAsJsonArray());
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", e.getMessage());
        }
        return response;
    }

    public JsonObject getBidResultsByBidder(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            ArrayList<Bid> bids = new BidDAO().getBidResultsByBidder(request.get("bidder_id").getAsString());
            response.addProperty("status", "success");
            response.add("message", gson.toJsonTree(bids).getAsJsonArray());
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", e.getMessage());
        }
        return response;
    }

    public JsonObject getActiveBidsByBidder(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            ArrayList<Bid> bids = new BidDAO().getActiveBidsByBidder(request.get("bidder_id").getAsString());
            response.addProperty("status", "success");
            response.add("message", gson.toJsonTree(bids).getAsJsonArray());
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", e.getMessage());
        }
        return response;
    }

    public JsonObject getHighestBidByItem(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            Bid bid = new BidDAO().getHighestBid(request.get("item_id").getAsString());
            if (bid != null) {
                response.addProperty("status", "success");
                response.add("message", gson.toJsonTree(bid));
            } else {
                response.addProperty("status", "error");
                response.addProperty("message", "Chưa có lượt đặt giá nào.");
            }
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", e.getMessage());
        }
        return response;
    }

    public JsonObject getActiveBidsByItem(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            ArrayList<Bid> bids = new BidDAO().getActiveBidsByItem(request.get("item_id").getAsString());
            response.addProperty("status", "success");
            response.add("message", gson.toJsonTree(bids).getAsJsonArray());
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", e.getMessage());
        }
        return response;
    }

    public JsonObject getAllBidsByItem(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            ArrayList<Bid> bids = new BidDAO().getAllBidsByItem(request.get("item_id").getAsString());
            response.addProperty("status", "success");
            response.add("message", gson.toJsonTree(bids).getAsJsonArray());
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", e.getMessage());
        }
        return response;
    }

}