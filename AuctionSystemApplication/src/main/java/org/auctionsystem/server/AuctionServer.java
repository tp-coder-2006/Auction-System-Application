package org.auctionsystem.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class AuctionServer {
    public static void main(String[] args) {
        try {
            // 1. Mở cổng 8888 để đón khách
            ServerSocket serverSocket = new ServerSocket(8888);
            System.out.println("🟢 Máy chủ đang chạy. Đợi Client tới gõ cửa ở cổng 8888...");

            // 2. Tạm dừng chương trình để chờ khách (khi nào khách tới mới chạy tiếp)
            Socket socket = serverSocket.accept();
            System.out.println("🤝 Đã có 1 Client kết nối thành công!");

            // 3. Lấy thư mà khách gửi
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String jsonMessage = reader.readLine();

            System.out.println("📩 Máy chủ đọc được chuỗi JSON từ Client: " + jsonMessage);

            // 4. Xong việc thì đóng cửa
            socket.close();
            serverSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}