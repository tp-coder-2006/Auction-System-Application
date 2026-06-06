package org.auctionsystem.server.service;

import com.google.gson.JsonObject;
import org.auctionsystem.server.DAO.RatingDAO;

/**
 * Service xử lý các nghiệp vụ liên quan đến đánh giá seller (bảng seller_ratings).
 * Mỗi bidder chỉ được đánh giá 1 seller 1 lần — sau đó chỉ có thể sửa điểm.
 */
public class RatingService {

    private final RatingDAO ratingDAO = new RatingDAO();

    /**
     * Kiểm tra bidder đã đánh giá seller này chưa, trả về điểm nếu đã đánh giá.
     * Response: { status, hasRated, existingScore }
     * existingScore = -1 nghĩa là chưa đánh giá.
     */
    public JsonObject checkAlreadyRated(JsonObject request) {
        if (!request.has("bidder_id") || request.get("bidder_id").isJsonNull())
            return buildError("Thiếu trường bidder_id");
        if (!request.has("seller_id") || request.get("seller_id").isJsonNull())
            return buildError("Thiếu trường seller_id");

        int existingScore = ratingDAO.getExistingRating(
                request.get("bidder_id").getAsString(),
                request.get("seller_id").getAsString()
        );

        JsonObject response = new JsonObject();
        response.addProperty("status", "success");
        response.addProperty("hasRated", existingScore != -1);
        response.addProperty("existingScore", existingScore);
        return response;
    }

    private JsonObject buildError(String message) {
        JsonObject err = new JsonObject();
        err.addProperty("status", "error");
        err.addProperty("message", message);
        return err;
    }
}
