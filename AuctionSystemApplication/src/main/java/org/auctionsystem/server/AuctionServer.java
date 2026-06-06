package org.auctionsystem.server;

import java.net.ServerSocket;
import java.net.Socket;

/**
 * AuctionServer — Entry point của server.
 *
 * Thay đổi so với phiên bản cũ:
 *   [SỬA NHỎ] Gọi AuctionScheduler.start() trước khi chấp nhận kết nối.
 *
 *   Scheduler mới (AuctionScheduler) thay thế đoạn code ScheduledExecutorService
 *   cũ được nhúng trực tiếp trong main(). Tách ra class riêng để dễ test & mở rộng.
 *   Sau mỗi lần kích hoạt PENDING → ACTIVE hoặc settle ACTIVE → CLOSED,
 *   scheduler sẽ broadcast event tương ứng xuống tất cả client.
 *
 *   [NEW] Gọi AdminStatsScheduler.start() để khởi động luồng nền cập nhật
 *   stats admin theo thời gian thực. Scheduler này:
 *     - Tự động push ADMIN_STATS_UPDATE mỗi 30 giây đến admin đang online.
 *     - Được trigger ngay lập tức khi có sự kiện thay đổi stats
 *       (ban/unban user, item thêm/sửa, auction settled, v.v.).
 */
public class AuctionServer {

    public static void main(String[] args) {
        // [SỬA NHỎ] Khởi động AuctionScheduler (thay đoạn scheduler cũ trong main)
        AuctionScheduler.start();

        // [NEW] Khởi động AdminStatsScheduler — real-time stats cho admin dashboard
        AdminStatsScheduler.start();

        try (ServerSocket serverSocket = new ServerSocket(8888)) {
            System.out.println("🚀 AuctionServer đang lắng nghe cổng 8888...");

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("✅ Client kết nối: " + socket.getInetAddress());

                // Mỗi client một thread riêng (ClientHandler persistent)
                Thread clientThread = new Thread(new ClientHandler(socket));
                clientThread.setDaemon(true);
                clientThread.start();
            }
        } catch (Exception e) {
            System.err.println("❌ Server lỗi: " + e.getMessage());
            e.printStackTrace();
        }
    }
}