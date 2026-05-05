package org.auctionsystem.server.Connectivity;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    private static final String URL      = "jdbc:mysql://localhost:3306/mydb";
    private static final String USER     = "root";
    private static final String PASSWORD = "12345678";

    // Khóa constructor - không ai được tạo đối tượng DatabaseConnection
    private DatabaseConnection() {}

    /**
     * Mỗi lần gọi hàm này sẽ mở 1 đường kết nối MỚI tới database.
     * Người gọi CÓ TRÁCH NHIỆM đóng connection sau khi dùng xong,
     * tốt nhất là dùng try-with-resources:
     *
     *   try (Connection conn = DatabaseConnection.getConnection()) {
     *       // ... làm việc với DB ...
     *   } // conn tự đóng ở đây
     */
    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Không tìm thấy MySQL Driver: " + e.getMessage());
        }
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
