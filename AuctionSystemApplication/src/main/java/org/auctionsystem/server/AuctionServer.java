package org.auctionsystem.server;

import org.auctionsystem.server.session.SessionManager;

import java.net.ServerSocket;
import java.net.Socket;

public class AuctionServer {
    public static void main(String[] args) {
        try {
            // ╔══════════════════════════════════════════════════════════════╗
            // ║  [MỚI] Khởi động scheduler tự động dọn session hết hạn      ║
            // ║  Gọi trước vòng lặp accept() để scheduler chạy ngay từ đầu  ║
            // ║  Tham số 5 = quét mỗi 5 phút                                ║
            // ╚══════════════════════════════════════════════════════════════╝
            SessionManager.startSessionCleanup(5);

            ServerSocket serverSocket = new ServerSocket(8888);
            System.out.println("🟢 Server đang chạy ở cổng 8888...");

            // Vòng lặp vô tận để liên tục đón client mới
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("🤝 Client mới kết nối: " + socket.getInetAddress());

                // Mỗi client → 1 thread riêng, không chặn nhau
                Thread clientThread = new Thread(new ClientHandler(socket));
                clientThread.setDaemon(true); // tự tắt khi server dừng
                clientThread.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}