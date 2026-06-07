package org.auctionsystem.client.event;

import javafx.application.Platform;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * EventDispatcher — Bộ phân luồng sự kiện real-time phía Client.
 *
 * Tất cả handler đều là global handler, dùng key = eventType + ":" + handlerKey.
 * Nhiều handler cùng loại event được phép, không bao giờ ghi đè lên nhau.
 * Tất cả callback chạy trên JavaFX Application Thread.
 */
public class EventDispatcher {

    // Global handlers — key = eventType + ":" + handlerKey
    private static final Map<String, Consumer<com.google.gson.JsonObject>> globalHandlers =
            new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    //  API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Đăng ký handler cho 1 event type.
     * Nhiều handler cùng eventType được phép — handlerKey phân biệt chúng.
     *
     * @param eventType  Loại event (vd: EventType.BID_PLACED)
     * @param handlerKey Tên định danh duy nhất (vd: "BanWatcher", UUID của controller)
     * @param handler    Callback nhận JsonObject payload
     */
    public static void registerGlobal(String eventType, String handlerKey,
                                      Consumer<com.google.gson.JsonObject> handler) {
        String key = eventType + ":" + handlerKey;
        globalHandlers.put(key, handler);
        System.out.println("[EventDispatcher] Đã đăng ký handler: " + key);
    }

    /**
     * Hủy handler theo eventType + handlerKey.
     */
    public static void unregisterGlobal(String eventType, String handlerKey) {
        String key = eventType + ":" + handlerKey;
        globalHandlers.remove(key);
        System.out.println("[EventDispatcher] Đã hủy handler: " + key);
    }

    /**
     * Hủy toàn bộ handlers (gọi khi logout).
     */
    public static void unregisterAllGlobal() {
        globalHandlers.clear();
        System.out.println("[EventDispatcher] Đã hủy toàn bộ handlers.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Dispatch (nội bộ — chỉ ServerConnection gọi)
    // ─────────────────────────────────────────────────────────────────────────

    public static void dispatch(String eventType, com.google.gson.JsonObject payload) {
        String prefix = eventType + ":";
        boolean found = false;
        for (Map.Entry<String, Consumer<com.google.gson.JsonObject>> entry : globalHandlers.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                found = true;
                Platform.runLater(() -> {
                    try {
                        entry.getValue().accept(payload);
                    } catch (Exception e) {
                        System.err.println("[EventDispatcher] Lỗi handler " + entry.getKey()
                                + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }
        }
        if (!found) {
            System.out.println("[EventDispatcher] Không có handler cho event: " + eventType + " — bỏ qua.");
        }
    }
}