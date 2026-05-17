package org.auctionsystem.server.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.auctionsystem.model.entities.ItemHistory;
import org.auctionsystem.server.DAO.ItemHistoryDAO;

import java.util.ArrayList;

public class ItemHistoryService {
    private final ItemHistoryDAO historyDAO = new ItemHistoryDAO();
    private final Gson gson = new Gson();

    public JsonObject getHistoryBySeller(JsonObject request) {
        return buildResponse(
                historyDAO.getHistoryBySeller(request.get("seller_id").getAsString())
        );
    }

    public JsonObject getHistoryByBuyer(JsonObject request) {
        return buildResponse(
                historyDAO.getHistoryByBuyer(request.get("buyer_id").getAsString())
        );
    }

    public JsonObject getHistoryByItem(JsonObject request) {
        return buildResponse(
                historyDAO.getHistoryByItem(request.get("item_id").getAsString())
        );
    }

    // Dùng chung để tránh lặp code
    private JsonObject buildResponse(ArrayList<ItemHistory> list) {
        JsonObject response = new JsonObject();
        try {
            JsonArray jsonArray = gson.toJsonTree(list).getAsJsonArray();
            response.addProperty("status", "success");
            response.add("message", jsonArray);
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return response;
    }
}