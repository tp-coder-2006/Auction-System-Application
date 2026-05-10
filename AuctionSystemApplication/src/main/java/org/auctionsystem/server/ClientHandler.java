package org.auctionsystem.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.auctionsystem.server.DAO.ItemDAO;
import org.auctionsystem.server.DAO.UserDAO;
import org.auctionsystem.server.Repository.BidRepository;
import org.auctionsystem.server.Repository.ItemRepository;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final Gson gson;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        this.gson   = new Gson();
    }

    @Override
    public void run() {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter   writer  = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String jsonMessage;
            while ((jsonMessage = reader.readLine()) != null) {
                System.out.println("📩 Client [" + socket.getInetAddress() + "] gửi: " + jsonMessage);

                JsonObject request  = gson.fromJson(jsonMessage, JsonObject.class);
                String     action   = request.has("action") ? request.get("action").getAsString() : "UNKNOWN";
                JsonObject response = new JsonObject();
                response.addProperty("action", action);

                switch (action) {
                    // ── Dùng chung ──────────────────────────────────────────
                    case "LOGIN"            -> handleLogin(request, response);
                    case "REGISTER"         -> handleRegister(request, response);

                    // ── Bidder ───────────────────────────────────────────────
                    case "GET_ITEMS"        -> handleGetItems(response);
                    case "BID"              -> handleBid(request, response);
                    case "GET_BID_HISTORY"  -> handleGetBidHistory(request, response);

                    // ── Seller ───────────────────────────────────────────────
                    case "ADD_ITEM"         -> handleAddItem(request, response);
                    case "UPDATE_ITEM"      -> handleUpdateItem(request, response);
                    case "DELETE_ITEM"      -> handleDeleteItem(request, response);
                    case "GET_MY_ITEMS"     -> handleGetMyItems(request, response);

                    default -> {
                        response.addProperty("status",  "error");
                        response.addProperty("message", "Hành động không được hỗ trợ.");
                    }
                }

                writer.println(response.toString());
            }

        } catch (Exception e) {
            System.err.println("❌ Lỗi kết nối với client " + socket.getInetAddress() + ": " + e.getMessage());
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
            System.out.println("👋 Client ngắt kết nối: " + socket.getInetAddress());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DÙNG CHUNG
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleLogin(JsonObject request, JsonObject response) {
        String username = request.get("username").getAsString();
        String password = request.get("password").getAsString();

        UserDAO userDao = new UserDAO();
        UserDAO.UserInfo user = userDao.loginUser(username, password);

        if (user != null) {
            response.addProperty("status",  "success");
            response.addProperty("message", "Đăng nhập thành công!");
            response.addProperty("userId",  user.id);
            response.addProperty("name",    user.name);
            response.addProperty("role",    user.role);
            response.addProperty("balance", user.balance);
            response.addProperty("email",   user.email);
        } else {
            response.addProperty("status",  "error");
            response.addProperty("message", "Sai tài khoản hoặc mật khẩu!");
        }
    }

    private void handleRegister(JsonObject request, JsonObject response) {
        String username = request.get("username").getAsString();
        String password = request.get("password").getAsString();
        String email    = request.get("email").getAsString();
        String name     = request.get("name").getAsString();
        String role     = request.has("role") ? request.get("role").getAsString() : "bidder";

        UserDAO userDao = new UserDAO();

        if (userDao.isUsernameExist(username)) {
            response.addProperty("status",  "error");
            response.addProperty("message", "Tên người dùng đã tồn tại!");
            return;
        }
        if (userDao.isEmailExist(email)) {
            response.addProperty("status",  "error");
            response.addProperty("message", "Email này đã được đăng ký!");
            return;
        }

        boolean ok = userDao.registerUser(name, username, password, email, role);
        response.addProperty("status",  ok ? "success" : "error");
        response.addProperty("message", ok ? "Đăng ký tài khoản thành công!"
                : "Đăng ký thất bại, vui lòng thử lại!");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BIDDER
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleGetItems(JsonObject response) {
        ItemRepository itemRepo = new ItemRepository();
        JsonArray items = itemRepo.getActiveItems();
        response.addProperty("status", "success");
        response.add("items", items);
    }

    private void handleBid(JsonObject request, JsonObject response) {
        String bidderId = request.get("bidderId").getAsString();
        String itemId   = request.get("itemId").getAsString();
        double amount   = request.get("amount").getAsDouble();

        BidRepository bidRepo = new BidRepository();
        boolean ok = bidRepo.saveBid(bidderId, itemId, amount);

        response.addProperty("status",  ok ? "success" : "error");
        response.addProperty("message", ok ? "Đặt giá thành công! Giá của bạn hiện là cao nhất."
                : "Đặt giá thất bại! Giá phải cao hơn giá hiện tại.");
    }

    private void handleGetBidHistory(JsonObject request, JsonObject response) {
        String bidderId = request.get("bidderId").getAsString();
        BidRepository bidRepo = new BidRepository();
        JsonArray history = bidRepo.getBidHistoryByBidder(bidderId);
        response.addProperty("status", "success");
        response.add("history", history);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SELLER
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Seller thêm sản phẩm mới.
     *
     * Client gửi lên:
     *   { "action": "ADD_ITEM", "sellerId": "...", "name": "...",
     *     "description": "...", "startingPrice": 1000000,
     *     "startTime": "2025-06-01 08:00:00", "endTime": "2025-06-08 20:00:00" }
     */
    private void handleAddItem(JsonObject request, JsonObject response) {
        String sellerId      = request.get("sellerId").getAsString();
        String name          = request.get("name").getAsString();
        String description   = request.has("description") ? request.get("description").getAsString() : "";
        double startingPrice = request.get("startingPrice").getAsDouble();
        String startTime     = request.get("startTime").getAsString();
        String endTime       = request.get("endTime").getAsString();

        if (startingPrice <= 0) {
            response.addProperty("status",  "error");
            response.addProperty("message", "Giá khởi điểm phải lớn hơn 0!");
            return;
        }

        ItemDAO itemDao = new ItemDAO();
        String itemId = itemDao.addItem(name, description, startingPrice, startTime, endTime, sellerId);

        if (itemId != null) {
            response.addProperty("status",  "success");
            response.addProperty("message", "Đăng sản phẩm thành công!");
            response.addProperty("itemId",  itemId);
        } else {
            response.addProperty("status",  "error");
            response.addProperty("message", "Đăng sản phẩm thất bại, vui lòng thử lại!");
        }
    }

    /**
     * Seller sửa thông tin sản phẩm (chỉ được khi status = 'pending').
     *
     * Client gửi lên:
     *   { "action": "UPDATE_ITEM", "itemId": "...", "sellerId": "...",
     *     "name": "...", "description": "...", "startingPrice": 1000000,
     *     "startTime": "...", "endTime": "..." }
     */
    private void handleUpdateItem(JsonObject request, JsonObject response) {
        String itemId        = request.get("itemId").getAsString();
        String sellerId      = request.get("sellerId").getAsString();
        String name          = request.get("name").getAsString();
        String description   = request.has("description") ? request.get("description").getAsString() : "";
        double startingPrice = request.get("startingPrice").getAsDouble();
        String startTime     = request.get("startTime").getAsString();
        String endTime       = request.get("endTime").getAsString();

        ItemDAO itemDao = new ItemDAO();
        boolean ok = itemDao.updateItem(itemId, name, description, startingPrice, startTime, endTime, sellerId);

        response.addProperty("status",  ok ? "success" : "error");
        response.addProperty("message", ok ? "Cập nhật sản phẩm thành công!"
                : "Không thể cập nhật. Sản phẩm đang active hoặc không thuộc về bạn.");
    }

    /**
     * Seller xóa sản phẩm (chỉ được khi status = 'pending' hoặc 'cancelled').
     *
     * Client gửi lên:
     *   { "action": "DELETE_ITEM", "itemId": "...", "sellerId": "..." }
     */
    private void handleDeleteItem(JsonObject request, JsonObject response) {
        String itemId   = request.get("itemId").getAsString();
        String sellerId = request.get("sellerId").getAsString();

        ItemDAO itemDao = new ItemDAO();
        boolean ok = itemDao.deleteItem(itemId, sellerId);

        response.addProperty("status",  ok ? "success" : "error");
        response.addProperty("message", ok ? "Xóa sản phẩm thành công!"
                : "Không thể xóa. Sản phẩm đang active hoặc không thuộc về bạn.");
    }

    /**
     * Lấy danh sách sản phẩm của Seller để hiển thị trên Seller Dashboard.
     *
     * Client gửi lên:
     *   { "action": "GET_MY_ITEMS", "sellerId": "..." }
     */
    private void handleGetMyItems(JsonObject request, JsonObject response) {
        String sellerId = request.get("sellerId").getAsString();
        ItemDAO itemDao = new ItemDAO();
        JsonArray items = itemDao.getItemsBySeller(sellerId);
        response.addProperty("status", "success");
        response.add("items", items);
    }
}