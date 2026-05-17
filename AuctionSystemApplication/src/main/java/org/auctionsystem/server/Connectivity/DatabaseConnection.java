package org.auctionsystem.server.Connectivity;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * DatabaseConnection — Singleton Pattern (Double-Checked Locking).
 *
 * Lý do Singleton ở đây:
 *   - Driver chỉ cần load MỘT LẦN duy nhất trong suốt vòng đời ứng dụng.
 *   - Mọi cấu hình DB (URL, USER, PASSWORD) tập trung tại một chỗ.
 *   - Tránh trường hợp nhiều nơi tự tạo DatabaseConnection riêng với config khác nhau.
 *
 * Tại sao mỗi getConnection() vẫn tạo connection mới:
 *   - AuctionServer xử lý nhiều client song song (mỗi client 1 Thread riêng).
 *   - JDBC Connection KHÔNG thread-safe — không thể chia sẻ 1 connection cho nhiều thread.
 *   - Mỗi Repository dùng try-with-resources → connection tự đóng sau khi xong việc.
 *   - Singleton ở đây quản lý CÁCH TẠO connection, không quản lý connection object.
 */
public class DatabaseConnection {

    // volatile đảm bảo mọi thread đều thấy giá trị mới nhất của instance
    // (tránh lỗi "partially constructed object" trong môi trường đa luồng)
    private static volatile DatabaseConnection instance = null;

    private static final String URL      = "jdbc:mysql://localhost:3306/mydb";
    private static final String USER     = "root";
    private static final String PASSWORD = "12345678";

    // Constructor private — không ai bên ngoài được tạo đối tượng này
    private DatabaseConnection() {
        try {
            // Driver chỉ load một lần duy nhất khi Singleton được khởi tạo
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("✅ MySQL Driver đã được nạp.");
        } catch (ClassNotFoundException e) {
            // Nếu không có Driver → throw ngay, không cho ứng dụng chạy tiếp
            throw new RuntimeException("Không tìm thấy MySQL Driver: " + e.getMessage());
        }
    }

    /**
     * Lấy instance duy nhất của DatabaseConnection.
     *
     * Double-Checked Locking:
     *   - Check lần 1 (không synchronized): tránh lock không cần thiết sau khi đã khởi tạo.
     *   - Check lần 2 (bên trong synchronized): đảm bảo chỉ 1 thread tạo instance.
     *   - volatile + 2 lần check = thread-safe mà không bị chậm vì lock liên tục.
     */
    public static DatabaseConnection getInstance() {
        if (instance == null) {                          // Check lần 1 — không lock
            synchronized (DatabaseConnection.class) {
                if (instance == null) {                  // Check lần 2 — trong lock
                    instance = new DatabaseConnection();
                }
            }
        }
        return instance;
    }

    /**
     * Mở một connection MỚI tới database.
     *
     * Người gọi CÓ TRÁCH NHIỆM đóng connection sau khi dùng xong.
     * Luôn dùng try-with-resources:
     *
     *   try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
     *       // ... làm việc với DB ...
     *   } // conn tự đóng ở đây
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}