package org.auctionsystem.server.handler;

import com.google.gson.JsonObject;
import org.auctionsystem.server.service.BidService;

public class BidHandler {
    private final BidService bidService = new BidService();

    public JsonObject handlePlaceBid(JsonObject request) {
        return bidService.placeBid(request);
    }

    public JsonObject handleGetBidsByBidder(JsonObject request) {
        return bidService.getBidsByBidder(request);
    }

    public JsonObject handleGetBidsByBidderAndItem(JsonObject request) {
        return bidService.getBidsByBidderAndItem(request);
    }

    public JsonObject handleSettleBid(JsonObject request) {
        return bidService.settleBid(request);
    }

    public JsonObject handleGetBidResultsByBidder(JsonObject request) {
        return bidService.getBidResultsByBidder(request);
    }

    public JsonObject handleGetActiveBidsByBidder(JsonObject request) {
        return bidService.getActiveBidsByBidder(request);
    }

    public JsonObject handleGetHighestBidByItem(JsonObject request) {
        return bidService.getHighestBidByItem(request);
    }

    public JsonObject handleGetActiveBidsByItem(JsonObject request) {
        return bidService.getActiveBidsByItem(request);
    }

    public JsonObject handleGetAllBidsByItem(JsonObject request) {
        return bidService.getAllBidsByItem(request);
    }
}
