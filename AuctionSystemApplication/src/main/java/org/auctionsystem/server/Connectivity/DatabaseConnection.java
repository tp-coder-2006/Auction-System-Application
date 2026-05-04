package org.auctionsystem.server.Connectivity;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    // 1. Biến static giữ kết nối duy nhất (Tránh mở quá nhiều đường ống gây sập máy)
    private static Connection connection = null;

    // 2. Thông tin database
    // (Đổi chữ 'mydb' thành tên database, đổi 'root' và password cho đúng với máy)
    private static final String URL = "jdbc:mysql://localhost:3306/mydb";
    private static final String USER = "root";
    private static final String PASSWORD = "12345678";

    // 3. Khóa Constructor lại
    private DatabaseConnection() {}

    // 4. Cung cấp một cổng duy nhất để lấy kết nối
    public static Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                // Đăng ký Driver và mở đường ống
                Class.forName("com.mysql.cj.jdbc.Driver");
                connection = DriverManager.getConnection(URL, USER, PASSWORD);
                System.out.println("Kết nối Database thành công!");
            }
        } catch (SQLException | ClassNotFoundException e) {
            System.err.println("Lỗi kết nối Database: " + e.getMessage());
        }
        return connection;
    }
}