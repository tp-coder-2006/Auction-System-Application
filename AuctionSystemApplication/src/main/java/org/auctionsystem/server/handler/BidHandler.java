package org.auctionsystem.server.handler;

import com.google.gson.JsonObject;
import org.auctionsystem.server.service.BidService;

public class BidHandler {
    private final BidService bidService = new BidService();

    public JsonObject handlePlaceBid(JsonObject request) {
        return bidService.placeBid(request);
    }

    public JsonObject handleGetBidHistory(JsonObject request) {
        return bidService.getBidHistory(request);
    }

    public JsonObject handleGetBidHistoryByItem(JsonObject request) {
        return bidService.getBidHistoryByItem(request);
    }

    public JsonObject handleSettleBid(JsonObject request) {
        return bidService.settleBid(request);
    }
}