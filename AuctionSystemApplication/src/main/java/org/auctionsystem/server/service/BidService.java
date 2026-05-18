package org.auctionsystem.server.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.auctionsystem.model.entities.Bid;
import org.auctionsystem.model.entities.Item;
import org.auctionsystem.model.enums.ItemStatus;
import org.auctionsystem.server.Connectivity.DatabaseConnection;
import org.auctionsystem.server.DAO.BidDAO;
import org.auctionsystem.server.DAO.ItemDAO;
import org.auctionsystem.server.DAO.ItemHistoryDAO;
import org.auctionsystem.server.DAO.UserDAO;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;

public class BidService {

    public JsonObject placeBid(JsonObject request) {
        JsonObject response = new JsonObject();
        BidDAO bidDAO = new BidDAO();
        UserDAO userDAO = new UserDAO();
        ItemDAO itemDAO = new ItemDAO();

        String itemId    = request.get("item_id").getAsString();
        String bidderId  = request.get("bidder_id").getAsString();
        double bidAmount = request.get("bid_amount").getAsDouble();
        LocalDateTime bidTime = LocalDateTime.now();

        try {
            Item item = itemDAO.getAItemById(itemId);
            if (item == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Không tìm thấy sản phẩm!");
                return response;
            }

            if (item.getStatus() == ItemStatus.PENDING) {
                response.addProperty("status", "error");
                response.addProperty("message", "Phiên đấu giá chưa được mở!");
                return response;
            }

            if (!checkBidTime(item)) {
                response.addProperty("status", "error");
                response.addProperty("message", "Đã quá thời gian đấu giá!");
                return response;
            }

            if (!checkHigherPrice(item, bidAmount)) {
                response.addProperty("status", "error");
                response.addProperty("message", "Giá không hợp lệ!");
                return response;
            }

            double balance = userDAO.getUserById(bidderId).getBalance();
            if (balance < bidAmount) {
                response.addProperty("status", "error");
                response.addProperty("message", "Số dư không đủ!");
                return response;
            }

            try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
                conn.setAutoCommit(false);
                try {
                    bidDAO.insertBid(conn, itemId, bidderId, bidTime, bidAmount);
                    bidDAO.updateItemPrice(conn, itemId, bidAmount);
                    conn.commit();
                    response.addProperty("status", "success");
                    response.addProperty("message", "Đặt giá thành công!");
                } catch (Exception ex) {
                    conn.rollback();
                    System.err.println("Lỗi đặt giá, rollback: " + ex.getMessage());
                    response.addProperty("status", "error");
                    response.addProperty("message", "Đặt giá thất bại!");
                }
            }

        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi máy chủ: " + e.getMessage());
        }
        return response;
    }

    public JsonObject getBidHistory(JsonObject request) {
        JsonObject response = new JsonObject();
        BidDAO bidDAO = new BidDAO();
        Gson gson = new Gson();

        try {
            ArrayList<Bid> bids = bidDAO.getBidHistory(request.get("bidder_id").getAsString());
            JsonArray jsonArray = gson.toJsonTree(bids).getAsJsonArray();
            response.addProperty("status", "success");
            response.add("message", jsonArray);
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi máy chủ: " + e.getMessage());
        }
        return response;
    }

    public JsonObject getBidHistoryByItem(JsonObject request) {
        JsonObject response = new JsonObject();
        BidDAO bidDAO = new BidDAO();
        Gson gson = new Gson();

        try {
            String bidderId = request.get("bidder_id").getAsString();
            String itemId   = request.get("item_id").getAsString();
            ArrayList<Bid> bids = bidDAO.getBidHistoryByItem(bidderId, itemId);
            JsonArray jsonArray = gson.toJsonTree(bids).getAsJsonArray();
            response.addProperty("status", "success");
            response.add("message", jsonArray);
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return response;
    }

    public JsonObject settleBid(JsonObject request) {
        JsonObject response = new JsonObject();
        BidDAO bidDAO           = new BidDAO();
        UserDAO userDAO         = new UserDAO();
        ItemDAO itemDAO         = new ItemDAO();
        ItemHistoryDAO historyDAO = new ItemHistoryDAO();

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);

            String itemId = request.get("item_id").getAsString();
            Item item = itemDAO.getAItemById(itemId);

            if (item == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Không tìm thấy sản phẩm!");
                return response;
            }

            Bid finalBid = bidDAO.getHighestBid(itemId);
            if (finalBid == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Không có lượt đặt giá nào!");
                return response;
            }

            String bidderId = finalBid.getBidderId();
            String sellerId = item.getSellerId();
            double amount   = finalBid.getBidAmount();

            boolean deducted = userDAO.updateBalance(bidderId, -amount, conn); // trừ tiền bidder
            boolean credited = userDAO.updateBalance(sellerId, +amount, conn); // cộng tiền seller

            if (deducted && credited) {
                conn.commit();
                itemDAO.updateOwner(itemId, bidderId);                          // cập nhật owner
                historyDAO.addHistory(itemId, sellerId, bidderId, amount);      // ghi lịch sử
                response.addProperty("status", "success");
                response.addProperty("message", "Thanh toán thành công!");
            } else {
                conn.rollback(); // hoàn tiền nếu lỗi
                itemDAO.cancelItem(itemId);
                response.addProperty("status", "error");
                response.addProperty("message", "Thanh toán thất bại — số dư không đủ!");
            }

        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return response;
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private boolean checkHigherPrice(Item item, double bidAmount) {
        double threshold = (item.getCurrentHighestPrice() != null)
                ? item.getCurrentHighestPrice()
                : item.getStartingPrice();
        return bidAmount > threshold;
    }

    private boolean checkBidTime(Item item) {
        return LocalDateTime.now().isBefore(item.getEndTime());
    }
}