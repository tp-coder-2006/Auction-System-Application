package org.auctionsystem.server.handler;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.auctionsystem.server.DAO.TransactionDAO;
import org.auctionsystem.server.service.AdminService;

public class AdminHandler {

    private final AdminService   adminService   = new AdminService();
    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final Gson           gson           = new Gson();

    public JsonObject handleBanUser(JsonObject request) {
        return adminService.banUser(request);
    }

    public JsonObject handleUnbanUser(JsonObject request) {
        return adminService.unbanUser(request);
    }

    public JsonObject handleGetAllUsers(JsonObject request) {
        return adminService.getAllUsers(request);
    }

    public JsonObject handleGetAllItems(JsonObject request) {
        return adminService.getAllItems(request);
    }

    public JsonObject handleGetSystemStats(JsonObject request) {
        return adminService.getSystemStats(request);
    }

    public JsonObject handleGetItemTrend(JsonObject request) {
        return adminService.getItemTrend(request);
    }

    public JsonObject handleGetRevenueTrend(JsonObject request) {
        return adminService.getRevenueTrend(request);
    }

    public JsonObject handleGetTransactionsByUser(JsonObject request) {
        return adminService.getTransactionsByUser(request);
    }

    public JsonObject handleGetTransactionsByItem(JsonObject request) {
        return adminService.getTransactionsByItem(request);
    }

    public JsonObject handleGetUserByUsername(JsonObject request) {
        return adminService.getUserByUsername(request);
    }

    /**
     * Lấy toàn bộ giao dịch trong hệ thống (tối đa 500 gần nhất).
     * Gọi thẳng TransactionDAO.getAllTransactions() — không qua AdminService
     * vì logic này đã có sẵn ở DAO, không cần lặp lại ở service layer.
     */
    public JsonObject handleDeleteItem(JsonObject request) {
        return adminService.deleteItem(request);
    }

    public JsonObject handleGetAllTransactions(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            response.addProperty("status", "success");
            response.add("message", gson.toJsonTree(transactionDAO.getAllTransactions()));
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi: " + e.getMessage());
        }
        return response;
    }
}