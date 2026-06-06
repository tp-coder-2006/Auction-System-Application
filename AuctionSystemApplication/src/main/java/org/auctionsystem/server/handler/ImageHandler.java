package org.auctionsystem.server.handler;

import com.google.gson.JsonObject;
import org.auctionsystem.server.service.ImageService;

/**
 * ImageHandler — Điều phối các hành động liên quan đến hình ảnh.
 *
 * Hành động được hỗ trợ:
 *   GET_IMAGE — client gửi image_url, server trả về ảnh base64
 *
 * Lưu ý: UPLOAD_AVATAR và UPLOAD_ITEM_IMAGE đã được tích hợp trực tiếp
 * vào UPDATE_PROFILE (UserService) và ADD_ITEM / UPDATE_ITEM (ItemService).
 */
public class ImageHandler {

    private final ImageService imageService = new ImageService();

    public JsonObject handleGetImage(JsonObject request) {
        return imageService.getImage(request);
    }
}