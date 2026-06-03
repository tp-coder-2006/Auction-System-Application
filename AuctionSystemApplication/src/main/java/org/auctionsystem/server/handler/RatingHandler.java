package org.auctionsystem.server.handler;

import com.google.gson.JsonObject;
import org.auctionsystem.server.service.RatingService;

/**
 * Handler cho các action liên quan đến đánh giá seller.
 */
public class RatingHandler {

    private final RatingService ratingService = new RatingService();

    public JsonObject handleCheckAlreadyRated(JsonObject request) {
        return ratingService.checkAlreadyRated(request);
    }
}
