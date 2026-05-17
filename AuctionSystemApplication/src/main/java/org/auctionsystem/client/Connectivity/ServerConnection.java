package org.auctionsystem.client.Connectivity;

// [SỬA] Thêm import UserSession để sendAuthRequest() có thể lấy session_id
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.auctionsystem.client.session.UserSession;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * ServerConnection - Cầu nối giữa Client và Server qua Socket.
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  Quy tắc sử dụng sau khi sửa:                                  │
 * │                                                                 │
 * │  Chưa đăng nhập (LOGIN, REGISTER):                             │
 * │    → dùng sendRequest(request)                                  │
 * │                                                                 │
 * │  Đã đăng nhập (mọi action khác):                               │
 * │    → dùng sendAuthRequest(request)                              │
 * │       session_id sẽ được tự động đính kèm, không cần thêm tay  │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * Ví dụ dùng trong Controller sau khi login:
 *
 *   JsonObject request = new JsonObject();
 *   request.addProperty("action", "GET_PROFILE");
 *   request.addProperty("user_id", userId);
 *   JsonObject response = ServerConnection.sendAuthRequest(request);
 *   // request sẽ tự động có thêm "session_id" trước khi gửi lên server
 */
public class ServerConnection {

    private static final String HOST = "localhost";
    private static final int PORT = 8888;
    private static final Gson gson = new Gson();

    /**
     * [GIỮ NGUYÊN] Gửi request KHÔNG yêu cầu đăng nhập.
     * Chỉ dùng cho LOGIN và REGISTER.
     * Không đính kèm session_id.
     *
     * @param request  JsonObject chứa "action" và các tham số kèm theo
     * @return         JsonObject phản hồi từ Server, hoặc null nếu có lỗi mạng
     */
    public static JsonObject sendRequest(JsonObject request) {
        try (Socket socket = new Socket(HOST, PORT);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Gửi JSON lên Server dưới dạng một dòng chuỗi
            writer.println(request.toString());

            // Đọc một dòng phản hồi từ Server và dịch ngược về JsonObject
            String responseJson = reader.readLine();
            return gson.fromJson(responseJson, JsonObject.class);

        } catch (Exception e) {
            System.err.println("❌ Không thể kết nối tới Server: " + e.getMessage());
            return null;
        }
    }

    // ╔══════════════════════════════════════════════════════════════════════╗
    // ║  [MỚI] sendAuthRequest — gửi request kèm session_id tự động        ║
    // ╚══════════════════════════════════════════════════════════════════════╝
    /**
     * [MỚI] Gửi request YÊU CẦU đăng nhập.
     *
     * Tự động lấy session_id từ UserSession singleton (đã được lưu lúc login)
     * và đính vào request trước khi gửi lên server.
     *
     * Server sẽ dùng session_id này để tra trong ConcurrentHashMap:
     *   - Nếu không tồn tại hoặc hết hạn → trả về lỗi "chưa đăng nhập"
     *   - Nếu hợp lệ → cho phép xử lý nghiệp vụ
     *
     * Lý do thêm method này:
     *   Trước đây mỗi controller phải tự thêm session_id vào request thủ công,
     *   dễ quên và không nhất quán. Method này tập trung logic đó vào 1 chỗ.
     *
     * @param request  JsonObject chứa "action" và các tham số nghiệp vụ
     * @return         JsonObject phản hồi từ Server, hoặc null nếu có lỗi mạng
     */
    public static JsonObject sendAuthRequest(JsonObject request) {
        // [MỚI] Lấy session_id từ UserSession singleton và đính vào request
        String sessionId = UserSession.getInstance().getSessionId();
        if (sessionId != null && !sessionId.isBlank()) {
            request.addProperty("session_id", sessionId);
        }
        // Sau đó gửi bình thường qua sendRequest
        return sendRequest(request);
    }
    // ══════════════════════════════════════════════════════════════════════
}