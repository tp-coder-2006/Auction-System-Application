package org.auctionsystem.server.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import org.auctionsystem.client.event.EventType;
import org.auctionsystem.server.AdminStatsScheduler;
import org.auctionsystem.server.ConnectedClientRegistry;
import org.auctionsystem.server.util.GsonConfig;
import com.google.gson.JsonObject;
import org.auctionsystem.model.entities.Transaction;
import org.auctionsystem.model.entities.User;
import org.auctionsystem.server.DAO.AdminDAO;
import org.auctionsystem.server.DAO.ItemDAO;
import org.auctionsystem.server.DAO.TransactionDAO;
import org.auctionsystem.server.session.SessionManager;
import org.auctionsystem.server.session.UserSession;

import java.util.List;

public class AdminService {

    private final AdminDAO adminDAO = new AdminDAO();
    private final ItemDAO  itemDAO  = new ItemDAO();
    private final Gson     gson     = GsonConfig.create();

    // ─── HELPER — KIỂM TRA QUYỀN ADMIN ────────────────────────────────────────

    private JsonObject requireAdmin(JsonObject request) {
        if (!request.has("session_id") || request.get("session_id").isJsonNull()) {
            return errorResponse("NO_SESSION", "Tài khoản chưa đăng nhập!");
        }

        String sessionId = request.get("session_id").getAsString();
        UserSession session = SessionManager.getSession(sessionId);

        if (session == null) {
            return errorResponse("SESSION_EXPIRED", "Phiên đăng nhập đã hết hạn.");
        }

        if (!"ADMIN".equalsIgnoreCase(session.getRole())) {
            return errorResponse("FORBIDDEN", "Bạn không có quyền thực hiện hành động này!");
        }

        return null;
    }

    private JsonObject errorResponse(String code, String message) {
        JsonObject err = new JsonObject();
        err.addProperty("status", "error");
        err.addProperty("code", code);
        err.addProperty("message", message);
        return err;
    }

    // ─── QUẢN LÝ USER ─────────────────────────────────────────────────────────

    /**
     * Tra cứu thông tin người dùng theo username (bao gồm cả avatar_url).
     */
    public JsonObject getUserByUsername(JsonObject request) {
        JsonObject err = requireAdmin(request);
        if (err != null) return err;

        JsonObject response = new JsonObject();
        try {
            String username = request.get("username").getAsString();
            User user = adminDAO.getUserByUsername(username);

            if (user == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Không tìm thấy người dùng!");
            } else {
                JsonObject userJson = gson.toJsonTree(user).getAsJsonObject();
                userJson.remove("password");
                response.addProperty("status", "success");
                response.add("information", userJson);
            }
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return response;
    }

    /**
     * Khóa tài khoản người dùng.
     * Sau khi ban thành công:
     *   1. Gửi event BANNED trực tiếp đến client của user bị ban (nếu đang online).
     *   2. Xóa toàn bộ session của user đó.
     *   3. Trigger cập nhật stats admin (user_stats thay đổi).
     */
    public JsonObject banUser(JsonObject request) {
        JsonObject err = requireAdmin(request);
        if (err != null) return err;

        JsonObject response = new JsonObject();
        try {
            User target = null;
            if (request.has("user_id")) {
                target = adminDAO.getUserById(request.get("user_id").getAsString());
            } else if (request.has("username")) {
                target = adminDAO.getUserByUsername(request.get("username").getAsString());
            }

            if (target == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Không tìm thấy người dùng!");
                return response;
            }

            String adminId = SessionManager.getSession(request.get("session_id").getAsString()).getUserId();
            if (adminId.equals(target.getId())) {
                response.addProperty("status", "error");
                response.addProperty("message", "Admin không thể tự khóa chính mình!");
                return response;
            }

            if (adminDAO.setActiveStatus(target.getId(), false)) {
                final String targetId = target.getId();

                // [NEW] Gửi event BANNED đến client trước khi xóa session,
                // để client còn nhận được thông báo khi socket vẫn đang mở.
                String targetSessionId = SessionManager.findSessionIdByUserId(targetId);
                if (targetSessionId != null) {
                    JsonObject bannedEvent = new JsonObject();
                    bannedEvent.addProperty("event", EventType.BANNED);
                    bannedEvent.addProperty("message", "Tài khoản của bạn đã bị khóa bởi quản trị viên.");
                    ConnectedClientRegistry.sendTo(targetSessionId, bannedEvent);
                }

                // Xóa toàn bộ session của user bị ban
                SessionManager.getAllSessions().stream()
                        .filter(s -> s.getUserId().equals(targetId))
                        .forEach(s -> SessionManager.removeSession(s.getSessionId()));

                response.addProperty("status", "success");
                response.addProperty("message", "Đã khóa tài khoản " + target.getUsername() + " thành công!");

                // [NEW] Trigger cập nhật stats: số user active thay đổi
                AdminStatsScheduler.notifyStatsChanged();
            } else {
                response.addProperty("status", "error");
                response.addProperty("message", "Khóa thất bại! User có thể là admin.");
            }
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi: " + e.getMessage());
        }
        return response;
    }

    /**
     * Mở khóa tài khoản người dùng.
     * Sau khi unban thành công → trigger cập nhật stats admin.
     */
    public JsonObject unbanUser(JsonObject request) {
        JsonObject err = requireAdmin(request);
        if (err != null) return err;

        JsonObject response = new JsonObject();
        try {
            User target = null;
            if (request.has("user_id")) {
                target = adminDAO.getUserById(request.get("user_id").getAsString());
            } else if (request.has("username")) {
                target = adminDAO.getUserByUsername(request.get("username").getAsString());
            }

            if (target == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Không tìm thấy người dùng!");
                return response;
            }

            if (adminDAO.setActiveStatus(target.getId(), true)) {
                response.addProperty("status", "success");
                response.addProperty("message", "Đã mở khóa tài khoản " + target.getUsername() + " thành công!");

                // [NEW] Trigger cập nhật stats: số user active thay đổi
                AdminStatsScheduler.notifyStatsChanged();
            } else {
                response.addProperty("status", "error");
                response.addProperty("message", "Mở khóa thất bại!");
            }
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi: " + e.getMessage());
        }
        return response;
    }

    // ─── TRUY VẤN DANH SÁCH ──────────────────────────────────────────────────

    public JsonObject getAllUsers(JsonObject request) {
        JsonObject err = requireAdmin(request);
        if (err != null) return err;

        JsonObject response = new JsonObject();
        try {
            List<User> users = adminDAO.getAllUsers();
            JsonArray arr = new JsonArray();
            for (User u : users) {
                JsonObject userJson = gson.toJsonTree(u).getAsJsonObject();
                userJson.remove("password");
                arr.add(userJson);
            }
            response.addProperty("status", "success");
            response.add("message", arr);
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi: " + e.getMessage());
        }
        return response;
    }

    public JsonObject getAllItems(JsonObject request) {
        JsonObject err = requireAdmin(request);
        if (err != null) return err;

        JsonObject response = new JsonObject();
        try {
            java.util.ArrayList<org.auctionsystem.model.entities.Item> items = itemDAO.getAllItems();
            response.addProperty("status", "success");
            response.add("message", gson.toJsonTree(items));
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi: " + e.getMessage());
        }
        return response;
    }

    /**
     * Admin xóa cứng (hard delete) một item khỏi DB — không phân biệt trạng thái.
     * Xóa toàn bộ dữ liệu liên quan: bids, item_ownership_history, images metadata.
     * Transactions tài chính được giữ lại (related_item_id → NULL).
     */
    public JsonObject deleteItem(JsonObject request) {
        JsonObject err = requireAdmin(request);
        if (err != null) return err;

        JsonObject response = new JsonObject();
        try {
            String itemId = request.get("item_id").getAsString();
            if (itemDAO.adminHardDeleteItem(itemId)) {
                response.addProperty("status", "success");
                response.addProperty("message", "Đã xóa vĩnh viễn sản phẩm và toàn bộ dữ liệu liên quan thành công!");

                // Broadcast để Searching Room xóa item khỏi bảng ngay lập tức
                JsonObject event = new JsonObject();
                event.addProperty("event",   EventType.ITEM_DELETED);
                event.addProperty("item_id", itemId);
                ConnectedClientRegistry.broadcastAll(event);

                AdminStatsScheduler.notifyStatsChanged();
            } else {
                response.addProperty("status", "error");
                response.addProperty("message", "Không thể xóa sản phẩm (không tìm thấy hoặc lỗi DB).");
            }
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi: " + e.getMessage());
        }
        return response;
    }

    // ─── THỐNG KÊ ─────────────────────────────────────────────────────────────

    public JsonObject getSystemStats(JsonObject request) {
        JsonObject err = requireAdmin(request);
        if (err != null) return err;

        JsonObject response = new JsonObject();
        try {
            JsonObject data = new JsonObject();
            data.add("user_stats",        gson.toJsonTree(adminDAO.getUserStats()));
            data.add("item_stats",        gson.toJsonTree(adminDAO.getItemStats()));
            data.add("transaction_stats", gson.toJsonTree(adminDAO.getTransactionStats()));
            data.add("top_sellers",       gson.toJsonTree(adminDAO.getTopSellers(5)));
            data.add("top_bidders",       gson.toJsonTree(adminDAO.getTopBidders(5)));

            response.addProperty("status", "success");
            response.add("message", data);
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi: " + e.getMessage());
        }
        return response;
    }

    public JsonObject getItemTrend(JsonObject request) {
        JsonObject err = requireAdmin(request);
        if (err != null) return err;
        JsonObject response = new JsonObject();
        try {
            int months = request.has("months") ? request.get("months").getAsInt() : 6;
            response.addProperty("status", "success");
            response.add("message", gson.toJsonTree(adminDAO.getItemCountByMonth(months)));
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi: " + e.getMessage());
        }
        return response;
    }

    public JsonObject getRevenueTrend(JsonObject request) {
        JsonObject err = requireAdmin(request);
        if (err != null) return err;
        JsonObject response = new JsonObject();
        try {
            int months = request.has("months") ? request.get("months").getAsInt() : 6;
            response.addProperty("status", "success");
            response.add("message", gson.toJsonTree(adminDAO.getRevenueByMonth(months)));
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi: " + e.getMessage());
        }
        return response;
    }

    // ─── GIAO DỊCH ────────────────────────────────────────────────────────────


    public JsonObject getTransactionsByUser(JsonObject request) {
        JsonObject err = requireAdmin(request);
        if (err != null) return err;
        JsonObject response = new JsonObject();
        try {
            String userId = request.get("user_id").getAsString();
            response.addProperty("status", "success");
            response.add("message", gson.toJsonTree(new TransactionDAO().getTransactionsByUser(userId)));
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi: " + e.getMessage());
        }
        return response;
    }

    public JsonObject getTransactionsByItem(JsonObject request) {
        JsonObject err = requireAdmin(request);
        if (err != null) return err;
        JsonObject response = new JsonObject();
        try {
            String itemId = request.get("item_id").getAsString();
            response.addProperty("status", "success");
            response.add("message", gson.toJsonTree(new TransactionDAO().getTransactionsByItem(itemId)));
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi: " + e.getMessage());
        }
        return response;
    }

}