package org.auctionsystem.server.handler;

import com.google.gson.JsonObject;
import org.auctionsystem.server.service.ItemHistoryService;

public class HistoryHandler {
    private final ItemHistoryService historyService = new ItemHistoryService();

    public JsonObject handleGetHistoryBySeller(JsonObject request) {
        return historyService.getHistoryBySeller(request);
    }

    public JsonObject handleGetHistoryByBuyer(JsonObject request) {
        return historyService.getHistoryByBuyer(request);
    }

    public JsonObject handleGetHistoryByItem(JsonObject request) {
        return historyService.getHistoryByItem(request);
    }
}