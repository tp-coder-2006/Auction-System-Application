package org.auctionsystem.client.Connectivity;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * ServerConnection - Cầu nối giữa Client và Server qua Socket.
 *
 * Cách dùng trong bất kỳ Controller nào:
 *   JsonObject request = new JsonObject();
 *   request.addProperty("action", "LOGIN");
 *   request.addProperty("username", "abc");
 *   JsonObject response = ServerConnection.sendRequest(request);
 */
public class ServerConnection {

    private static final String HOST = "localhost";
    private static final int PORT = 8888;
    private static final Gson gson = new Gson();

    /**
     * Gửi một JsonObject lên Server và nhận JsonObject phản hồi về.
     * Mỗi lần gọi tự mở và tự đóng socket — không cần quản lý kết nối thủ công.
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
}
