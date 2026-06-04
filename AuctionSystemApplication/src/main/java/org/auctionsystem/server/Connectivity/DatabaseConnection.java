package org.auctionsystem.server.Connectivity;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * DatabaseConnection — Singleton bọc HikariCP connection pool.
 *
 * Tại sao HikariCP thay vì DriverManager.getConnection():
 *   - DriverManager mỗi lần tạo 1 TCP connection mới tới MySQL → chậm (~5-10ms overhead).
 *   - 100 client đặt giá đồng thời = 100 connection cùng lúc → MySQL mặc định
 *     max_connections=151, dễ hết slot → toàn bộ hệ thống văng exception.
 *   - HikariCP giữ sẵn pool connection tái sử dụng → getConnection() chỉ lấy
 *     từ pool (~microseconds), không tạo mới.
 *   - Khi pool đầy, request xếp hàng chờ (connectionTimeout) thay vì crash.
 *
 * Cách dùng (giữ nguyên so với cũ — không cần đổi DAO):
 *   try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
 *       // ... làm việc với DB ...
 *   } // conn trả về pool, không đóng thật sự
 *
 * Cài HikariCP: thêm vào pom.xml:
 *   <dependency>
 *       <groupId>com.zaxxer</groupId>
 *       <artifactId>HikariCP</artifactId>
 *       <version>5.1.0</version>
 *   </dependency>
 */
public class DatabaseConnection {

    private static volatile DatabaseConnection instance = null;

    private static final String URL      = "jdbc:mysql://localhost:3306/mydb"
            + "?useSSL=false&serverTimezone=Asia%2FHo_Chi_Minh&allowPublicKeyRetrieval=true";
    private static final String USER     = "root";
    private static final String PASSWORD = "12345678";

    private final HikariDataSource dataSource;

    private DatabaseConnection() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(URL);
        config.setUsername(USER);
        config.setPassword(PASSWORD);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // Pool size: mỗi ClientHandler thread cần tối đa 1 connection,
        // scheduler dùng thêm 2 → đặt maximumPoolSize vừa đủ.
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);

        // Thời gian tối đa chờ lấy connection từ pool (ms).
        // Sau thời gian này mới ném SQLException thay vì chờ vô hạn.
        config.setConnectionTimeout(30_000);

        // Connection nhàn rỗi quá 10 phút → trả về pool để đóng bớt
        config.setIdleTimeout(600_000);

        // Mỗi connection sống tối đa 30 phút dù có dùng hay không
        // (tránh MySQL server đóng connection phía nó mà pool không biết)
        config.setMaxLifetime(1_800_000);

        // Heartbeat: HikariCP ping connection này trước khi cho mượn
        // để chắc chắn nó còn sống (tránh "stale connection" sau khi MySQL restart)
        config.setConnectionTestQuery("SELECT 1");

        config.setPoolName("AuctionSystemPool");

        this.dataSource = new HikariDataSource(config);
        System.out.println("✅ HikariCP pool khởi tạo thành công (max=" + config.getMaximumPoolSize() + ").");
    }

    /** Double-checked locking — thread-safe, không lock sau khi đã khởi tạo. */
    public static DatabaseConnection getInstance() {
        if (instance == null) {
            synchronized (DatabaseConnection.class) {
                if (instance == null) {
                    instance = new DatabaseConnection();
                }
            }
        }
        return instance;
    }

    /**
     * Lấy connection từ pool.
     * Người gọi CÓ TRÁCH NHIỆM đóng (close) sau khi dùng — dùng try-with-resources.
     * close() trên HikariCP connection = trả về pool, không đóng thật sự.
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Đóng toàn bộ pool — gọi khi tắt server.
     * Sau khi gọi, mọi getConnection() sẽ ném SQLException.
     */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("[DatabaseConnection] Pool đã đóng.");
        }
    }
}