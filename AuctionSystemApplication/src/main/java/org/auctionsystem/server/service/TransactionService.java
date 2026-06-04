package org.auctionsystem.server.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.auctionsystem.client.event.EventType;
import org.auctionsystem.model.enums.TransactionType;
import org.auctionsystem.server.Connectivity.DatabaseConnection;
import org.auctionsystem.server.DAO.TransactionDAO;
import org.auctionsystem.server.AdminStatsScheduler;
import org.auctionsystem.server.ConnectedClientRegistry;
import org.auctionsystem.server.session.SessionManager;
import org.auctionsystem.server.session.UserSession;
import org.auctionsystem.server.util.GsonConfig;

import java.sql.Connection;
import java.util.List;

public class TransactionService {
    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final Gson gson = GsonConfig.create();

    private void syncSessionBalance(String userId, double newBalance) {
        for (UserSession s : SessionManager.getAllSessions()) {
            if (s.getUserId().equals(userId)) s.setBalance(newBalance);
        }
    }

    // GIỮ NGUYÊN TÊN HÀM: deposit
    public JsonObject deposit(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            String userId = request.get("user_id").getAsString();
            double amount = request.get("amount").getAsDouble();
            try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
                conn.setAutoCommit(false);
                double bBefore = transactionDAO.getBalanceById(userId, conn);
                double bAfter = bBefore + amount;
                if (transactionDAO.updateBalance(userId, amount, conn)) {
                    transactionDAO.insertTransaction(conn, userId, TransactionType.DEPOSIT, amount, bBefore, bAfter, null, "Nạp tiền");
                    conn.commit();
                    syncSessionBalance(userId, bAfter);
                    response.addProperty("status", "success");
                    response.addProperty("message", "Nạp tiền thành công!");
                    response.addProperty("new_balance", bAfter);

                    // [NEW] Broadcast BALANCE_UPDATED chỉ đến đúng user vừa nạp tiền
                    JsonObject event = new JsonObject();
                    event.addProperty("event", EventType.BALANCE_UPDATED);
                    event.addProperty("type",    "DEPOSIT");
                    event.addProperty("amount",  amount);
                    event.addProperty("balance", bAfter);
                    sendToUser(userId, event);

                    // [NEW] Trigger admin stats: transaction_stats thay đổi
                    AdminStatsScheduler.notifyStatsChanged();
                } else { conn.rollback(); response.addProperty("status", "error"); }
            }
        } catch (Exception e) { response.addProperty("status", "error"); }
        return response;
    }

    // GIỮ NGUYÊN TÊN HÀM: withdraw
    public JsonObject withdraw(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            String userId = request.get("user_id").getAsString();
            double amount = request.get("amount").getAsDouble();
            try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
                conn.setAutoCommit(false);
                double bBefore = transactionDAO.getBalanceById(userId, conn);
                if (bBefore < amount) { response.addProperty("status", "error"); return response; }
                double bAfter = bBefore - amount;
                if (transactionDAO.updateBalance(userId, -amount, conn)) {
                    transactionDAO.insertTransaction(conn, userId, TransactionType.WITHDRAW, amount, bBefore, bAfter, null, "Rút tiền");
                    conn.commit();
                    syncSessionBalance(userId, bAfter);
                    response.addProperty("status", "success");
                    response.addProperty("message", "Rút tiền thành công!");
                    response.addProperty("new_balance", bAfter);

                    // [NEW] Broadcast BALANCE_UPDATED chỉ đến đúng user vừa rút tiền
                    JsonObject event = new JsonObject();
                    event.addProperty("event", EventType.BALANCE_UPDATED);
                    event.addProperty("type",    "WITHDRAW");
                    event.addProperty("amount",  amount);
                    event.addProperty("balance", bAfter);
                    sendToUser(userId, event);

                    // [NEW] Trigger admin stats: transaction_stats thay đổi
                    AdminStatsScheduler.notifyStatsChanged();
                } else { conn.rollback(); response.addProperty("status", "error"); }
            }
        } catch (Exception e) { response.addProperty("status", "error"); }
        return response;
    }

    // GIỮ NGUYÊN TÊN HÀM: getMyTransactions
    public JsonObject getMyTransactions(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            String userId = request.get("user_id").getAsString();
            List<JsonObject> list = transactionDAO.getTransactionsByUser(userId);
            JsonArray arr = new JsonArray();
            for (JsonObject o : list) arr.add(o);
            response.addProperty("status", "success");
            response.add("message", arr);
        } catch (Exception e) { response.addProperty("status", "error"); }
        return response;
    }

    // GIỮ NGUYÊN TÊN HÀM: getMyTransactionsByType
    public JsonObject getMyTransactionsByType(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            String userId = request.get("user_id").getAsString();
            TransactionType type = TransactionType.valueOf(request.get("type").getAsString().toUpperCase());
            List<JsonObject> list = transactionDAO.getTransactionsByUserAndType(userId, type);
            JsonArray arr = new JsonArray();
            for (JsonObject o : list) arr.add(o);
            response.addProperty("status", "success");
            response.add("message", arr);
        } catch (Exception e) { response.addProperty("status", "error"); }
        return response;
    }

    /**
     * Ghi 2 transaction trong 1 phiên settle đấu giá: trừ tiền bidder (BID_DEDUCT)
     * và cộng tiền seller (BID_CREDIT).
     *
     * Nhận Connection từ ngoài để tham gia vào transaction của AuctionScheduler —
     * KHÔNG commit, KHÔNG rollback, KHÔNG đóng connection ở đây.
     * Caller (AuctionScheduler) chịu trách nhiệm quản lý transaction.
     *
     * @return double[] { bidderBalanceAfter, sellerBalanceAfter } nếu thành công, null nếu thất bại
     */
    public double[] settleTransfer(Connection conn,
                                   String winnerId, String sellerId,
                                   double amount, String itemId, String itemName) {
        try {
            // Trừ tiền bidder
            double bidderBefore = transactionDAO.getBalanceById(winnerId, conn);
            boolean deducted = transactionDAO.updateBalance(winnerId, -amount, conn);
            if (!deducted) return null;
            double bidderAfter = bidderBefore - amount;
            transactionDAO.insertTransaction(conn, winnerId, TransactionType.BID_DEDUCT,
                    amount, bidderBefore, bidderAfter,
                    itemId, "Thanh toán đấu giá " + itemName);

            // Cộng tiền seller
            double sellerBefore = transactionDAO.getBalanceById(sellerId, conn);
            transactionDAO.updateBalance(sellerId, amount, conn);
            double sellerAfter = sellerBefore + amount;
            transactionDAO.insertTransaction(conn, sellerId, TransactionType.BID_CREDIT,
                    amount, sellerBefore, sellerAfter,
                    itemId, "Nhận tiền bán hàng " + itemName);

            return new double[]{ bidderAfter, sellerAfter };
        } catch (Exception e) {
            System.err.println("[TransactionService] Lỗi settleTransfer: " + e.getMessage());
            return null;
        }
    }

    /**
     * Broadcast BID_DEDUCT đến bidder và BID_CREDIT đến seller sau khi settle commit xong.
     * Gọi SAU conn.commit() — không liên quan đến transaction DB.
     */
    public void broadcastSettleEvents(String winnerId, String sellerId,
                                      double amount, String itemId, String itemName,
                                      double bidderBalanceAfter, double sellerBalanceAfter) {
        // Sync session balance
        syncSessionBalance(winnerId, bidderBalanceAfter);
        syncSessionBalance(sellerId, sellerBalanceAfter);

        // BID_DEDUCT → bidder
        JsonObject deductEvent = new JsonObject();
        deductEvent.addProperty("event",     EventType.BID_DEDUCT);
        deductEvent.addProperty("item_id",   itemId);
        deductEvent.addProperty("item_name", itemName);
        deductEvent.addProperty("amount",    amount);
        deductEvent.addProperty("balance",   bidderBalanceAfter);
        sendToUser(winnerId, deductEvent);

        // BID_CREDIT → seller
        JsonObject creditEvent = new JsonObject();
        creditEvent.addProperty("event",     EventType.BID_CREDIT);
        creditEvent.addProperty("item_id",   itemId);
        creditEvent.addProperty("item_name", itemName);
        creditEvent.addProperty("amount",    amount);
        creditEvent.addProperty("balance",   sellerBalanceAfter);
        sendToUser(sellerId, creditEvent);
    }

    /**
     * Nếu user không online → bỏ qua (không có lỗi).
     */
    private void sendToUser(String userId, JsonObject event) {
        String sessionId = SessionManager.findSessionIdByUserId(userId);
        if (sessionId != null) ConnectedClientRegistry.sendTo(sessionId, event);
    }
}