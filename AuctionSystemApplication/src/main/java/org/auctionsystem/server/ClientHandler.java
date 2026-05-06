package org.auctionsystem.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.auctionsystem.server.Repository.BidRepository;
import org.auctionsystem.server.Repository.ItemRepository;
import org.auctionsystem.server.Repository.UserRepository;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final Gson gson;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        this.gson = new Gson();
    }

    @Override
    public void run() {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String jsonMessage;
            while ((jsonMessage = reader.readLine()) != null) {
                System.out.println("📩 Client [" + socket.getInetAddress() + "] gửi: " + jsonMessage);

                JsonObject request = gson.fromJson(jsonMessage, JsonObject.class);
                String action = request.has("action") ? request.get("action").getAsString() : "UNKNOWN";

                JsonObject response = new JsonObject();
                response.addProperty("action", action);

                // BỘ ĐỊNH TUYẾN - mỗi action được xử lý bởi một hàm riêng
                switch (action) {
                    case "LOGIN":
                        handleLogin(request, response);
                        break;
                    case "REGISTER":
                        handleRegister(request, response);
                        break;
                    case "GET_ITEMS":
                        handleGetItems(response);
                        break;
                    case "BID":
                        handleBid(request, response);
                        break;
                    case "GET_BID_HISTORY":
                        handleGetBidHistory(request, response);
                        break;
                    default:
                        response.addProperty("status", "error");
                        response.addProperty("message", "Hành động không được hỗ trợ từ Server.");
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

    // =========================================================
    // CÁC HÀM XỬ LÝ LOGIC NGHIỆP VỤ
    // =========================================================

    private void handleLogin(JsonObject request, JsonObject response) {
        String username = request.get("username").getAsString();
        String password = request.get("password").getAsString();

        UserRepository userRepo = new UserRepository();
        boolean isSuccess = userRepo.checkLogin(username, password);

        if (isSuccess) {
            response.addProperty("status", "success");
            response.addProperty("message", "Đăng nhập thành công!");
        } else {
            response.addProperty("status", "error");
            response.addProperty("message", "Sai tài khoản hoặc mật khẩu!");
        }
    }

    private void handleRegister(JsonObject request, JsonObject response) {
        String username = request.get("username").getAsString();
        String password = request.get("password").getAsString();
        String email    = request.get("email").getAsString();
        String name     = request.get("name").getAsString();

        UserRepository userRepo = new UserRepository();
        boolean isSuccess = userRepo.registerUser(username, password, email, name);

        if (isSuccess) {
            response.addProperty("status", "success");
            response.addProperty("message", "Đăng ký tài khoản thành công!");
        } else {
            response.addProperty("status", "error");
            response.addProperty("message", "Tên người dùng đã tồn tại, hãy chọn tên khác!");
        }
    }

    private void handleGetItems(JsonObject response) {
        // Lấy danh sách sản phẩm đang mở đấu giá từ database
        ItemRepository itemRepo = new ItemRepository();
        JsonArray items = itemRepo.getActiveItems();

        response.addProperty("status", "success");
        // Nhét thẳng JsonArray vào response — client sẽ parse ra và hiển thị lên ListView
        response.add("items", items);
    }

    private void handleBid(JsonObject request, JsonObject response) {
        String bidderId = request.get("bidderId").getAsString();
        String itemId   = request.get("itemId").getAsString();
        double amount   = request.get("amount").getAsDouble();

        BidRepository bidRepo = new BidRepository();
        boolean isSuccess = bidRepo.saveBid(bidderId, itemId, amount);

        if (isSuccess) {
            response.addProperty("status", "success");
            response.addProperty("message", "Đặt giá thành công! Giá của bạn hiện là cao nhất.");
        } else {
            response.addProperty("status", "error");
            response.addProperty("message", "Đặt giá thất bại! Giá phải cao hơn giá hiện tại.");
        }
    }

    private void handleGetBidHistory(JsonObject request, JsonObject response) {
        String bidderId = request.get("bidderId").getAsString();

        BidRepository bidRepo = new BidRepository();
        JsonArray history = bidRepo.getBidHistoryByBidder(bidderId);

        response.addProperty("status", "success");
        response.add("history", history);
    }
}
