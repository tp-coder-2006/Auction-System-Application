package org.auctionsystem.server;

import java.net.ServerSocket;
import java.net.Socket;

public class AuctionServer {
    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(8888);
            System.out.println("🟢 Server đang chạy ở cổng 8888...");

            // Vòng lặp vô tận để liên tục đón client mới
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("🤝 Client mới kết nối: " + socket.getInetAddress());

                // Mỗi client → 1 thread riêng, không chặn nhau
                new Thread(new ClientHandler(socket)).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
