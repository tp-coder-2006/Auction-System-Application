package org.auctionsystem.server;

import com.google.gson.Gson;
import org.auctionsystem.server.util.GsonConfig;
import com.google.gson.JsonObject;
import org.auctionsystem.server.handler.AdminHandler;
import org.auctionsystem.server.handler.BidHandler;
import org.auctionsystem.server.handler.ImageHandler;
import org.auctionsystem.server.handler.TransactionHandler;
import org.auctionsystem.server.handler.HistoryHandler;
import org.auctionsystem.server.handler.RatingHandler;
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
    private final Gson   gson;
    private PrintWriter writer;
    private String currentSessionId = null;

    private final UserHandler        userHandler        = new UserHandler();
    private final ItemHandler        itemHandler        = new ItemHandler();
    private final BidHandler         bidHandler         = new BidHandler();
    private final HistoryHandler     historyHandler     = new HistoryHandler();
    private final RatingHandler      ratingHandler      = new RatingHandler();
    private final AdminHandler       adminHandler       = new AdminHandler();
    private final TransactionHandler transactionHandler = new TransactionHandler();
    private final ImageHandler       imageHandler       = new ImageHandler();

    public ClientHandler(Socket socket) {
        this.socket = socket;
        this.gson   = GsonConfig.create();
    }

    public synchronized void sendEvent(String eventJson) {
        if (writer != null && !socket.isClosed()) {
            writer.println(eventJson);
            System.out.println("📤 [Event → " + socket.getInetAddress() + "] " + eventJson);
        }
    }

    @Override
    public void run() {
        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                PrintWriter pw = new PrintWriter(socket.getOutputStream(), true)
        ) {
            this.writer = pw;

            String jsonMessage;
            while ((jsonMessage = reader.readLine()) != null) {
                System.out.println("📩 [" + socket.getInetAddress() + "] " + jsonMessage);

                JsonObject request  = gson.fromJson(jsonMessage, JsonObject.class);
                String action       = request.has("action")
                        ? request.get("action").getAsString() : "UNKNOWN";

                String requestId = request.has("request_id")
                        ? request.get("request_id").getAsString() : null;

                JsonObject response = route(action, request);
                response.addProperty("action", action);

                if (requestId != null) {
                    response.addProperty("request_id", requestId);
                }

                if ("LOGIN".equals(action)
                        && "success".equals(
                        response.has("status") ? response.get("status").getAsString() : "")) {
                    if (response.has("session_id")) {
                        currentSessionId = response.get("session_id").getAsString();
                        ConnectedClientRegistry.register(currentSessionId, this);

                        // [NEW] Nếu vừa login là ADMIN → push stats ngay lập tức,
                        // không phải chờ đến chu kỳ 30 giây tiếp theo.
                        UserSession loginSession = SessionManager.getSession(currentSessionId);
                        if (loginSession != null && "ADMIN".equalsIgnoreCase(loginSession.getRole())) {
                            AdminStatsScheduler.pushStatsToAdmin(currentSessionId);
                        }
                    }
                }

                synchronized (this) {
                    pw.println(response);
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Lỗi client " + socket.getInetAddress()
                    + ": " + e.getMessage());
        } finally {
            if (currentSessionId != null) {
                ConnectedClientRegistry.unregister(currentSessionId);
                SessionManager.removeSession(currentSessionId);
            }
            try { socket.close(); } catch (Exception ignored) {}
            System.out.println("👋 Client ngắt kết nối: " + socket.getInetAddress());
        }
    }

    private JsonObject requireSession(JsonObject request) {
        if (!request.has("session_id") || request.get("session_id").isJsonNull()) {
            JsonObject err = new JsonObject();
            err.addProperty("status", "error");
            err.addProperty("code", "NO_SESSION");
            err.addProperty("message", "Tài khoản chưa đăng nhập!");
            return err;
        }
        String sessionId = request.get("session_id").getAsString();
        UserSession session = SessionManager.getSession(sessionId);
        if (session == null) {
            JsonObject err = new JsonObject();
            err.addProperty("status", "expired");
            err.addProperty("code", "SESSION_EXPIRED");
            err.addProperty("message", "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại!");
            return err;
        }
        return null;
    }

    private JsonObject requireAdmin(JsonObject request) {
        JsonObject sessionErr = requireSession(request);
        if (sessionErr != null) return sessionErr;
        String sessionId = request.get("session_id").getAsString();
        UserSession session = SessionManager.getSession(sessionId);
        if (!"ADMIN".equalsIgnoreCase(session.getRole())) {
            JsonObject err = new JsonObject();
            err.addProperty("status", "error");
            err.addProperty("code", "FORBIDDEN");
            err.addProperty("message", "Bạn không có quyền thực hiện hành động này!");
            return err;
        }
        return null;
    }

    private JsonObject route(String action, JsonObject request) {
        try {
            return switch (action) {
                case "LOGIN"    -> userHandler.handleLogin(request);
                case "REGISTER" -> userHandler.handleRegister(request);

                case "GET_IMAGE" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : imageHandler.handleGetImage(request);
                }

                case "LOGOUT" -> {
                    JsonObject resp = new JsonObject();
                    if (request.has("session_id") && !request.get("session_id").isJsonNull()) {
                        String sid = request.get("session_id").getAsString();
                        ConnectedClientRegistry.unregister(sid);
                        SessionManager.removeSession(sid);
                        currentSessionId = null;
                    }
                    resp.addProperty("status", "success");
                    resp.addProperty("message", "Đăng xuất thành công!");
                    yield resp;
                }

                case "GET_PROFILE" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : userHandler.handleGetMyProfile(request);
                }
                // [THÊM MỚI] Cập nhật thông tin và Avatar
                case "UPDATE_PROFILE" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : userHandler.handleUpdateProfile(request);
                }
                case "UPDATE_PASSWORD" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : userHandler.handleUpdatePassword(request);
                }
                case "DEPOSIT" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : transactionHandler.handleDeposit(request);
                }
                case "UPDATE_RATING" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : userHandler.handleUpdateRating(request);
                }
                case "WITHDRAW" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : transactionHandler.handleWithdraw(request);
                }
                case "GET_OTHER_PROFILE" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : userHandler.handleGetOtherProfile(request);
                }
                case "SEARCH_USERS" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : userHandler.handleSearchUsers(request);
                }
                case "GET_ALL_ACTIVE_USERS" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : userHandler.handleGetAllActiveUsers(request);
                }


                case "BAN_USER", "ADMIN_BAN_USER" -> {
                    JsonObject err = requireAdmin(request);
                    yield err != null ? err : adminHandler.handleBanUser(request);
                }
                case "UNBAN_USER", "ADMIN_UNBAN_USER" -> {
                    JsonObject err = requireAdmin(request);
                    yield err != null ? err : adminHandler.handleUnbanUser(request);
                }
                case "GET_ALL_USERS", "ADMIN_GET_ALL_USERS" -> {
                    JsonObject err = requireAdmin(request);
                    yield err != null ? err : adminHandler.handleGetAllUsers(request);
                }
                // [THÊM MỚI] Admin tìm người dùng theo username
                case "ADMIN_GET_USER_BY_USERNAME" -> {
                    JsonObject err = requireAdmin(request);
                    yield err != null ? err : adminHandler.handleGetUserByUsername(request);
                }
                case "ADMIN_GET_ALL_ITEMS" -> {
                    JsonObject err = requireAdmin(request);
                    yield err != null ? err : adminHandler.handleGetAllItems(request);
                }
                case "ADMIN_DELETE_ITEM" -> {
                    JsonObject err = requireAdmin(request);
                    yield err != null ? err : adminHandler.handleDeleteItem(request);
                }
                case "GET_SYSTEM_STATS" -> {
                    JsonObject err = requireAdmin(request);
                    yield err != null ? err : adminHandler.handleGetSystemStats(request);
                }
                case "GET_ITEM_TREND" -> {
                    JsonObject err = requireAdmin(request);
                    yield err != null ? err : adminHandler.handleGetItemTrend(request);
                }
                case "GET_REVENUE_TREND" -> {
                    JsonObject err = requireAdmin(request);
                    yield err != null ? err : adminHandler.handleGetRevenueTrend(request);
                }

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
                case "GET_ALL_ITEMS" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : itemHandler.handleGetAllItems(request);
                }
                case "GET_VISIBLE_ITEMS" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : itemHandler.handleGetVisibleItems(request);
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
                case "RESTORE_HIDDEN_ITEM" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : itemHandler.handleRestoreHiddenItem(request);
                }
                case "SEARCH_ITEMS" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : itemHandler.handleSearchItems(request);
                }

                case "PLACE_BID" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : bidHandler.handlePlaceBid(request);
                }
                case "GET_BIDS_BY_BIDDER" -> {
                    JsonObject err = requireSession(request);
                    if (err != null) { yield err; }
                    UserSession session = SessionManager.getSession(request.get("session_id").getAsString());
                    String callerId    = session.getUserId();
                    String callerRole  = session.getRole();
                    String reqBidderId = request.has("bidder_id") ? request.get("bidder_id").getAsString() : "";
                    if (!"ADMIN".equalsIgnoreCase(callerRole) && !callerId.equals(reqBidderId)) {
                        JsonObject deny = new JsonObject();
                        deny.addProperty("status", "error");
                        deny.addProperty("message", "Bạn không có quyền xem lịch sử bid của người khác!");
                        yield deny;
                    }
                    yield bidHandler.handleGetBidsByBidder(request);
                }
                case "GET_BIDS_BY_BIDDER_AND_ITEM" -> {
                    JsonObject err = requireSession(request);
                    if (err != null) { yield err; }
                    UserSession session = SessionManager.getSession(request.get("session_id").getAsString());
                    String callerId    = session.getUserId();
                    String callerRole  = session.getRole();
                    String reqBidderId = request.has("bidder_id") ? request.get("bidder_id").getAsString() : "";
                    if (!"ADMIN".equalsIgnoreCase(callerRole) && !callerId.equals(reqBidderId)) {
                        JsonObject deny = new JsonObject();
                        deny.addProperty("status", "error");
                        deny.addProperty("message", "Bạn không có quyền xem lịch sử bid của người khác!");
                        yield deny;
                    }
                    yield bidHandler.handleGetBidsByBidderAndItem(request);
                }
                case "SETTLE_BID" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : bidHandler.handleSettleBid(request);
                }
                case "GET_BID_RESULTS_BY_BIDDER" -> {
                    JsonObject err = requireSession(request);
                    if (err != null) { yield err; }
                    UserSession session = SessionManager.getSession(request.get("session_id").getAsString());
                    String callerId    = session.getUserId();
                    String callerRole  = session.getRole();
                    String reqBidderId = request.has("bidder_id") ? request.get("bidder_id").getAsString() : "";
                    if (!"ADMIN".equalsIgnoreCase(callerRole) && !callerId.equals(reqBidderId)) {
                        JsonObject deny = new JsonObject();
                        deny.addProperty("status", "error");
                        deny.addProperty("message", "Bạn không có quyền xem kết quả đấu giá của người khác!");
                        yield deny;
                    }
                    yield bidHandler.handleGetBidResultsByBidder(request);
                }
                case "GET_ACTIVE_BIDS_BY_BIDDER" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : bidHandler.handleGetActiveBidsByBidder(request);
                }
                case "GET_HIGHEST_BID_BY_ITEM" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : bidHandler.handleGetHighestBidByItem(request);
                }
                case "GET_ACTIVE_BIDS_BY_ITEM" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : bidHandler.handleGetActiveBidsByItem(request);
                }
                case "GET_ALL_BIDS_BY_ITEM" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : bidHandler.handleGetAllBidsByItem(request);
                }

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
                case "CHECK_BOUGHT_FROM_SELLER" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : historyHandler.handleCheckBoughtFromSeller(request);
                }
                case "CHECK_ALREADY_RATED" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : ratingHandler.handleCheckAlreadyRated(request);
                }

                case "GET_MY_TRANSACTIONS" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : transactionHandler.handleGetMyTransactions(request);
                }
                case "GET_MY_TRANSACTIONS_BY_TYPE" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : transactionHandler.handleGetMyTransactionsByType(request);
                }
                case "ADMIN_GET_ALL_TRANSACTIONS" -> {
                    JsonObject err = requireAdmin(request);
                    yield err != null ? err : adminHandler.handleGetAllTransactions(request);
                }
                case "ADMIN_GET_TRANSACTIONS_BY_USER" -> {
                    JsonObject err = requireAdmin(request);
                    yield err != null ? err : adminHandler.handleGetTransactionsByUser(request);
                }
                case "ADMIN_GET_TRANSACTIONS_BY_ITEM" -> {
                    JsonObject err = requireAdmin(request);
                    yield err != null ? err : adminHandler.handleGetTransactionsByItem(request);
                }

                case "UPLOAD_AVATAR" -> {
                    JsonObject err = requireSession(request);
                    yield err != null ? err : userHandler.handleUploadAvatar(request);
                }

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