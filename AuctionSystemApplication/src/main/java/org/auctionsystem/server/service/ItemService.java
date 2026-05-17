package org.auctionsystem.server.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.auctionsystem.model.entities.Item;
import org.auctionsystem.server.DAO.ItemDAO;

import java.util.ArrayList;
import java.util.List;

public class ItemService {
    private final ItemDAO itemDAO = new ItemDAO();
    private final Gson gson = new Gson();

    public JsonObject getItemsBySeller(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            List<Item> items = itemDAO.getItemsBySeller(request.get("seller_id").getAsString());
            response.addProperty("status", "success");
            response.add("message", gson.toJsonTree(items));
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi máy chủ: " + e.getMessage());
        }
        return response;
    }

    public JsonObject getItemsByOwner(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            List<Item> items = itemDAO.getItemsByOwner(request.get("owner_id").getAsString());
            response.addProperty("status", "success");
            response.add("message", gson.toJsonTree(items));
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi máy chủ: " + e.getMessage());
        }
        return response;
    }

    public JsonObject getAItemById(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            Item item = itemDAO.getAItemById(request.get("item_id").getAsString());
            if (item == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Không tìm thấy sản phẩm!");
            } else {
                response.addProperty("status", "success");
                response.add("message", gson.toJsonTree(item));
            }
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi máy chủ: " + e.getMessage());
        }
        return response;
    }

    public JsonObject getAllActiveItems(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            ArrayList<Item> items = itemDAO.getActiveItems(); // chỉ lấy active
            response.addProperty("status", "success");
            response.add("message", gson.toJsonTree(items));
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return response;
    }

    public JsonObject updateItemStatus(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            itemDAO.autoUpdateItemStatuses();
            response.addProperty("status", "success");
            response.addProperty("message", "Cập nhật trạng thái thành công!");
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi máy chủ: " + e.getMessage());
        }
        return response;
    }

    public JsonObject cancelItem(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            String itemId   = request.get("item_id").getAsString();
            String sellerId = request.get("seller_id").getAsString();
            if (itemDAO.cancelItem(itemId, sellerId)) {
                response.addProperty("status", "success");
                response.addProperty("message", "Sản phẩm đã được hủy thành công!");
            } else {
                response.addProperty("status", "error");
                response.addProperty("message", "Không thể hủy: không có quyền hoặc sản phẩm không hợp lệ.");
            }
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi máy chủ: " + e.getMessage());
        }
        return response;
    }

    public JsonObject restartItemAuction(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            String itemId          = request.get("item_id").getAsString();
            String requesterId     = request.get("owner_id").getAsString(); // dùng owner_id
            double newStartingPrice = request.get("starting_price").getAsDouble();
            String newStartTime    = request.get("start_time").getAsString();
            String newEndTime      = request.get("end_time").getAsString();

            if (itemDAO.restartItemAuction(itemId, requesterId, newStartingPrice, newStartTime, newEndTime)) {
                response.addProperty("status", "success");
                response.addProperty("message", "Đã tái khởi động phiên đấu giá thành công!");
            } else {
                response.addProperty("status", "error");
                response.addProperty("message", "Không thể tái khởi động — sai chủ sở hữu hoặc sản phẩm đang active.");
            }
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi máy chủ: " + e.getMessage());
        }
        return response;
    }

    public JsonObject addItem(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            String name         = request.get("name").getAsString();
            String description  = request.get("description").getAsString();
            double startingPrice = request.get("starting_price").getAsDouble();
            String startTime    = request.get("start_time").getAsString();
            String endTime      = request.get("end_time").getAsString();
            String sellerId     = request.get("seller_id").getAsString();

            String itemId = itemDAO.addItem(name, description, startingPrice, startTime, endTime, sellerId);
            if (itemId != null) {
                response.addProperty("status", "success");
                response.addProperty("message", "Thêm sản phẩm thành công!");
                response.addProperty("item_id", itemId);
            } else {
                response.addProperty("status", "error");
                response.addProperty("message", "Thêm sản phẩm thất bại!");
            }
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi máy chủ: " + e.getMessage());
        }
        return response;
    }

    public JsonObject updateItem(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            String itemId       = request.get("item_id").getAsString();
            String name         = request.get("name").getAsString();
            String description  = request.get("description").getAsString();
            double startingPrice = request.get("starting_price").getAsDouble();
            String startTime    = request.get("start_time").getAsString();
            String endTime      = request.get("end_time").getAsString();
            String sellerId     = request.get("seller_id").getAsString();

            if (itemDAO.updateItem(itemId, name, description, startingPrice, startTime, endTime, sellerId)) {
                response.addProperty("status", "success");
                response.addProperty("message", "Cập nhật sản phẩm thành công!");
            } else {
                response.addProperty("status", "error");
                response.addProperty("message", "Cập nhật thất bại — không có quyền hoặc item đang active.");
            }
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi máy chủ: " + e.getMessage());
        }
        return response;
    }

    public JsonObject deleteItem(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            String itemId   = request.get("item_id").getAsString();
            String sellerId = request.get("seller_id").getAsString();

            if (itemDAO.deleteItem(itemId, sellerId)) {
                response.addProperty("status", "success");
                response.addProperty("message", "Xóa sản phẩm thành công!");
            } else {
                response.addProperty("status", "error");
                response.addProperty("message", "Xóa thất bại — không có quyền hoặc item đang active.");
            }
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi máy chủ: " + e.getMessage());
        }
        return response;
    }
}