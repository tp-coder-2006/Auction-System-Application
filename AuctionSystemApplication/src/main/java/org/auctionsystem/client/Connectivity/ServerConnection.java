package org.auctionsystem.client.Connectivity;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.auctionsystem.client.event.EventDispatcher;
import org.auctionsystem.client.session.UserSession;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * ServerConnection — Kết nối socket bền vững (persistent) với Reader Thread riêng.
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  Kiến trúc trước (cũ):                                                  │
 * │    Mỗi request mở 1 socket mới → gửi → đọc → đóng socket.              │
 * │    Vấn đề: Server không thể chủ động push event về client.              │
 * │                                                                         │
 * │  Kiến trúc sau (mới — file này):                                        │
 * │    1 socket duy nhất, sống suốt phiên làm việc.                        │
 * │    Reader Thread chạy nền, đọc liên tục từ socket:                     │
 * │      - Nếu JSON có "event" → dispatch tới EventDispatcher               │
 * │      - Nếu JSON có "request_id" → trả về CompletableFuture đang chờ    │
 * │    Gọi API dùng sendRequest() — trả về Future, chờ response.           │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * Cách dùng sau khi login:
 *
 *   // Khởi tạo (gọi 1 lần sau login thành công):
 *   ServerConnection.connect();
 *
 *   // Gửi request nghiệp vụ:
 *   JsonObject req = new JsonObject();
 *   req.addProperty("action", "GET_ACTIVE_ITEMS");
 *   JsonObject res = ServerConnection.sendAuthRequest(req);
 *
 *   // Đóng khi logout:
 *   ServerConnection.disconnect();
 */
public class ServerConnection {

    private static final String HOST    = "localhost";
    private static final int    PORT    = 8888;
    private static final int    TIMEOUT = 10; // giây chờ response

    private static final Gson gson = new Gson();

    // Kết nối persistent
    private static Socket       socket;
    private static PrintWriter  writer;
    private static BufferedReader reader;
    private static Thread       readerThread;
    private static volatile boolean running = false;

    // Matching request_id → CompletableFuture
    // Khi gửi request, tạo 1 entry; Reader Thread điền response khi nhận được.
    private static final Map<String, CompletableFuture<JsonObject>> pendingRequests =
            new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    //  Kết nối / Ngắt kết nối
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Mở kết nối persistent và khởi động Reader Thread.
     * Gọi 1 lần sau khi đăng nhập thành công.
     */
    public static synchronized void connect() {
        if (running) return; // Đã kết nối rồi
        try {
            socket = new Socket(HOST, PORT);
            writer = new PrintWriter(socket.getOutputStream(), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            running = true;
            startReaderThread();
            System.out.println("[ServerConnection] Đã kết nối persistent tới server.");
        } catch (Exception e) {
            System.err.println("[ServerConnection] Không thể kết nối: " + e.getMessage());
        }
    }

    /**
     * Đóng kết nối (gọi khi logout hoặc app đóng).
     * Tự động hủy tất cả pending futures và dừng reader thread.
     */
    public static synchronized void disconnect() {
        running = false;
        // Hủy tất cả request đang chờ
        pendingRequests.forEach((id, future) -> future.cancel(true));
        pendingRequests.clear();
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) {}
        System.out.println("[ServerConnection] Đã ngắt kết nối.");
    }

    /** @return true nếu socket đang kết nối và reader thread đang chạy */
    public static boolean isConnected() {
        return running && socket != null && !socket.isClosed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Reader Thread
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Khởi động thread đọc liên tục từ socket.
     *
     * Phân loại JSON nhận được:
     *  - Có "event"      → server push, chuyển tới EventDispatcher
     *  - Có "request_id" → response cho request đang chờ, điền vào Future
     */
    private static void startReaderThread() {
        readerThread = new Thread(() -> {
            try {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    try {
                        JsonObject json = gson.fromJson(line, JsonObject.class);

                        if (json.has("event")) {
                            // ── Server push: chuyển tới EventDispatcher ──────
                            String eventType = json.get("event").getAsString();
                            EventDispatcher.dispatch(eventType, json);

                        } else if (json.has("request_id")) {
                            // ── Response cho request đang chờ ────────────────
                            String requestId = json.get("request_id").getAsString();
                            CompletableFuture<JsonObject> future = pendingRequests.remove(requestId);
                            if (future != null) {
                                future.complete(json);
                            } else {
                                System.err.println("[ServerConnection] Không tìm thấy future cho request_id: "
                                        + requestId);
                            }

                        } else {
                            System.err.println("[ServerConnection] JSON không nhận dạng được: " + line);
                        }

                    } catch (Exception parseEx) {
                        System.err.println("[ServerConnection] Lỗi parse JSON: " + parseEx.getMessage());
                    }
                }
            } catch (Exception e) {
                if (running) {
                    System.err.println("[ServerConnection] Reader thread lỗi: " + e.getMessage());
                    // Thử reconnect sau 3s
                    scheduleReconnect();
                }
            }
        }, "ServerConnection-Reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /**
     * Tự động reconnect sau khi mất kết nối.
     * Chờ 3 giây rồi thử lại. Tối đa 5 lần.
     */
    private static void scheduleReconnect() {
        new Thread(() -> {
            int attempt = 0;
            while (!isConnected() && attempt < 5) {
                attempt++;
                System.out.println("[ServerConnection] Thử kết nối lại lần " + attempt + "...");
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {}
                running = false;
                connect();
            }
        }, "ServerConnection-Reconnect").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Gửi Request
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Gửi request KHÔNG yêu cầu đăng nhập (chỉ dùng cho LOGIN và REGISTER).
     *
     * Phương thức này mở socket tạm thời (one-shot) vì chưa có persistent connection.
     * Sau khi login thành công, gọi connect() để thiết lập kết nối bền vững.
     *
     * @param request  JsonObject có "action" và các tham số
     * @return         JsonObject phản hồi từ server, null nếu lỗi
     */
    public static JsonObject sendRequest(JsonObject request) {
        try (Socket tmpSocket = new Socket(HOST, PORT);
             PrintWriter tmpWriter = new PrintWriter(tmpSocket.getOutputStream(), true);
             BufferedReader tmpReader = new BufferedReader(
                     new InputStreamReader(tmpSocket.getInputStream()))) {

            tmpWriter.println(request.toString());
            String responseJson = tmpReader.readLine();
            return gson.fromJson(responseJson, JsonObject.class);

        } catch (Exception e) {
            System.err.println("[ServerConnection] sendRequest lỗi: " + e.getMessage());
            return null;
        }
    }

    /**
     * Gửi request YÊU CẦU đăng nhập qua kết nối persistent.
     *
     * Tự động:
     *  1. Thêm "session_id" từ UserSession.
     *  2. Sinh "request_id" UUID để khớp với response.
     *  3. Đăng ký CompletableFuture, gửi JSON, chờ Reader Thread điền kết quả.
     *
     * @param request  JsonObject có "action" và các tham số nghiệp vụ
     * @return         JsonObject phản hồi từ server,
     *                 hoặc JsonObject lỗi {"status":"error"} nếu timeout/mất kết nối
     */
    public static JsonObject sendAuthRequest(JsonObject request) {
        if (!isConnected()) {
            return errorResponse("Chưa kết nối tới server. Vui lòng đăng nhập lại.");
        }

        // Đính session_id
        String sessionId = UserSession.getInstance().getSessionId();
        if (sessionId != null && !sessionId.isBlank()) {
            request.addProperty("session_id", sessionId);
        }

        // Sinh request_id duy nhất
        String requestId = UUID.randomUUID().toString();
        request.addProperty("request_id", requestId);

        // Đăng ký Future trước khi gửi (tránh race condition nếu response cực nhanh)
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        try {
            synchronized (writer) {
                writer.println(request.toString());
            }
            // Chờ response tối đa TIMEOUT giây
            return future.get(TIMEOUT, TimeUnit.SECONDS);

        } catch (java.util.concurrent.TimeoutException e) {
            pendingRequests.remove(requestId);
            System.err.println("[ServerConnection] Timeout khi chờ response cho: "
                    + request.get("action").getAsString());
            return errorResponse("Server không phản hồi. Vui lòng thử lại.");

        } catch (Exception e) {
            pendingRequests.remove(requestId);
            System.err.println("[ServerConnection] Lỗi gửi request: " + e.getMessage());
            return errorResponse("Lỗi kết nối: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helper
    // ─────────────────────────────────────────────────────────────────────────

    private static JsonObject errorResponse(String message) {
        JsonObject err = new JsonObject();
        err.addProperty("status", "error");
        err.addProperty("message", message);
        return err;
    }
}