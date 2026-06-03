package org.auctionsystem.client.event;

import javafx.application.Platform;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * EventDispatcher — Bộ phân luồng sự kiện real-time phía Client.
 *
 * Hỗ trợ 2 loại handler:
 *  - Handler thường (register/unregister): dùng cho Controller màn hình hiện tại.
 *    Mỗi eventType chỉ có 1 handler thường tại 1 thời điểm.
 *  - Handler toàn cục (registerGlobal/unregisterGlobal): dùng cho các singleton
 *    như NotificationManager, BanWatcher — hoạt động độc lập với màn hình.
 *    Nhiều handler toàn cục cùng loại event được phép.
 *
 * Cả 2 loại đều chạy trên JavaFX Application Thread.
 */
public class EventDispatcher {

    // Handler thường — 1 per event type
    private static final Map<String, Consumer<com.google.gson.JsonObject>> handlers =
            new ConcurrentHashMap<>();

    // Handler toàn cục — nhiều per event type, dùng key = eventType + ":" + handlerKey
    private static final Map<String, Consumer<com.google.gson.JsonObject>> globalHandlers =
            new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    //  Handler thường (per-screen)
    // ─────────────────────────────────────────────────────────────────────────

    public static void register(String eventType, Consumer<com.google.gson.JsonObject> handler) {
        handlers.put(eventType, handler);
        System.out.println("[EventDispatcher] Đã đăng ký handler cho: " + eventType);
    }

    public static void unregister(String eventType) {
        handlers.remove(eventType);
        System.out.println("[EventDispatcher] Đã hủy đăng ký handler cho: " + eventType);
    }

    public static void unregisterAll() {
        handlers.clear();
        System.out.println("[EventDispatcher] Đã hủy toàn bộ handlers.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Handler toàn cục (singleton/global)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Đăng ký handler toàn cục cho 1 event type.
     * Handler này tồn tại suốt vòng đời app (hoặc cho đến khi unregisterGlobal).
     * Không bị ghi đè bởi register() thông thường.
     *
     * @param eventType  Loại event
     * @param handlerKey Tên định danh duy nhất cho handler này (vd: "NotificationManager")
     * @param handler    Callback nhận JsonObject payload
     */
    public static void registerGlobal(String eventType, String handlerKey,
                                       Consumer<com.google.gson.JsonObject> handler) {
        String key = eventType + ":" + handlerKey;
        globalHandlers.put(key, handler);
        System.out.println("[EventDispatcher] Đã đăng ký global handler: " + key);
    }

    /**
     * Hủy handler toàn cục theo eventType + handlerKey.
     */
    public static void unregisterGlobal(String eventType, String handlerKey) {
        String key = eventType + ":" + handlerKey;
        globalHandlers.remove(key);
        System.out.println("[EventDispatcher] Đã hủy global handler: " + key);
    }

    /**
     * Hủy toàn bộ global handlers (gọi khi logout).
     */
    public static void unregisterAllGlobal() {
        globalHandlers.clear();
        System.out.println("[EventDispatcher] Đã hủy toàn bộ global handlers.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Dispatch (nội bộ — chỉ ServerConnection gọi)
    // ─────────────────────────────────────────────────────────────────────────

    public static void dispatch(String eventType, com.google.gson.JsonObject payload) {
        // 1. Gọi handler thường (per-screen) nếu có
        Consumer<com.google.gson.JsonObject> handler = handlers.get(eventType);
        if (handler != null) {
            Platform.runLater(() -> {
                try {
                    handler.accept(payload);
                } catch (Exception e) {
                    System.err.println("[EventDispatcher] Lỗi khi xử lý event " + eventType
                            + ": " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } else {
            System.out.println("[EventDispatcher] Không có handler cho event: " + eventType
                    + " — bỏ qua.");
        }

        // 2. Gọi tất cả global handlers cho event type này
        String prefix = eventType + ":";
        for (Map.Entry<String, Consumer<com.google.gson.JsonObject>> entry : globalHandlers.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                Platform.runLater(() -> {
                    try {
                        entry.getValue().accept(payload);
                    } catch (Exception e) {
                        System.err.println("[EventDispatcher] Lỗi global handler " + entry.getKey()
                                + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Kiểm tra
    // ─────────────────────────────────────────────────────────────────────────

    public static boolean hasHandler(String eventType) {
        return handlers.containsKey(eventType);
    }
}
