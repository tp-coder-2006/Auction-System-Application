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
     * Gửi event chỉ đến đúng 1 user theo userId.
     * Tìm sessionId của user đang online rồi dùng ConnectedClientRegistry.sendTo().
     * Nếu user không online → bỏ qua (không có lỗi).
     */
    private void sendToUser(String userId, JsonObject event) {
        String sessionId = SessionManager.findSessionIdByUserId(userId);
        if (sessionId != null) ConnectedClientRegistry.sendTo(sessionId, event);
    }
}