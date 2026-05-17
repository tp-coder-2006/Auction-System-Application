package org.auctionsystem.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.auctionsystem.server.handler.BidHandler;
import org.auctionsystem.server.handler.HistoryHandler;
import org.auctionsystem.server.handler.ItemHandler;
import org.auctionsystem.server.handler.UserHandler;
import org.auctionsystem.server.session.SessionManager;
import org.auctionsystem.server.session.UserSession;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final Gson gson;

    // Khởi tạo handler 1 lần, dùng lại cho mọi request
    private final UserHandler    userHandler    = new UserHandler();
    private final ItemHandler    itemHandler    = new ItemHandler();
    private final BidHandler     bidHandler     = new BidHandler();
    private final HistoryHandler historyHandler = new HistoryHandler();

    public ClientHandler(Socket socket) {
        this.socket = socket;
        this.gson   = new Gson();
    }

    @Override
    public void run() {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer    = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String jsonMessage;
            while ((jsonMessage = reader.readLine()) != null) {
                System.out.println("📩 [" + socket.getInetAddress() + "] " + jsonMessage);

                JsonObject request  = gson.fromJson(jsonMessage, JsonObject.class);
                String action       = request.has("action") ? request.get("action").getAsString() : "UNKNOWN";
                JsonObject response = route(action, request);

                // Đảm bảo response luôn có action để client nhận biết
                response.addProperty("action", action);
                writer.println(response);
            }

        } catch (Exception e) {
            System.err.println("❌ Lỗi client " + socket.getInetAddress() + ": " + e.getMessage());
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
            System.out.println("👋 Client ngắt kết nối: " + socket.getInetAddress());
        }
    }

    // ╔══════════════════════════════════════════════════════════════════════╗
    // ║  [MỚI] requireSession — helper kiểm tra session trước nghiệp vụ    ║
    // ╚══════════════════════════════════════════════════════════════════════╝
    /**
     * Kiểm tra xem session_id trong request có tồn tại và còn hiệu lực
     * trong ConcurrentHashMap của SessionManager hay không.
     *
     * Trả về:
     *   - null          → session hợp lệ, cho phép tiếp tục xử lý nghiệp vụ
     *   - JsonObject    → lỗi (chưa đăng nhập / hết hạn), trả thẳng về client
     *
     * Lý do thêm method này:
     *   Trước đây các action như GET_PROFILE, PLACE_BID, ADD_ITEM... được xử lý
     *   ngay mà không kiểm tra người dùng có đang đăng nhập hay không.
     *   Bất kỳ ai gửi đúng định dạng JSON đều có thể thực hiện được.
     */
    private JsonObject requireSession(JsonObject request) {

        // Nhánh 1: không có session_id trong request
        if (!request.has("session_id") || request.get("session_id").isJsonNull()) {
            JsonObject err = new JsonObject();
            err.addProperty("status", "error");
            err.addProperty("code", "NO_SESSION");
            err.addProperty("message", "Tài khoản chưa đăng nhập!");
            return err;
        }

        String sessionId = request.get("session_id").getAsString();
        UserSession session = SessionManager.getSession(sessionId);

        // Nhánh 2: null (đã xóa khỏi map) HOẶC còn trong map nhưng isExpired()
        // → gộp chung vì client không cần phân biệt, xử lý như nhau
        if (session == null || session.isExpired()) {
            if (session != null) SessionManager.removeSession(sessionId); // dọn khe hở
            JsonObject err = new JsonObject();
            err.addProperty("status", "expired");
            err.addProperty("code", "SESSION_EXPIRED");
            err.addProperty("message", "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại!");
            return err;
        }

        // Hợp lệ
        session.resetLastActiveTime();
        return null;
    }
    // ══════════════════════════════════════════════════════════════════════

    private JsonObject route(String action, JsonObject request) {
        try {
            return switch (action) {

                // ── PING: giữ session sống ─────────────────────────────────────────
                // [SỬA] Thêm null-check cho session_id để tránh NullPointerException
                // (trước đây: gọi thẳng request.get("session_id").getAsString() không kiểm tra)
                case "PING" -> {
                    if (request.has("session_id") && !request.get("session_id").isJsonNull()) {
                        UserSession userSession = SessionManager.getSession(
                                request.get("session_id").getAsString()
                        );
                        if (userSession != null) {
                            userSession.resetLastActiveTime();
                        }
                    }
                    yield new JsonObject();
                }

                case "CHECK_PING" -> {
                    JsonObject err = requireSession(request);
                    if (err != null) {
                        // session không còn → trả nguyên err (đã có status: "expired")
                        yield err;
                    }
                    JsonObject ok = new JsonObject();
                    ok.addProperty("status", "alive");
                    yield ok;
                }

                // ── Không cần session (public endpoint) ───────────────────────────
                // [GIỮ NGUYÊN] LOGIN và REGISTER không cần kiểm tra session
                case "LOGIN"    -> userHandler.handleLogin(request);
                case "REGISTER" -> userHandler.handleRegister(request);

                // ╔══════════════════════════════════════════════════════════════╗
                // ║  [MỚI] LOGOUT — xóa session khỏi ConcurrentHashMap          ║
                // ╚══════════════════════════════════════════════════════════════╝
                // Trước đây không có action này. Client chỉ chuyển màn hình,
                // session vẫn còn nằm trong map cho đến khi tự hết hạn (30 phút).
                case "LOGOUT" -> {
                    JsonObject logoutResp = new JsonObject();
                    if (request.has("session_id") && !request.get("session_id").isJsonNull()) {
                        // [MỚI] Xóa session ngay lập tức, không chờ hết hạn tự nhiên
                        SessionManager.removeSession(request.get("session_id").getAsString());
                    }
                    logoutResp.addProperty("status", "success");
                    logoutResp.addProperty("message", "Đăng xuất thành công!");
                    yield logoutResp;
                }
                // ══════════════════════════════════════════════════════════════

                // ╔══════════════════════════════════════════════════════════════╗
                // ║  [SỬA] Nhóm User — thêm requireSession() trước nghiệp vụ   ║
                // ╚══════════════════════════════════════════════════════════════╝
                // Trước đây: case "GET_PROFILE" -> userHandler.handleGetProfile(request);
                // Sau khi sửa: kiểm tra session trước, nếu không hợp lệ trả lỗi ngay
                case "GET_PROFILE" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : userHandler.handleGetProfile(request);
                }
                case "UPDATE_PASSWORD" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : userHandler.handleUpdatePassword(request);
                }
                case "DEPOSIT" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : userHandler.handleDeposit(request);
                }
                case "WITHDRAW" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : userHandler.handleWithdraw(request);
                }

                // ╔══════════════════════════════════════════════════════════════╗
                // ║  [SỬA] Nhóm Item — thêm requireSession() trước nghiệp vụ   ║
                // ╚══════════════════════════════════════════════════════════════╝
                case "ADD_ITEM" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : itemHandler.handleAddItem(request);
                }
                case "UPDATE_ITEM" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : itemHandler.handleUpdateItem(request);
                }
                case "DELETE_ITEM" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : itemHandler.handleDeleteItem(request);
                }
                case "CANCEL_ITEM" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : itemHandler.handleCancelItem(request);
                }
                case "GET_ITEM" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : itemHandler.handleGetItem(request);
                }
                case "GET_ITEMS_BY_SELLER" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : itemHandler.handleGetItemsBySeller(request);
                }
                case "GET_ITEMS_BY_OWNER" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : itemHandler.handleGetItemsByOwner(request);
                }
                case "GET_ACTIVE_ITEMS" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : itemHandler.handleGetActiveItems(request);
                }
                case "UPDATE_ITEM_STATUS" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : itemHandler.handleUpdateItemStatus(request);
                }
                case "RESTART_AUCTION" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : itemHandler.handleRestartAuction(request);
                }

                // ╔══════════════════════════════════════════════════════════════╗
                // ║  [SỬA] Nhóm Bid — thêm requireSession() trước nghiệp vụ    ║
                // ╚══════════════════════════════════════════════════════════════╝
                case "PLACE_BID" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : bidHandler.handlePlaceBid(request);
                }
                case "GET_BID_HISTORY" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : bidHandler.handleGetBidHistory(request);
                }
                case "GET_BID_HISTORY_BY_ITEM" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : bidHandler.handleGetBidHistoryByItem(request);
                }
                case "SETTLE_BID" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : bidHandler.handleSettleBid(request);
                }

                // ╔══════════════════════════════════════════════════════════════╗
                // ║  [SỬA] Nhóm History — thêm requireSession() trước nghiệp vụ║
                // ╚══════════════════════════════════════════════════════════════╝
                case "GET_HISTORY_BY_SELLER" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : historyHandler.handleGetHistoryBySeller(request);
                }
                case "GET_HISTORY_BY_BUYER" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : historyHandler.handleGetHistoryByBuyer(request);
                }
                case "GET_HISTORY_BY_ITEM" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : historyHandler.handleGetHistoryByItem(request);
                }

                // ── Unknown ───────────────────────────────────────────────────
                default -> {
                    JsonObject err = new JsonObject();
                    err.addProperty("status", "error");
                    err.addProperty("message", "Hành động không được hỗ trợ: " + action);
                    yield err;
                }
            };
        } catch (Exception e) {
            JsonObject err = new JsonObject();
            err.addProperty("status", "error");
            err.addProperty("message", "Lỗi hệ thống: " + e.getMessage());
            return err;
        }
    }
}