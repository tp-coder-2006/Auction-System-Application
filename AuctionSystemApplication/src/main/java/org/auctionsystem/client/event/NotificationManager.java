package org.auctionsystem.client.event;

import com.google.gson.JsonObject;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import javafx.stage.*;
import javafx.util.Duration;
import org.auctionsystem.client.Controller.Scene_Utils;
import org.auctionsystem.client.session.UserSession;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * NotificationManager — Hiển thị toast notification ở góc dưới phải màn hình.
 *
 * Lắng nghe 2 loại sự kiện global:
 *  - BID_PLACED      → Thông báo có bid mới cho mọi user
 *  - AUCTION_SETTLED → Thông báo kết quả đấu giá (thắng / thua / kết thúc)
 *
 * Cách dùng: gọi NotificationManager.activate() 1 lần sau khi login thành công.
 * Gọi NotificationManager.deactivate() khi logout.
 */
public final class NotificationManager {

    private NotificationManager() {}

    private static final String HANDLER_KEY = java.util.UUID.randomUUID().toString();
    private static volatile boolean active = false;

    // Hàng chờ toast (tránh overlap khi nhiều thông báo đến cùng lúc)
    private static final Queue<ToastData> queue = new LinkedList<>();
    private static final AtomicBoolean showing = new AtomicBoolean(false);

    // Offset Y tích lũy cho nhiều toast cùng lúc
    private static double currentOffsetY = 0;
    private static final double TOAST_HEIGHT = 80;
    private static final double TOAST_GAP    = 8;

    // ─────────────────────────────────────────────────────────────────────────
    //  Activate / Deactivate
    // ─────────────────────────────────────────────────────────────────────────

    public static synchronized void activate() {
        if (active) return;
        active = true;

        EventDispatcher.registerGlobal(EventType.BID_PLACED,       HANDLER_KEY,
                NotificationManager::onBidPlaced);
        EventDispatcher.registerGlobal(EventType.AUCTION_SETTLED,  HANDLER_KEY,
                NotificationManager::onAuctionSettled);
        EventDispatcher.registerGlobal(EventType.ITEM_CANCELLED,  HANDLER_KEY,
                NotificationManager::onItemCancelled);

        System.out.println("[NotificationManager] Đã kích hoạt.");
    }

    public static synchronized void deactivate() {
        if (!active) return;
        active = false;

        EventDispatcher.unregisterGlobal(EventType.BID_PLACED,      HANDLER_KEY);
        EventDispatcher.unregisterGlobal(EventType.AUCTION_SETTLED,  HANDLER_KEY);
        EventDispatcher.unregisterGlobal(EventType.ITEM_CANCELLED,   HANDLER_KEY);

        queue.clear();
        showing.set(false);
        currentOffsetY = 0;

        System.out.println("[NotificationManager] Đã hủy kích hoạt.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Event handlers
    // ─────────────────────────────────────────────────────────────────────────

    private static void onBidPlaced(JsonObject payload) {
        String itemName  = getString(payload, "item_name", getString(payload, "item_id", "sản phẩm"));
        String bidderName = getString(payload, "bidder_name", "Ai đó");
        double amount    = payload.has("bid_amount") ? payload.get("bid_amount").getAsDouble() : 0;

        String title = "💰 Bid mới";
        String body  = bidderName + " vừa đặt "
                + String.format("%,.0f ₫", amount)
                + " cho [" + itemName + "]";

        enqueue(new ToastData(title, body, ToastType.BID));
    }

    private static void onAuctionSettled(JsonObject payload) {
        String itemName = getString(payload, "item_name", getString(payload, "item_id", "sản phẩm"));
        String buyerId  = getString(payload, "bidder_id", "");
        String myId     = UserSession.getInstance() != null
                ? UserSession.getInstance().getUserId() : "";

        String title, body;
        ToastType type;

        if (buyerId.isEmpty()) {
            // Không có người thắng (item bị hủy hoặc không có bid nào)
            title = "⚠️ Đấu giá kết thúc";
            body  = "[" + itemName + "] đã kết thúc mà không có người thắng.";
            type  = ToastType.INFO;
        } else if (myId.equals(buyerId)) {
            title = "🎉 Đấu giá thành công!";
            body  = "Chúc mừng! Bạn đã thắng phiên đấu giá [" + itemName + "]!";
            type  = ToastType.WIN;
        } else {
            // Kiểm tra xem user có tham gia đặt bid không
            // Nếu có (BID_DEDUCT event đã xảy ra trước đó) → thất bại
            // Đơn giản nhất: hiện thông báo kết thúc cho mọi người
            title = "📋 Đấu giá kết thúc";
            body  = "[" + itemName + "] đã có người chiến thắng.";
            type  = ToastType.INFO;
        }

        enqueue(new ToastData(title, body, type));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Toast queue & display
    // ─────────────────────────────────────────────────────────────────────────


    private static void onItemCancelled(JsonObject payload) {
        String itemName = getString(payload, "item_name", getString(payload, "item_id", "sản phẩm"));
        enqueue(new ToastData(
                "❌ Phiên đấu giá bị hủy",
                "[" + itemName + "] đã bị hủy do không có lượt đặt giá hợp lệ.",
                ToastType.INFO));
    }

    private static synchronized void enqueue(ToastData data) {
        queue.add(data);
        processQueue();
    }

    private static void processQueue() {
        Platform.runLater(() -> {
            if (queue.isEmpty()) return;
            ToastData data = queue.poll();
            if (data != null) showToast(data);
        });
    }

    private static void showToast(ToastData data) {
        Stage primaryStage = Scene_Utils.getPrimaryStage();
        if (primaryStage == null || !primaryStage.isShowing()) return;

        // ── Xây dựng popup ───────────────────────────────────────────────────
        Stage popup = new Stage(StageStyle.TRANSPARENT);
        popup.initOwner(primaryStage);
        popup.initModality(Modality.NONE);
        popup.setAlwaysOnTop(true);

        // Container chính
        VBox box = new VBox(4);
        box.setPadding(new Insets(12, 16, 12, 14));
        box.setMaxWidth(320);
        box.setMinWidth(260);
        box.setAlignment(Pos.TOP_LEFT);

        // Style theo loại toast
        String bgColor, borderColor, titleColor;
        switch (data.type) {
            case WIN:
                bgColor     = "#1a2e1a";
                borderColor = "#4caf50";
                titleColor  = "#81c784";
                break;
            case BID:
                bgColor     = "#1a2335";
                borderColor = "#42a5f5";
                titleColor  = "#90caf9";
                break;
            default: // INFO
                bgColor     = "#2a2a2a";
                borderColor = "#888888";
                titleColor  = "#cccccc";
                break;
        }

        box.setStyle(
                "-fx-background-color: " + bgColor + ";" +
                        "-fx-background-radius: 8;" +
                        "-fx-border-color: " + borderColor + ";" +
                        "-fx-border-width: 1.5;" +
                        "-fx-border-radius: 8;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.55), 12, 0, 0, 3);"
        );

        // Thanh màu bên trái
        Region sideBar = new Region();
        sideBar.setMinWidth(4);
        sideBar.setMaxWidth(4);
        sideBar.setStyle("-fx-background-color: " + borderColor + "; -fx-background-radius: 2;");

        Label lblTitle = new Label(data.title);
        lblTitle.setStyle(
                "-fx-font-size: 13px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-text-fill: " + titleColor + ";"
        );
        lblTitle.setWrapText(true);

        Label lblBody = new Label(data.body);
        lblBody.setStyle(
                "-fx-font-size: 11.5px;" +
                        "-fx-text-fill: #d0d0d0;" +
                        "-fx-wrap-text: true;"
        );
        lblBody.setMaxWidth(290);
        lblBody.setWrapText(true);
        lblBody.setTextAlignment(TextAlignment.LEFT);

        VBox textBox = new VBox(3, lblTitle, lblBody);

        HBox contentRow = new HBox(10, sideBar, textBox);
        contentRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        box.getChildren().add(contentRow);

        // Progress bar (thời gian hiển thị)
        Region progressBar = new Region();
        progressBar.setMinHeight(3);
        progressBar.setMaxHeight(3);
        progressBar.setPrefWidth(300);
        progressBar.setStyle("-fx-background-color: " + borderColor + "; -fx-background-radius: 2;");
        box.getChildren().add(progressBar);

        Scene scene = new Scene(box);
        scene.setFill(Color.TRANSPARENT);

        popup.setScene(scene);

        // ── Vị trí: góc dưới phải ───────────────────────────────────────────
        popup.show();

        double screenX = primaryStage.getX() + primaryStage.getWidth()  - box.getWidth()  - 20;
        double screenY = primaryStage.getY() + primaryStage.getHeight() - box.getHeight() - 20
                - currentOffsetY;

        popup.setX(Math.max(screenX, 0));
        popup.setY(Math.max(screenY, 20));
        currentOffsetY += box.getHeight() + TOAST_GAP;

        // ── Animation: slide in từ phải ──────────────────────────────────────
        box.setTranslateX(320);
        box.setOpacity(0);

        TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), box);
        slideIn.setToX(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(250), box);
        fadeIn.setToValue(1);
        ParallelTransition inAnim = new ParallelTransition(slideIn, fadeIn);
        inAnim.play();

        // ── Progress bar shrink ──────────────────────────────────────────────
        double displaySecs = 4.5;
        Timeline progressTl = new Timeline(
                new KeyFrame(Duration.ZERO,                 new KeyValue(progressBar.prefWidthProperty(), 300)),
                new KeyFrame(Duration.seconds(displaySecs), new KeyValue(progressBar.prefWidthProperty(), 0))
        );
        progressTl.play();

        // ── Auto-close ───────────────────────────────────────────────────────
        PauseTransition pause = new PauseTransition(Duration.seconds(displaySecs));
        pause.setOnFinished(e -> {
            FadeTransition out = new FadeTransition(Duration.millis(300), box);
            out.setToValue(0);
            out.setOnFinished(ev -> {
                popup.close();
                currentOffsetY = Math.max(0, currentOffsetY - box.getHeight() - TOAST_GAP);
                // Xử lý toast tiếp theo trong hàng chờ
                processQueue();
            });
            out.play();
        });
        pause.play();

        // Click để đóng sớm
        box.setOnMouseClicked(e -> {
            pause.stop();
            progressTl.stop();
            FadeTransition out = new FadeTransition(Duration.millis(200), box);
            out.setToValue(0);
            out.setOnFinished(ev -> {
                popup.close();
                currentOffsetY = Math.max(0, currentOffsetY - box.getHeight() - TOAST_GAP);
                processQueue();
            });
            out.play();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Inner types
    // ─────────────────────────────────────────────────────────────────────────

    private enum ToastType { WIN, BID, INFO }

    private static class ToastData {
        final String    title;
        final String    body;
        final ToastType type;
        ToastData(String title, String body, ToastType type) {
            this.title = title;
            this.body  = body;
            this.type  = type;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static String getString(JsonObject obj, String key, String def) {
        return (obj != null && obj.has(key) && !obj.get(key).isJsonNull())
                ? obj.get(key).getAsString() : def;
    }
}