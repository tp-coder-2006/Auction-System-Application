package org.auctionsystem.server.service;

import com.google.gson.Gson;
import org.auctionsystem.client.event.EventType;
import org.auctionsystem.server.DAO.BidDAO;
import org.auctionsystem.server.util.GsonConfig;
import com.google.gson.JsonObject;
import org.auctionsystem.model.entities.Item;
import org.auctionsystem.model.enums.ItemStatus;
import org.auctionsystem.server.DAO.ImageDAO;
import org.auctionsystem.server.DAO.ItemDAO;
import org.auctionsystem.server.DAO.UserDAO;
import org.auctionsystem.server.AuctionScheduler;
import org.auctionsystem.server.AdminStatsScheduler;
import org.auctionsystem.server.ConnectedClientRegistry;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ItemService {
    private final ItemDAO  itemDAO  = new ItemDAO();
    private final ImageDAO imageDAO = new ImageDAO();
    private final UserDAO  userDAO  = new UserDAO();
    private final Gson gson = GsonConfig.create();

    // 1. GIỮ NGUYÊN
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

    // 2. GIỮ NGUYÊN
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

    // 3. GIỮ NGUYÊN
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
                response.addProperty("increment", Math.ceil(item.getStartingPrice() * 0.02));
            }
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi máy chủ: " + e.getMessage());
        }
        return response;
    }

    // 4. GIỮ NGUYÊN
    public JsonObject getAllItems(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            ArrayList<Item> items = itemDAO.getAllItems();
            response.addProperty("status", "success");
            response.add("message", gson.toJsonTree(items));
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi máy chủ: " + e.getMessage());
        }
        return response;
    }

    // 5. GIỮ NGUYÊN
    public JsonObject getVisibleItems(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            ArrayList<Item> items = itemDAO.getVisibleItems();
            response.addProperty("status", "success");
            response.add("message", gson.toJsonTree(items));
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi máy chủ: " + e.getMessage());
        }
        return response;
    }

    // 6. GIỮ NGUYÊN
    public JsonObject getAllActiveItems(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            ArrayList<Item> items = itemDAO.getActiveItems();
            response.addProperty("status", "success");
            response.add("message", gson.toJsonTree(items));
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return response;
    }

    // 7. GIỮ NGUYÊN
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

    // 8. GIỮ NGUYÊN
    public JsonObject cancelItem(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            String itemId   = request.get("item_id").getAsString();
            String sellerId = request.get("seller_id").getAsString();

            // Lấy thông tin item trước khi hủy để dùng cho broadcast
            org.auctionsystem.model.entities.Item item = itemDAO.getAItemById(itemId);

            if (itemDAO.cancelItem(itemId, sellerId)) {
                response.addProperty("status", "success");
                response.addProperty("message", "Sản phẩm đã được hủy thành công!");

                // [NEW] Broadcast ITEM_CANCELLED đến tất cả client
                JsonObject event = new JsonObject();
                event.addProperty("event", EventType.ITEM_CANCELLED);
                event.addProperty("item_id",       itemId);
                event.addProperty("seller_id",     sellerId);
                event.addProperty("cancel_reason", "SELLER_CANCELLED");
                if (item != null) {
                    event.addProperty("item_name", item.getName());
                    if (item.getImageUrl() != null) {
                        event.addProperty("image_url", item.getImageUrl());
                    }
                }
                ConnectedClientRegistry.broadcastAll(event);

                // [NEW] Trigger admin stats: item_stats thay đổi
                AdminStatsScheduler.notifyStatsChanged();
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

    // ─── DELETE ITEM (Service layer) ───────────────────────────────────────────
    //
    // Logic mới — phân biệt hard delete / soft delete dựa trên 2 tiêu chí:
    //
    //   HARD DELETE (xóa hẳn khỏi DB):
    //     ✅ item.status == PENDING
    //     ✅ Chưa từng có bid nào đặt cho item này (bidDAO.hasBidForItem == false)
    //     → Item hoàn toàn "sạch", xóa hẳn không ảnh hưởng dữ liệu lịch sử.
    //
    //   SOFT DELETE (ẩn khỏi giao diện, is_active = 0):
    //     ✅ Đã từng có ít nhất 1 bid — cần giữ lại để lịch sử đấu giá còn ý nghĩa
    //     ✅ status phải là PENDING, CANCELLED, hoặc CLOSED (không được xóa khi ACTIVE)
    //     → Record vẫn tồn tại trong DB nhưng bị ẩn, có thể restore.
    //
    //   KHÔNG ĐƯỢC XÓA:
    //     ❌ status == ACTIVE  → đang có phiên đấu giá, không cho phép xóa
    //
    public JsonObject deleteItem(JsonObject request) {
        BidDAO bidDAO= new BidDAO();
        JsonObject response = new JsonObject();
        try {
            String itemId   = request.get("item_id").getAsString();
            String sellerId = request.get("seller_id").getAsString();

            // 1. Kiểm tra item tồn tại
            Item item = itemDAO.getAItemById(itemId);
            if (item == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Không tìm thấy sản phẩm!");
                return response;
            }

            // 2. Kiểm tra quyền sở hữu
            if (!item.getSellerId().equals(sellerId)) {
                response.addProperty("status", "error");
                response.addProperty("message", "Bạn không có quyền xóa sản phẩm này!");
                return response;
            }

            // 3. Chặn xóa item đang trong phiên đấu giá
            if (item.getStatus() == ItemStatus.ACTIVE) {
                response.addProperty("status", "error");
                response.addProperty("message", "Không thể xóa sản phẩm đang trong phiên đấu giá!");
                return response;
            }

            // 4. Kiểm tra item đã từng có bid chưa
            boolean everHadBid = bidDAO.hasBidForItem(itemId);

            boolean success;
            String  successMessage;

            if (!everHadBid && item.getStatus() == ItemStatus.PENDING) {
                // ── HARD DELETE: pending + chưa có bid nào ──────────────────
                success        = itemDAO.hardDeleteItem(itemId, sellerId);
                successMessage = "Xóa sản phẩm thành công!";
                // Xóa file ảnh vật lý sau khi hard delete thành công
                if (success && item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                    ImageService.deleteFileQuietly("auction_images/" + item.getImageUrl());
                }
            } else {
                // ── SOFT DELETE: đã có bid, hoặc pending nhưng có bid lịch sử
                // Chỉ cho phép với status PENDING / CANCELLED / CLOSED
                if (item.getStatus() == ItemStatus.ACTIVE) {
                    // Guard thêm (trường hợp race condition)
                    response.addProperty("status", "error");
                    response.addProperty("message", "Không thể ẩn sản phẩm đang active!");
                    return response;
                }
                success        = itemDAO.softDeleteItem(itemId, sellerId);
                successMessage = "Ẩn sản phẩm thành công! (Lịch sử đấu giá được giữ lại)";
            }

            if (success) {
                response.addProperty("status", "success");
                response.addProperty("message", successMessage);
                response.addProperty("delete_type", everHadBid ? "soft" : "hard");

                // Broadcast để Searching Room xóa item khỏi bảng ngay lập tức
                JsonObject event = new JsonObject();
                event.addProperty("event",   EventType.ITEM_DELETED);
                event.addProperty("item_id", itemId);
                ConnectedClientRegistry.broadcastAll(event);

                AdminStatsScheduler.notifyStatsChanged();
            } else {
                response.addProperty("status", "error");
                response.addProperty("message", "Xóa thất bại! Vui lòng thử lại.");
            }

        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi máy chủ: " + e.getMessage());
        }
        return response;
    }

    // 10. GIỮ NGUYÊN
    public JsonObject restartItemAuction(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            String itemId           = request.get("item_id").getAsString();
            String requesterId      = request.get("owner_id").getAsString();
            double newStartingPrice = request.get("starting_price").getAsDouble();
            String newStartTime     = request.get("start_time").getAsString();
            String newEndTime       = request.get("end_time").getAsString();

            if (!itemDAO.restartItemAuction(itemId, requesterId, newStartingPrice, newStartTime, newEndTime)) {
                response.addProperty("status", "error");
                response.addProperty("message", "Không thể tái khởi động — sai chủ sở hữu hoặc sản phẩm đang active.");
                return response;
            }

            // Upload ảnh mới nếu có
            boolean hasImage = request.has("image_data")
                    && !request.get("image_data").isJsonNull()
                    && !request.get("image_data").getAsString().isBlank()
                    && request.has("extension")
                    && !request.get("extension").isJsonNull();

            if (hasImage) {
                String imageData = request.get("image_data").getAsString();
                String extension = request.get("extension").getAsString().toLowerCase();

                if (!ImageService.isAllowedExtension(extension)) {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Định dạng ảnh không hợp lệ! Chỉ chấp nhận: jpg, jpeg, png, webp");
                    return response;
                }

                byte[] imageBytes = ImageService.decodeBase64(imageData);
                if (imageBytes == null) {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Dữ liệu ảnh base64 không hợp lệ!");
                    return response;
                }
                if (imageBytes.length > 5 * 1024 * 1024L) {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Ảnh quá lớn! Tối đa 5MB.");
                    return response;
                }

                String newImageId  = UUID.randomUUID().toString();
                String newFilename = newImageId + "." + extension;
                String newRelative = "items/" + newFilename;
                String newAbsolute = "auction_images" + "/" + newRelative;

                try {
                    ImageService.writeFile(newAbsolute, imageBytes);
                } catch (IOException ioEx) {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Lỗi ghi file ảnh: " + ioEx.getMessage());
                    return response;
                }

                boolean registered = imageDAO.registerImage(newImageId, newRelative, "item", itemId);
                if (!registered) {
                    ImageService.deleteFileQuietly(newAbsolute);
                    response.addProperty("status", "error");
                    response.addProperty("message", "Lỗi đăng ký ảnh vào database!");
                    return response;
                }

                itemDAO.updateImageUrl(itemId, newRelative);
            }

            // Trả về sellerUsername để client cập nhật currentItem
            Item updatedItem = itemDAO.getAItemById(itemId);
            if (updatedItem != null && updatedItem.getSellerUsername() != null) {
                response.addProperty("seller_username", updatedItem.getSellerUsername());
            }
            response.addProperty("status", "success");
            response.addProperty("message", "Đã tái khởi động phiên đấu giá thành công!");
            response.addProperty("increment", Math.ceil(newStartingPrice * 0.02));

            // Broadcast ITEM_RELISTED đến tất cả client để cập nhật bảng ngay lập tức
            JsonObject event = new JsonObject();
            event.addProperty("event",         EventType.ITEM_RELISTED);
            event.addProperty("item_id",       itemId);
            event.addProperty("starting_price", newStartingPrice);
            event.addProperty("start_time",    newStartTime);
            event.addProperty("end_time",      newEndTime);
            event.addProperty("seller_id",     requesterId);
            if (updatedItem != null) {
                event.addProperty("name", updatedItem.getName());
            }
            ConnectedClientRegistry.broadcastAll(event);
            AdminStatsScheduler.notifyStatsChanged();
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi máy chủ: " + e.getMessage());
        }
        return response;
    }

    // 11. CÓ SỬA ĐỔI: Tách 2 bước ADD_ITEM → UPLOAD_ITEM_IMAGE, rollback đầy đủ
    public JsonObject addItem(JsonObject request) {
        JsonObject response = new JsonObject();
        String itemId = null;  // dùng để rollback nếu upload ảnh thất bại

        try {
            String name          = request.get("name").getAsString();
            String description   = request.get("description").getAsString();
            double startingPrice = request.get("starting_price").getAsDouble();
            String startTime     = request.get("start_time").getAsString();
            String endTime       = request.get("end_time").getAsString();
            String sellerId      = request.get("seller_id").getAsString();

            // Kiểm tra dữ liệu đầu vào
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime start, end;
            try {
                start = LocalDateTime.parse(startTime, fmt);
                end   = LocalDateTime.parse(endTime,   fmt);
            } catch (Exception parseEx) {
                response.addProperty("status", "error");
                response.addProperty("message", "Định dạng thời gian không hợp lệ! Dùng: yyyy-MM-dd HH:mm:ss");
                return response;
            }
            if (!start.isBefore(end)) {
                response.addProperty("status", "error");
                response.addProperty("message", "Thời gian bắt đầu phải trước thời gian kết thúc!");
                return response;
            }
            if (end.isBefore(LocalDateTime.now())) {
                response.addProperty("status", "error");
                response.addProperty("message", "Thời gian kết thúc không được ở quá khứ!");
                return response;
            }
            if (startingPrice <= 0) {
                response.addProperty("status", "error");
                response.addProperty("message", "Giá khởi điểm phải lớn hơn 0!");
                return response;
            }

            // ── Pre-validate extension trước khi INSERT (tránh rollback) ─────
            boolean hasImage = request.has("image_data")
                    && !request.get("image_data").isJsonNull()
                    && !request.get("image_data").getAsString().isBlank()
                    && request.has("extension")
                    && !request.get("extension").isJsonNull();

            if (hasImage) {
                String extCheck = request.get("extension").getAsString().toLowerCase();
                if (!ImageService.isAllowedExtension(extCheck)) {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Định dạng ảnh không hợp lệ! Chỉ chấp nhận: jpg, jpeg, png, webp");
                    return response;
                }

                // ── Pre-validate kích thước ảnh TRƯỚC khi INSERT vào DB ──────
                byte[] preCheckBytes = ImageService.decodeBase64(
                        request.get("image_data").getAsString());
                if (preCheckBytes == null) {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Dữ liệu ảnh base64 không hợp lệ!");
                    return response;
                }
                if (preCheckBytes.length > 5 * 1024 * 1024L) {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Ảnh quá lớn! Tối đa 5MB.");
                    return response;
                }
            }

            // ── Bước 1: INSERT item với image_url = null ──────────────────────
            itemId = itemDAO.addItem(name, description, startingPrice, startTime, endTime, sellerId, null);
            if (itemId == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Thêm sản phẩm thất bại!");
                return response;
            }

            // ── Bước 2: Upload ảnh (nếu có) ───────────────────────────────────
            String finalImageUrl = null;

            if (hasImage) {
                String imageData = request.get("image_data").getAsString();
                String extension = request.get("extension").getAsString().toLowerCase();

                // Validate extension
                if (!ImageService.isAllowedExtension(extension)) {
                    // Rollback: xóa item vừa thêm
                    itemDAO.hardDeleteItem(itemId, sellerId);
                    response.addProperty("status", "error");
                    response.addProperty("message", "Định dạng ảnh không hợp lệ! Chỉ chấp nhận: jpg, jpeg, png, webp");
                    return response;
                }

                // Decode base64
                byte[] imageBytes = ImageService.decodeBase64(imageData);
                if (imageBytes == null) {
                    itemDAO.hardDeleteItem(itemId, sellerId);
                    response.addProperty("status", "error");
                    response.addProperty("message", "Dữ liệu ảnh base64 không hợp lệ!");
                    return response;
                }
                if (imageBytes.length > 5 * 1024 * 1024L) {
                    itemDAO.hardDeleteItem(itemId, sellerId);
                    response.addProperty("status", "error");
                    response.addProperty("message", "Ảnh quá lớn! Tối đa 5MB.");
                    return response;
                }

                // Ghi file lên disk
                String newImageId  = UUID.randomUUID().toString();
                String newFilename = newImageId + "." + extension;
                String newRelative = "items/" + newFilename;
                String newAbsolute = "auction_images" + "/" + newRelative;

                try {
                    ImageService.writeFile(newAbsolute, imageBytes);
                } catch (IOException ioEx) {
                    // Rollback: xóa item vừa thêm
                    itemDAO.hardDeleteItem(itemId, sellerId);
                    response.addProperty("status", "error");
                    response.addProperty("message", "Lỗi ghi file ảnh: " + ioEx.getMessage());
                    return response;
                }

                // Đăng ký metadata ảnh vào DB
                boolean registered = imageDAO.registerImage(newImageId, newRelative, "item", itemId);
                if (!registered) {
                    // Rollback: xóa file vừa ghi + xóa item vừa thêm
                    ImageService.deleteFileQuietly(newAbsolute);
                    itemDAO.hardDeleteItem(itemId, sellerId);
                    response.addProperty("status", "error");
                    response.addProperty("message", "Lỗi đăng ký ảnh vào database!");
                    return response;
                }

                // Cập nhật image_url vào bảng items
                boolean updated = itemDAO.updateImageUrl(itemId, newRelative);
                if (!updated) {
                    // Rollback: xóa file + xóa record ảnh + xóa item
                    ImageService.deleteFileQuietly(newAbsolute);
                    imageDAO.deleteImageRecord(newRelative);
                    itemDAO.hardDeleteItem(itemId, sellerId);
                    response.addProperty("status", "error");
                    response.addProperty("message", "Lỗi cập nhật image_url cho sản phẩm!");
                    return response;
                }

                finalImageUrl = newRelative;
            }

            // ── Thành công ────────────────────────────────────────────────────
            response.addProperty("status", "success");
            response.addProperty("message", "Thêm sản phẩm thành công!");
            response.addProperty("item_id", itemId);
            response.addProperty("increment", Math.ceil(startingPrice * 0.02));
            if (finalImageUrl != null) {
                response.addProperty("image_url", finalImageUrl);
            }

            // Broadcast để Searching Room thêm item mới vào bảng ngay lập tức
            String sellerUsername = sellerId;
            try {
                org.auctionsystem.model.entities.User seller = userDAO.getProfileById(sellerId);
                if (seller != null && seller.getUsername() != null) sellerUsername = seller.getUsername();
            } catch (Exception ignored) {}

            JsonObject event = new JsonObject();
            event.addProperty("event",                "ITEM_ADDED");
            event.addProperty("item_id",              itemId);
            event.addProperty("name",                 name);
            event.addProperty("status",               "PENDING");
            event.addProperty("starting_price",       startingPrice);
            event.addProperty("current_highest_price", startingPrice);
            event.addProperty("start_time",           startTime);
            event.addProperty("end_time",             endTime);
            event.addProperty("seller_id",            sellerId);
            event.addProperty("seller_name",          sellerUsername);
            if (finalImageUrl != null) event.addProperty("image_url", finalImageUrl);
            ConnectedClientRegistry.broadcastAll(event);
            AdminStatsScheduler.notifyStatsChanged();

        } catch (Exception e) {
            // Nếu đã tạo item nhưng xảy ra lỗi ngoài ý muốn → rollback xóa item
            if (itemId != null) {
                try {
                    String sellerId = request.has("seller_id") ? request.get("seller_id").getAsString() : null;
                    if (sellerId != null) itemDAO.hardDeleteItem(itemId, sellerId);
                } catch (Exception ignored) {}
            }
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi máy chủ: " + e.getMessage());
        }
        return response;
    }


    // 12. CÓ SỬA ĐỔI: Tích hợp upload ảnh, 1 request duy nhất
    public JsonObject updateItem(JsonObject request) {
        JsonObject response = new JsonObject();
        String newAbsolutePath = null; // dùng để rollback file nếu lỗi sau khi ghi

        try {
            String itemId        = request.get("item_id").getAsString();
            String name          = request.get("name").getAsString();
            String description   = request.get("description").getAsString();
            double startingPrice = request.get("starting_price").getAsDouble();
            String startTime     = request.get("start_time").getAsString();
            String endTime       = request.get("end_time").getAsString();
            String sellerId      = request.get("seller_id").getAsString();

            // ── Xử lý ảnh (nếu có ảnh mới) ──────────────────────────────────
            boolean hasImage = request.has("image_data")
                    && !request.get("image_data").isJsonNull()
                    && !request.get("image_data").getAsString().isBlank()
                    && request.has("extension")
                    && !request.get("extension").isJsonNull();

            String finalImageUrl = null;

            if (hasImage) {
                String imageData = request.get("image_data").getAsString();
                String extension = request.get("extension").getAsString().toLowerCase();

                if (!ImageService.isAllowedExtension(extension)) {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Định dạng ảnh không hợp lệ! Chỉ chấp nhận: jpg, jpeg, png, webp");
                    return response;
                }

                byte[] imageBytes = ImageService.decodeBase64(imageData);
                if (imageBytes == null) {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Dữ liệu ảnh base64 không hợp lệ!");
                    return response;
                }
                if (imageBytes.length > 5 * 1024 * 1024L) {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Ảnh quá lớn! Tối đa 5MB.");
                    return response;
                }

                // Tra cứu ảnh cũ trước khi làm bất cứ điều gì
                String oldPath = imageDAO.getCurrentItemImagePath(itemId);

                // Ghi file mới lên disk
                String newImageId  = UUID.randomUUID().toString();
                String newRelative = "items/" + newImageId + "." + extension;
                String newAbsolute = "auction_images/" + newRelative;
                newAbsolutePath    = newAbsolute;

                try {
                    ImageService.writeFile(newAbsolute, imageBytes);
                } catch (IOException ioEx) {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Lỗi ghi file ảnh: " + ioEx.getMessage());
                    return response;
                }

                // Đăng ký metadata ảnh mới vào DB
                boolean registered = imageDAO.registerImage(newImageId, newRelative, "item", itemId);
                if (!registered) {
                    ImageService.deleteFileQuietly(newAbsolute);
                    response.addProperty("status", "error");
                    response.addProperty("message", "Lỗi đăng ký ảnh vào database!");
                    return response;
                }

                // Dọn ảnh cũ (chỉ sau khi ảnh mới đã được đăng ký thành công)
                if (oldPath != null) {
                    ImageService.deleteFileQuietly("auction_images/" + oldPath);
                    imageDAO.deleteImageRecord(oldPath);
                }

                finalImageUrl   = newRelative;
                newAbsolutePath = null; // đã xử lý xong, không cần rollback file nữa
            } else {
                // Không có ảnh mới → giữ nguyên ảnh cũ trong DB
                finalImageUrl = imageDAO.getCurrentItemImagePath(itemId);
            }

            // ── Cập nhật item vào DB ──────────────────────────────────────────
            if (itemDAO.updateItem(itemId, name, description, startingPrice, startTime, endTime, sellerId, finalImageUrl)) {
                response.addProperty("status", "success");
                response.addProperty("message", "Cập nhật sản phẩm thành công!");
                response.addProperty("increment", Math.ceil(startingPrice * 0.02));
                if (finalImageUrl != null) {
                    response.addProperty("image_url", finalImageUrl);
                }

                // Broadcast để Searching Room cập nhật item ngay lập tức
                JsonObject event = new JsonObject();
                event.addProperty("event",          "ITEM_UPDATED");
                event.addProperty("item_id",        itemId);
                event.addProperty("name",           name);
                event.addProperty("starting_price",       startingPrice);
                event.add("current_highest_price", com.google.gson.JsonNull.INSTANCE);
                event.addProperty("start_time",     startTime);
                event.addProperty("end_time",       endTime);
                if (finalImageUrl != null) event.addProperty("image_url", finalImageUrl);
                ConnectedClientRegistry.broadcastAll(event);
                AdminStatsScheduler.notifyStatsChanged();
            } else {
                // Rollback: xóa ảnh mới nếu vừa upload
                if (hasImage && finalImageUrl != null) {
                    ImageService.deleteFileQuietly("auction_images/" + finalImageUrl);
                    imageDAO.deleteImageRecord(finalImageUrl);
                }
                response.addProperty("status", "error");
                response.addProperty("message", "Cập nhật thất bại — không có quyền hoặc item đang active.");
            }

        } catch (Exception e) {
            if (newAbsolutePath != null) ImageService.deleteFileQuietly(newAbsolutePath);
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi máy chủ: " + e.getMessage());
        }
        return response;
    }

    // 13. GIỮ NGUYÊN
    public JsonObject restoreHiddenItem(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            String itemId   = request.get("item_id").getAsString();
            String sellerId = request.get("seller_id").getAsString();
            if (itemDAO.restoreHiddenItem(itemId, sellerId)) {
                response.addProperty("status", "success");
                response.addProperty("message", "Khôi phục sản phẩm thành công!");
            } else {
                response.addProperty("status", "error");
                response.addProperty("message", "Không thể khôi phục — sản phẩm không tồn tại hoặc không hợp lệ.");
            }
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi máy chủ: " + e.getMessage());
        }
        return response;
    }

    // 14. GIỮ NGUYÊN
    public JsonObject searchItems(JsonObject request) {
        JsonObject response = new JsonObject();
        try {
            String keyword = request.get("keyword").getAsString();
            List<Item> items = itemDAO.searchItems(keyword);
            response.addProperty("status", "success");
            response.add("message", gson.toJsonTree(items));
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi máy chủ: " + e.getMessage());
        }
        return response;
    }
}