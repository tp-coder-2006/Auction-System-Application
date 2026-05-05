package org.auctionsystem.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
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

                // Phân tích chuỗi JSON nhận được thành đối tượng JsonObject
                JsonObject request = gson.fromJson(jsonMessage, JsonObject.class);
                String action = request.has("action") ? request.get("action").getAsString() : "UNKNOWN";

                // Chuẩn bị khung JSON để phản hồi lại Client
                JsonObject response = new JsonObject();
                response.addProperty("action", action);

                // BỘ ĐỊNH TUYẾN (ROUTER) - Chia nhánh xử lý logic theo từng loại lệnh
                switch (action) {
                    case "LOGIN":
                        handleLogin(request, response);
                        break;
                    case "REGISTER":
                        // handleRegister(request, response);
                        break;
                    case "BID":
                        // handleBid(request, response);
                        break;
                    default:
                        response.addProperty("status", "error");
                        response.addProperty("message", "Hành động không được hỗ trợ từ Server.");
                }

                // Gửi kết quả JSON trả lại cho Client
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

        // Gọi xuống database thông qua UserRepository
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
}
