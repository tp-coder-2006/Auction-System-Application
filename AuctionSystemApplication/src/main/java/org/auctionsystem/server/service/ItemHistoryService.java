package org.auctionsystem.server.service;

import com.google.gson.Gson;
import org.auctionsystem.server.util.GsonConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.auctionsystem.model.entities.ItemHistory;
import org.auctionsystem.server.DAO.ItemHistoryDAO;

import java.util.ArrayList;

public class ItemHistoryService {
    private final ItemHistoryDAO historyDAO = new ItemHistoryDAO();
    private final Gson gson = GsonConfig.create();

    public JsonObject getHistoryBySeller(JsonObject request) {
        if (!request.has("seller_id") || request.get("seller_id").isJsonNull())
            return buildError("Thiếu trường seller_id");
        return buildResponse(
                historyDAO.getHistoryBySeller(request.get("seller_id").getAsString())
        );
    }

    public JsonObject getHistoryByBuyer(JsonObject request) {
        if (!request.has("buyer_id") || request.get("buyer_id").isJsonNull())
            return buildError("Thiếu trường buyer_id");
        return buildResponse(
                historyDAO.getHistoryByBuyer(request.get("buyer_id").getAsString())
        );
    }

    public JsonObject getHistoryByItem(JsonObject request) {
        if (!request.has("item_id") || request.get("item_id").isJsonNull())
            return buildError("Thiếu trường item_id");
        return buildResponse(
                historyDAO.getHistoryByItem(request.get("item_id").getAsString())
        );
    }

    public JsonObject checkBoughtFromSeller(JsonObject request) {
        if (!request.has("buyer_id") || request.get("buyer_id").isJsonNull())
            return buildError("Thiếu trường buyer_id");
        if (!request.has("seller_id") || request.get("seller_id").isJsonNull())
            return buildError("Thiếu trường seller_id");
        boolean hasBought = historyDAO.hasBuyerPurchasedFromSeller(
                request.get("buyer_id").getAsString(),
                request.get("seller_id").getAsString()
        );
        JsonObject response = new JsonObject();
        response.addProperty("status", "success");
        response.addProperty("hasBought", hasBought);
        return response;
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

    private JsonObject buildError(String message) {
        JsonObject err = new JsonObject();
        err.addProperty("status", "error");
        err.addProperty("message", message);
        return err;
    }
}