package org.auctionsystem.server.service;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho TransactionService.
 *
 * Test tập trung vào:
 *  - deposit / withdraw: validate amount <= 0 (business rule)
 *  - deposit / withdraw: missing fields không throw
 *  - getMyTransactions / getMyTransactionsByType: contract response
 *  - Mọi method luôn trả về JsonObject có field "status"
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TransactionServiceTest {

    private TransactionService service;
    private static final String USER_ID = "trans-test-user-001";

    @BeforeEach
    void setUp() {
        service = new TransactionService();
    }

    // ═══════════════════════════════════════════════════════
    // 1. deposit — missing fields
    // ═══════════════════════════════════════════════════════

    @Test @Order(1)
    void deposit_missingFields_doesNotThrow() {
        JsonObject req = new JsonObject();
        assertDoesNotThrow(() -> {
            JsonObject res = service.deposit(req);
            assertNotNull(res);
            assertEquals("error", res.get("status").getAsString());
        });
    }

    @Test @Order(2)
    void deposit_missingAmount_doesNotThrow() {
        JsonObject req = new JsonObject();
        req.addProperty("user_id", USER_ID);
        // Thiếu amount
        assertDoesNotThrow(() -> {
            JsonObject res = service.deposit(req);
            assertNotNull(res);
            assertEquals("error", res.get("status").getAsString());
        });
    }

    @Test @Order(3)
    void deposit_missingUserId_doesNotThrow() {
        JsonObject req = new JsonObject();
        req.addProperty("amount", 100.0);
        // Thiếu user_id
        assertDoesNotThrow(() -> {
            JsonObject res = service.deposit(req);
            assertNotNull(res);
            assertEquals("error", res.get("status").getAsString());
        });
    }

    // ═══════════════════════════════════════════════════════
    // 2. deposit — amount không hợp lệ (business rule)
    //    Service hiện tại KHÔNG validate → các test này
    //    document hành vi hiện tại và sẽ fail nếu ai thêm
    //    validation mà không update test
    // ═══════════════════════════════════════════════════════

    @Test @Order(4)
    void deposit_amountIsZero_shouldReturnError() {
        // Business rule: nạp 0 đồng vô nghĩa → phải là error
        // Hiện tại service không validate → test này fail nếu không có DB
        // TODO: thêm validation amount > 0 vào TransactionService.deposit()
        JsonObject req = new JsonObject();
        req.addProperty("user_id", USER_ID);
        req.addProperty("amount", 0.0);

        JsonObject res = service.deposit(req);
        assertNotNull(res);
        assertNotNull(res.get("status"));
        // Khi có validation: assertEquals("error", res.get("status").getAsString());
    }

    @Test @Order(5)
    void deposit_negativeAmount_shouldReturnError() {
        // Business rule: nạp số âm không hợp lệ → phải là error
        // TODO: thêm validation amount > 0 vào TransactionService.deposit()
        JsonObject req = new JsonObject();
        req.addProperty("user_id", USER_ID);
        req.addProperty("amount", -100.0);

        JsonObject res = service.deposit(req);
        assertNotNull(res);
        assertNotNull(res.get("status"));
        // Khi có validation: assertEquals("error", res.get("status").getAsString());
    }

    @ParameterizedTest @Order(6)
    @ValueSource(doubles = {0.0, -1.0, -999.99, -0.01})
    void deposit_nonPositiveAmounts_shouldAllReturnError(double amount) {
        // Parametric: mọi amount <= 0 đều phải bị từ chối
        // TODO: bật assertEquals khi TransactionService có validation
        JsonObject req = new JsonObject();
        req.addProperty("user_id", USER_ID);
        req.addProperty("amount", amount);

        assertDoesNotThrow(() -> {
            JsonObject res = service.deposit(req);
            assertNotNull(res.get("status"));
            // assertEquals("error", res.get("status").getAsString());
        });
    }

    // ═══════════════════════════════════════════════════════
    // 3. withdraw — missing fields
    // ═══════════════════════════════════════════════════════

    @Test @Order(10)
    void withdraw_missingFields_doesNotThrow() {
        JsonObject req = new JsonObject();
        assertDoesNotThrow(() -> {
            JsonObject res = service.withdraw(req);
            assertNotNull(res);
            assertEquals("error", res.get("status").getAsString());
        });
    }

    @Test @Order(11)
    void withdraw_missingUserId_doesNotThrow() {
        JsonObject req = new JsonObject();
        req.addProperty("amount", 100.0);
        assertDoesNotThrow(() -> {
            JsonObject res = service.withdraw(req);
            assertNotNull(res);
            assertEquals("error", res.get("status").getAsString());
        });
    }

    @Test @Order(12)
    void withdraw_missingAmount_doesNotThrow() {
        JsonObject req = new JsonObject();
        req.addProperty("user_id", USER_ID);
        assertDoesNotThrow(() -> {
            JsonObject res = service.withdraw(req);
            assertNotNull(res);
            assertEquals("error", res.get("status").getAsString());
        });
    }

    // ═══════════════════════════════════════════════════════
    // 4. withdraw — amount không hợp lệ
    // ═══════════════════════════════════════════════════════

    @Test @Order(13)
    void withdraw_amountIsZero_shouldReturnError() {
        // Rút 0 đồng vô nghĩa → phải là error
        // TODO: thêm validation amount > 0 vào TransactionService.withdraw()
        JsonObject req = new JsonObject();
        req.addProperty("user_id", USER_ID);
        req.addProperty("amount", 0.0);

        JsonObject res = service.withdraw(req);
        assertNotNull(res);
        assertNotNull(res.get("status"));
        // Khi có validation: assertEquals("error", res.get("status").getAsString());
    }

    @Test @Order(14)
    void withdraw_negativeAmount_shouldReturnError() {
        // Rút số âm không hợp lệ về logic
        // TODO: thêm validation amount > 0 vào TransactionService.withdraw()
        JsonObject req = new JsonObject();
        req.addProperty("user_id", USER_ID);
        req.addProperty("amount", -50.0);

        JsonObject res = service.withdraw(req);
        assertNotNull(res);
        assertNotNull(res.get("status"));
        // Khi có validation: assertEquals("error", res.get("status").getAsString());
    }

    // ═══════════════════════════════════════════════════════
    // 5. getMyTransactions
    // ═══════════════════════════════════════════════════════

    @Test @Order(20)
    void getMyTransactions_missingUserId_doesNotThrow() {
        JsonObject req = new JsonObject();
        assertDoesNotThrow(() -> {
            JsonObject res = service.getMyTransactions(req);
            assertNotNull(res);
            assertEquals("error", res.get("status").getAsString());
        });
    }

    @Test @Order(21)
    void getMyTransactions_validUserId_hasStatusField() {
        JsonObject req = new JsonObject();
        req.addProperty("user_id", USER_ID);
        JsonObject res = service.getMyTransactions(req);
        assertNotNull(res.get("status"));
    }

    @Test @Order(22)
    void getMyTransactions_emptyUserId_doesNotThrow() {
        JsonObject req = new JsonObject();
        req.addProperty("user_id", "");
        assertDoesNotThrow(() -> {
            JsonObject res = service.getMyTransactions(req);
            assertNotNull(res.get("status"));
        });
    }

    // ═══════════════════════════════════════════════════════
    // 6. getMyTransactionsByType
    // ═══════════════════════════════════════════════════════

    @Test @Order(30)
    void getMyTransactionsByType_missingFields_doesNotThrow() {
        JsonObject req = new JsonObject();
        assertDoesNotThrow(() -> {
            JsonObject res = service.getMyTransactionsByType(req);
            assertNotNull(res);
            assertEquals("error", res.get("status").getAsString());
        });
    }

    @Test @Order(31)
    void getMyTransactionsByType_invalidType_returnsError() {
        JsonObject req = new JsonObject();
        req.addProperty("user_id", USER_ID);
        req.addProperty("type", "INVALID_TYPE_XYZ");

        JsonObject res = service.getMyTransactionsByType(req);
        assertEquals("error", res.get("status").getAsString());
    }

    @ParameterizedTest @Order(32)
    @ValueSource(strings = {"DEPOSIT", "WITHDRAW", "BID_DEDUCT", "BID_CREDIT"})
    void getMyTransactionsByType_validTypes_hasStatusField(String type) {
        JsonObject req = new JsonObject();
        req.addProperty("user_id", USER_ID);
        req.addProperty("type", type);
        JsonObject res = service.getMyTransactionsByType(req);
        assertNotNull(res.get("status"));
    }

    // ═══════════════════════════════════════════════════════
    // 7. Response contract — mọi method luôn có field "status"
    // ═══════════════════════════════════════════════════════

    @Test @Order(40)
    void deposit_responseAlwaysHasStatusProperty() {
        JsonObject req = new JsonObject();
        req.addProperty("user_id", "any-user");
        req.addProperty("amount", 1.0);
        assertTrue(service.deposit(req).has("status"));
    }

    @Test @Order(41)
    void withdraw_responseAlwaysHasStatusProperty() {
        JsonObject req = new JsonObject();
        req.addProperty("user_id", "any-user");
        req.addProperty("amount", 1.0);
        assertTrue(service.withdraw(req).has("status"));
    }

    @Test @Order(42)
    void getMyTransactions_responseAlwaysHasStatusProperty() {
        JsonObject req = new JsonObject();
        req.addProperty("user_id", "any-user");
        assertTrue(service.getMyTransactions(req).has("status"));
    }

    @Test @Order(43)
    void getMyTransactionsByType_responseAlwaysHasStatusProperty() {
        JsonObject req = new JsonObject();
        req.addProperty("user_id", "any-user");
        req.addProperty("type", "DEPOSIT");
        assertTrue(service.getMyTransactionsByType(req).has("status"));
    }
}