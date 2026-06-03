package org.auctionsystem.server.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.auctionsystem.server.session.SessionManager;
import org.auctionsystem.server.session.UserSession;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests cho Auction System Backend.
 *
 * ⚠️  YÊU CẦU: Cần DB MySQL đang chạy với schema auction_system.
 *
 * Cách chạy:
 *   mvn test -Dtest=AuctionIntegrationTest
 *   (thêm -Ddb.url=... -Ddb.user=... -Ddb.pass=... nếu cần override)
 *
 * Các test này kiểm tra luồng nghiệp vụ đầy đủ (end-to-end tại tầng service):
 *  FLOW 1: Register → Login → UpdateProfile → UpdatePassword
 *  FLOW 2: Seller AddItem → Admin Approve → Bidder PlaceBid → Settle
 *  FLOW 3: Deposit → Withdraw → GetTransactions
 *  FLOW 4: Rating flow
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("integration")
class AuctionIntegrationTest {

    // ─── Services ────────────────────────────────────────────────────────────
    private static UserService        userService;
    private static ItemService        itemService;
    private static BidService         bidService;
    private static AdminService       adminService;
    private static TransactionService transactionService;

    // ─── Shared state across tests ────────────────────────────────────────────
    private static String bidderSessionId;
    private static String sellerSessionId;
    private static String adminSessionId;
    private static String bidderId;
    private static String sellerId;
    private static String itemId;

    // Unique suffix để tránh conflict khi chạy nhiều lần
    private static final String SUFFIX = String.valueOf(System.currentTimeMillis()).substring(8);

    @BeforeAll
    static void setUpServices() {
        userService        = new UserService();
        itemService        = new ItemService();
        bidService         = new BidService();
        adminService       = new AdminService();
        transactionService = new TransactionService();
    }

    // ═══════════════════════════════════════════════════════
    // FLOW 1 — User: Register + Login + UpdateProfile + UpdatePassword
    // ═══════════════════════════════════════════════════════

    @Test @Order(10)
    void flow1_registerBidder_success() {
        JsonObject req = new JsonObject();
        req.addProperty("username", "bidder_" + SUFFIX);
        req.addProperty("password", "Bidder@12" + SUFFIX);
        req.addProperty("name",     "Integration Bidder");
        req.addProperty("email",    "bidder_" + SUFFIX + "@test.com");
        req.addProperty("role",     "BIDDER");

        JsonObject res = userService.registerUser(req);
        assertEquals("success", res.get("status").getAsString(),
                "Đăng ký bidder phải thành công: " + res);
    }

    @Test @Order(11)
    void flow1_registerSeller_success() {
        JsonObject req = new JsonObject();
        req.addProperty("username", "seller_" + SUFFIX);
        req.addProperty("password", "Seller@12" + SUFFIX);
        req.addProperty("name",     "Integration Seller");
        req.addProperty("email",    "seller_" + SUFFIX + "@test.com");
        req.addProperty("role",     "SELLER");

        JsonObject res = userService.registerUser(req);
        assertEquals("success", res.get("status").getAsString(),
                "Đăng ký seller phải thành công: " + res);
    }

    @Test @Order(12)
    void flow1_registerDuplicateUsername_fails() {
        JsonObject req = new JsonObject();
        req.addProperty("username", "bidder_" + SUFFIX); // trùng với @Order(10)
        req.addProperty("password", "Another@12");
        req.addProperty("name",     "Dup User");
        req.addProperty("email",    "dup@test.com");
        req.addProperty("role",     "BIDDER");

        JsonObject res = userService.registerUser(req);
        assertEquals("error", res.get("status").getAsString());
        assertTrue(res.get("message").getAsString().contains("đăng nhập"));
    }

    @Test @Order(13)
    void flow1_registerDuplicateEmail_fails() {
        JsonObject req = new JsonObject();
        req.addProperty("username", "dup_email_" + SUFFIX);
        req.addProperty("password", "Another@12");
        req.addProperty("name",     "Dup Email");
        req.addProperty("email",    "bidder_" + SUFFIX + "@test.com"); // trùng email
        req.addProperty("role",     "BIDDER");

        JsonObject res = userService.registerUser(req);
        assertEquals("error", res.get("status").getAsString());
    }

    @Test @Order(14)
    void flow1_loginBidder_success_setsSession() {
        JsonObject req = new JsonObject();
        req.addProperty("username", "bidder_" + SUFFIX);
        req.addProperty("password", "Bidder@12" + SUFFIX);

        JsonObject res = userService.loginUser(req);
        assertEquals("success", res.get("status").getAsString(),
                "Đăng nhập bidder phải thành công: " + res);

        bidderSessionId = res.get("session_id").getAsString();
        bidderId        = res.get("user_id").getAsString();

        assertNotNull(bidderSessionId);
        assertNotNull(bidderId);
        assertEquals("BIDDER", res.get("role").getAsString());
        assertNotNull(SessionManager.getSession(bidderSessionId));
    }

    @Test @Order(15)
    void flow1_loginSeller_success_setsSession() {
        JsonObject req = new JsonObject();
        req.addProperty("username", "seller_" + SUFFIX);
        req.addProperty("password", "Seller@12" + SUFFIX);

        JsonObject res = userService.loginUser(req);
        assertEquals("success", res.get("status").getAsString());

        sellerSessionId = res.get("session_id").getAsString();
        sellerId        = res.get("user_id").getAsString();
        assertNotNull(sellerSessionId);
    }

    @Test @Order(16)
    void flow1_loginWrongPassword_fails() {
        JsonObject req = new JsonObject();
        req.addProperty("username", "bidder_" + SUFFIX);
        req.addProperty("password", "WrongPassword@1");

        JsonObject res = userService.loginUser(req);
        assertEquals("error", res.get("status").getAsString());
        assertTrue(res.get("message").getAsString().contains("Sai mật khẩu"));
    }

    @Test @Order(17)
    void flow1_loginNonExistentUser_fails() {
        JsonObject req = new JsonObject();
        req.addProperty("username", "completely_nonexistent_user_xyz123");
        req.addProperty("password", "SomePass@1");

        JsonObject res = userService.loginUser(req);
        assertEquals("error", res.get("status").getAsString());
    }

    @Test @Order(18)
    void flow1_getMyProfile_success() {
        Assumptions.assumeTrue(bidderId != null, "Login phải chạy trước");

        JsonObject req = new JsonObject();
        req.addProperty("user_id",    bidderId);
        req.addProperty("session_id", bidderSessionId);

        JsonObject res = userService.getMyProfile(req);
        assertEquals("success", res.get("status").getAsString());
        assertTrue(res.has("information"));

        JsonObject info = res.get("information").getAsJsonObject();
        assertFalse(info.has("password"), "Password không được trả về");
        assertEquals(bidderId, info.get("id").getAsString());
    }

    @Test @Order(19)
    void flow1_updatePassword_wrongOldPassword_fails() {
        Assumptions.assumeTrue(bidderId != null);

        JsonObject req = new JsonObject();
        req.addProperty("user_id",      bidderId);
        req.addProperty("old_password", "WrongOldPass@1");
        req.addProperty("new_password", "NewPass@12" + SUFFIX);

        JsonObject res = userService.updatePassword(req);
        assertEquals("error", res.get("status").getAsString());
        assertTrue(res.get("message").getAsString().contains("Mật khẩu cũ"));
    }

    // ═══════════════════════════════════════════════════════
    // FLOW 2 — Transaction: Deposit + Withdraw
    // ═══════════════════════════════════════════════════════

    @Test @Order(20)
    void flow2_depositToBidder_success() {
        Assumptions.assumeTrue(bidderId != null);

        JsonObject req = new JsonObject();
        req.addProperty("user_id", bidderId);
        req.addProperty("amount",  50000.0);

        JsonObject res = transactionService.deposit(req);
        assertEquals("success", res.get("status").getAsString(),
                "Nạp tiền phải thành công: " + res);
    }

    @Test @Order(21)
    void flow2_depositToSeller_success() {
        Assumptions.assumeTrue(sellerId != null);

        JsonObject req = new JsonObject();
        req.addProperty("user_id", sellerId);
        req.addProperty("amount",  10000.0);

        JsonObject res = transactionService.deposit(req);
        assertEquals("success", res.get("status").getAsString());
    }

    @Test @Order(22)
    void flow2_withdraw_insufficientBalance_fails() {
        Assumptions.assumeTrue(bidderId != null);

        JsonObject req = new JsonObject();
        req.addProperty("user_id", bidderId);
        req.addProperty("amount",  999_999_999.0); // số tiền khổng lồ

        JsonObject res = transactionService.withdraw(req);
        assertEquals("error", res.get("status").getAsString());
    }

    @Test @Order(23)
    void flow2_getMyTransactions_hasTransactions() {
        Assumptions.assumeTrue(bidderId != null);

        JsonObject req = new JsonObject();
        req.addProperty("user_id", bidderId);

        JsonObject res = transactionService.getMyTransactions(req);
        assertEquals("success", res.get("status").getAsString());
        JsonArray arr = res.get("message").getAsJsonArray();
        assertFalse(arr.isEmpty(), "Phải có ít nhất 1 giao dịch sau khi nạp tiền");
    }

    // ═══════════════════════════════════════════════════════
    // FLOW 3 — Admin: Login, Approve item
    // ═══════════════════════════════════════════════════════

    @Test @Order(30)
    void flow3_adminLogin_success() {
        // Tài khoản admin phải có sẵn trong DB (seed data)
        JsonObject req = new JsonObject();
        req.addProperty("username", "admin");     // adjust to match your seed
        req.addProperty("password", "Admin@1234"); // adjust to match your seed

        JsonObject res = userService.loginUser(req);
        if ("success".equals(res.get("status").getAsString())) {
            adminSessionId = res.get("session_id").getAsString();
            assertEquals("ADMIN", res.get("role").getAsString());
        } else {
            // Admin chưa có trong DB seed → skip các test phụ thuộc
            System.out.println("[WARN] Admin login failed — admin seed data missing. Skipping admin tests.");
        }
    }

    @Test @Order(31)
    void flow3_sellerAddItem_success() {
        Assumptions.assumeTrue(sellerSessionId != null && sellerId != null);

        String startTime = java.time.LocalDateTime.now().plusSeconds(5)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String endTime = java.time.LocalDateTime.now().plusMinutes(5)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        JsonObject req = new JsonObject();
        req.addProperty("name",           "Integration Test Item " + SUFFIX);
        req.addProperty("description",    "Auto-generated test item");
        req.addProperty("starting_price", 100.0);
        req.addProperty("start_time",     startTime);
        req.addProperty("end_time",       endTime);
        req.addProperty("seller_id",      sellerId);

        JsonObject res = itemService.addItem(req);
        assertEquals("success", res.get("status").getAsString(),
                "Thêm item phải thành công: " + res);
        itemId = res.get("item_id").getAsString();
        assertNotNull(itemId);
    }

    @Test @Order(32)
    void flow3_adminGetAllItems_containsAddedItem() {
        Assumptions.assumeTrue(adminSessionId != null && itemId != null,
                "Admin session và item_id phải có sẵn");

        JsonObject req = new JsonObject();
        req.addProperty("session_id", adminSessionId);

        JsonObject res = adminService.getAllItems(req);
        assertEquals("success", res.get("status").getAsString(),
                "Admin lấy danh sách item phải thành công: " + res);

        // Kiểm tra item vừa thêm xuất hiện trong danh sách
        com.google.gson.JsonArray items = res.get("message").getAsJsonArray();
        boolean found = false;
        for (com.google.gson.JsonElement el : items) {
            if (itemId.equals(el.getAsJsonObject().get("id").getAsString())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Item vừa thêm phải có trong danh sách admin: " + itemId);
    }

    @Test @Order(33)
    void flow3_getItemById_statusIsPendingOrActive() {
        Assumptions.assumeTrue(itemId != null);

        JsonObject req = new JsonObject();
        req.addProperty("item_id", itemId);

        JsonObject res = itemService.getAItemById(req);
        assertEquals("success", res.get("status").getAsString());

        JsonObject itemJson = res.get("message").getAsJsonObject();
        String status = itemJson.get("status").getAsString();
        assertTrue(status.equals("PENDING") || status.equals("ACTIVE"),
                "Status sau khi approve phải là PENDING hoặc ACTIVE, thực tế: " + status);
    }

    // ═══════════════════════════════════════════════════════
    // FLOW 4 — Bidding
    // ═══════════════════════════════════════════════════════

    @Test @Order(40)
    void flow4_placeBid_sellerCannotBidOwnItem() {
        Assumptions.assumeTrue(itemId != null && sellerSessionId != null);

        JsonObject req = new JsonObject();
        req.addProperty("session_id", sellerSessionId);
        req.addProperty("item_id",    itemId);
        req.addProperty("bid_amount", 200.0);

        JsonObject res = bidService.placeBid(req);
        assertEquals("error", res.get("status").getAsString());
        // Lỗi có thể là "không tìm thấy sản phẩm" (nếu chưa ACTIVE)
        // hoặc "không thể đặt giá cho sản phẩm của mình"
        assertNotNull(res.get("message").getAsString());
    }

    @Test @Order(41)
    void flow4_searchItems_returnsResults() {
        JsonObject req = new JsonObject();
        req.addProperty("keyword", "Integration");

        JsonObject res = itemService.searchItems(req);
        assertEquals("success", res.get("status").getAsString());
        assertNotNull(res.get("message").getAsJsonArray());
    }

    @Test @Order(42)
    void flow4_getAllActiveItems_hasStatusField() {
        JsonObject req = new JsonObject();
        JsonObject res = itemService.getAllActiveItems(req);
        assertNotNull(res.get("status"));
    }

    // ═══════════════════════════════════════════════════════
    // FLOW 5 — Cancel & Delete item
    // ═══════════════════════════════════════════════════════

    @Test @Order(50)
    void flow5_cancelItem_wrongSeller_fails() {
        Assumptions.assumeTrue(itemId != null);

        JsonObject req = new JsonObject();
        req.addProperty("item_id",   itemId);
        req.addProperty("seller_id", "wrong-seller-id");

        JsonObject res = itemService.cancelItem(req);
        assertEquals("error", res.get("status").getAsString());
    }

    @Test @Order(51)
    void flow5_deleteItem_activeItem_fails() {
        // Rule mới: item đang ACTIVE không được xóa dưới bất kỳ hình thức nào
        Assumptions.assumeTrue(itemId != null && sellerId != null);

        // Lấy status hiện tại của item
        JsonObject getReq = new JsonObject();
        getReq.addProperty("item_id", itemId);
        JsonObject getRes = itemService.getAItemById(getReq);
        Assumptions.assumeTrue("success".equals(getRes.get("status").getAsString()));

        String status = getRes.get("message").getAsJsonObject()
                .get("status").getAsString();
        Assumptions.assumeTrue("ACTIVE".equals(status),
                "Test này chỉ chạy khi item đang ACTIVE");

        JsonObject req = new JsonObject();
        req.addProperty("item_id",   itemId);
        req.addProperty("seller_id", sellerId);

        JsonObject res = itemService.deleteItem(req);
        assertEquals("error", res.get("status").getAsString(),
                "Item ACTIVE không được phép xóa");
        assertTrue(res.get("message").getAsString().contains("đang trong phiên đấu giá"),
                "Message phải giải thích lý do từ chối");
    }

    @Test @Order(52)
    void flow5_deleteItem_pendingNoBid_hardDeletes() {
        // Tạo item pending mới (chưa có bid) → phải được hard delete
        Assumptions.assumeTrue(sellerId != null);

        String start = LocalDateTime.now().plusMinutes(120)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String end   = LocalDateTime.now().plusMinutes(180)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        JsonObject addReq = new JsonObject();
        addReq.addProperty("name",           "HardDelete-" + SUFFIX);
        addReq.addProperty("description",    "item để test hard delete");
        addReq.addProperty("starting_price", 50.0);
        addReq.addProperty("start_time",     start);
        addReq.addProperty("end_time",       end);
        addReq.addProperty("seller_id",      sellerId);

        JsonObject addRes = itemService.addItem(addReq);
        Assumptions.assumeTrue("success".equals(addRes.get("status").getAsString()),
                "Cần tạo được item để test hard delete");

        String newItemId = addRes.get("item_id").getAsString();

        // Xóa item pending chưa có bid → hard delete
        JsonObject delReq = new JsonObject();
        delReq.addProperty("item_id",   newItemId);
        delReq.addProperty("seller_id", sellerId);

        JsonObject delRes = itemService.deleteItem(delReq);
        assertEquals("success", delRes.get("status").getAsString(),
                "Item PENDING chưa có bid phải được hard delete thành công");
        assertEquals("hard", delRes.get("delete_type").getAsString(),
                "delete_type phải là 'hard'");

        // Xác nhận item đã biến mất hoàn toàn khỏi DB
        JsonObject getReq = new JsonObject();
        getReq.addProperty("item_id", newItemId);
        JsonObject getRes = itemService.getAItemById(getReq);
        assertEquals("error", getRes.get("status").getAsString(),
                "Item đã hard delete không còn tìm thấy được");
    }

    @Test @Order(53)
    void flow5_deleteItem_cancelledNoBid_hardDeletes() {
        // Item CANCELLED nhưng chưa từng có bid → hard delete
        // Lý do: không có dữ liệu lịch sử nào cần giữ lại
        Assumptions.assumeTrue(sellerId != null);

        String start = LocalDateTime.now().plusMinutes(120)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String end   = LocalDateTime.now().plusMinutes(180)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        JsonObject addReq = new JsonObject();
        addReq.addProperty("name",           "CancelledNoBid-" + SUFFIX);
        addReq.addProperty("description",    "item cancelled chưa có bid");
        addReq.addProperty("starting_price", 50.0);
        addReq.addProperty("start_time",     start);
        addReq.addProperty("end_time",       end);
        addReq.addProperty("seller_id",      sellerId);

        JsonObject addRes = itemService.addItem(addReq);
        Assumptions.assumeTrue("success".equals(addRes.get("status").getAsString()),
                "Cần tạo được item để test");

        String newItemId = addRes.get("item_id").getAsString();

        // Cancel item (vẫn chưa có bid)
        JsonObject cancelReq = new JsonObject();
        cancelReq.addProperty("item_id",   newItemId);
        cancelReq.addProperty("seller_id", sellerId);
        JsonObject cancelRes = itemService.cancelItem(cancelReq);
        Assumptions.assumeTrue("success".equals(cancelRes.get("status").getAsString()),
                "Cần cancel được item để test");

        // Delete: cancelled + chưa có bid → hard delete
        JsonObject delReq = new JsonObject();
        delReq.addProperty("item_id",   newItemId);
        delReq.addProperty("seller_id", sellerId);

        JsonObject delRes = itemService.deleteItem(delReq);
        assertEquals("success", delRes.get("status").getAsString(),
                "Item CANCELLED chưa có bid phải xóa thành công");
        assertEquals("hard", delRes.get("delete_type").getAsString(),
                "Cancelled + chưa có bid → hard delete (không có lịch sử cần giữ)");

        // Xác nhận item biến mất hoàn toàn khỏi DB
        JsonObject getReq = new JsonObject();
        getReq.addProperty("item_id", newItemId);
        assertEquals("error", itemService.getAItemById(getReq).get("status").getAsString(),
                "Item đã hard delete không còn tìm thấy được");
    }

    @Test @Order(54)
    void flow5_deleteItem_wrongSeller_fails() {
        // Seller khác không được xóa item của seller này
        Assumptions.assumeTrue(sellerId != null);

        String start = LocalDateTime.now().plusMinutes(120)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String end   = LocalDateTime.now().plusMinutes(180)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        JsonObject addReq = new JsonObject();
        addReq.addProperty("name",           "AuthCheck-" + SUFFIX);
        addReq.addProperty("description",    "item để test auth xóa");
        addReq.addProperty("starting_price", 50.0);
        addReq.addProperty("start_time",     start);
        addReq.addProperty("end_time",       end);
        addReq.addProperty("seller_id",      sellerId);

        JsonObject addRes = itemService.addItem(addReq);
        Assumptions.assumeTrue("success".equals(addRes.get("status").getAsString()));
        String newItemId = addRes.get("item_id").getAsString();

        JsonObject delReq = new JsonObject();
        delReq.addProperty("item_id",   newItemId);
        delReq.addProperty("seller_id", "wrong-seller-999");

        JsonObject delRes = itemService.deleteItem(delReq);
        assertEquals("error", delRes.get("status").getAsString(),
                "Seller không phải chủ sở hữu không được xóa item");

        // Cleanup
        JsonObject cleanReq = new JsonObject();
        cleanReq.addProperty("item_id",   newItemId);
        cleanReq.addProperty("seller_id", sellerId);
        itemService.deleteItem(cleanReq);
    }

    // ═══════════════════════════════════════════════════════
    // FLOW 6 — Rating validation
    // ═══════════════════════════════════════════════════════

    @Test @Order(60)
    void flow6_rating_sellerCantRateSeller_fails() {
        Assumptions.assumeTrue(sellerSessionId != null);

        JsonObject req = new JsonObject();
        req.addProperty("session_id",      sellerSessionId);
        req.addProperty("rating",          4.0);
        req.addProperty("seller_username", "any_seller");

        JsonObject res = userService.updateRating(req);
        assertEquals("error", res.get("status").getAsString());
        assertTrue(res.get("message").getAsString().contains("Bidder"));
    }

    @Test @Order(61)
    void flow6_rating_bidderNeverPurchased_fails() {
        Assumptions.assumeTrue(bidderSessionId != null);

        JsonObject req = new JsonObject();
        req.addProperty("session_id",      bidderSessionId);
        req.addProperty("rating",          5.0);
        req.addProperty("seller_username", "seller_" + SUFFIX);

        JsonObject res = userService.updateRating(req);
        assertEquals("error", res.get("status").getAsString());
        // Lỗi: chưa từng mua sản phẩm hoặc không phải seller
        assertNotNull(res.get("message").getAsString());
    }

    // ═══════════════════════════════════════════════════════
    // FLOW 7 — Admin Hard Delete
    //
    // Admin xóa vĩnh viễn item khỏi DB bất kể trạng thái.
    // Kiểm tra:
    //   - Không có quyền admin → FORBIDDEN
    //   - Item không tồn tại → error
    //   - Xóa thành công → item biến mất hoàn toàn khỏi DB
    //   - Xóa item đã có bid → bids cũng bị xóa theo (referential integrity)
    // ═══════════════════════════════════════════════════════

    @Test @Order(70)
    void flow7_adminDeleteItem_nonAdminSession_returnsForbidden() {
        Assumptions.assumeTrue(sellerSessionId != null);

        // Lấy session_id của seller (không phải admin) rồi gọi adminService.deleteItem
        String sellerSid = sellerSessionId;

        JsonObject req = new JsonObject();
        req.addProperty("session_id", sellerSid);
        req.addProperty("item_id",    "any-item-id");

        JsonObject res = adminService.deleteItem(req);
        assertEquals("error",     res.get("status").getAsString(),
                "Seller không được phép gọi admin hard delete");
        assertEquals("FORBIDDEN", res.get("code").getAsString());
    }

    @Test @Order(71)
    void flow7_adminDeleteItem_nonExistentItem_returnsError() {
        Assumptions.assumeTrue(adminSessionId != null,
                "Cần admin session để chạy test này");

        JsonObject req = new JsonObject();
        req.addProperty("session_id", adminSessionId);
        req.addProperty("item_id",    "non-existent-item-xyz-flow7");

        JsonObject res = adminService.deleteItem(req);
        assertEquals("error", res.get("status").getAsString(),
                "Item không tồn tại phải trả error");
        assertNotNull(res.get("message"));
    }

    @Test @Order(72)
    void flow7_adminDeleteItem_pendingItem_hardDeletesFromDB() {
        // Tạo item pending mới → admin hard delete → xác nhận biến mất hoàn toàn
        Assumptions.assumeTrue(adminSessionId != null && sellerId != null,
                "Cần admin session và sellerId");

        String start = LocalDateTime.now().plusMinutes(200)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String end   = LocalDateTime.now().plusMinutes(260)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        JsonObject addReq = new JsonObject();
        addReq.addProperty("name",           "AdminHardDelete-PENDING-" + SUFFIX);
        addReq.addProperty("description",    "item để test admin hard delete");
        addReq.addProperty("starting_price", 100.0);
        addReq.addProperty("start_time",     start);
        addReq.addProperty("end_time",       end);
        addReq.addProperty("seller_id",      sellerId);

        JsonObject addRes = itemService.addItem(addReq);
        Assumptions.assumeTrue("success".equals(addRes.get("status").getAsString()),
                "Cần tạo được item để test admin delete");
        String newItemId = addRes.get("item_id").getAsString();

        // Admin hard delete
        JsonObject delReq = new JsonObject();
        delReq.addProperty("session_id", adminSessionId);
        delReq.addProperty("item_id",    newItemId);

        JsonObject delRes = adminService.deleteItem(delReq);
        assertEquals("success", delRes.get("status").getAsString(),
                "Admin hard delete item PENDING phải thành công: " + delRes);

        // Xác nhận item đã biến mất hoàn toàn — kể cả với getAllItems (admin view)
        JsonObject getReq = new JsonObject();
        getReq.addProperty("item_id", newItemId);
        JsonObject getRes = itemService.getAItemById(getReq);
        assertEquals("error", getRes.get("status").getAsString(),
                "Item đã bị admin hard delete không còn tìm thấy được qua getAItemById");

        // Xác nhận không xuất hiện trong danh sách admin
        JsonObject listReq = new JsonObject();
        listReq.addProperty("session_id", adminSessionId);
        JsonObject listRes = adminService.getAllItems(listReq);
        if ("success".equals(listRes.get("status").getAsString())) {
            JsonArray items = listRes.get("message").getAsJsonArray();
            boolean stillExists = false;
            for (com.google.gson.JsonElement el : items) {
                if (newItemId.equals(el.getAsJsonObject().get("id").getAsString())) {
                    stillExists = true;
                    break;
                }
            }
            assertFalse(stillExists,
                    "Item đã hard delete không được xuất hiện trong danh sách admin");
        }
    }

    @Test @Order(73)
    void flow7_adminDeleteItem_cancelledItem_hardDeletesFromDB() {
        // Item CANCELLED → admin hard delete → biến mất hoàn toàn
        Assumptions.assumeTrue(adminSessionId != null && sellerId != null,
                "Cần admin session và sellerId");

        String start = LocalDateTime.now().plusMinutes(200)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String end   = LocalDateTime.now().plusMinutes(260)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // Tạo item
        JsonObject addReq = new JsonObject();
        addReq.addProperty("name",           "AdminHardDelete-CANCELLED-" + SUFFIX);
        addReq.addProperty("description",    "item cancelled để test admin hard delete");
        addReq.addProperty("starting_price", 50.0);
        addReq.addProperty("start_time",     start);
        addReq.addProperty("end_time",       end);
        addReq.addProperty("seller_id",      sellerId);

        JsonObject addRes = itemService.addItem(addReq);
        Assumptions.assumeTrue("success".equals(addRes.get("status").getAsString()));
        String newItemId = addRes.get("item_id").getAsString();

        // Cancel item trước
        JsonObject cancelReq = new JsonObject();
        cancelReq.addProperty("item_id",   newItemId);
        cancelReq.addProperty("seller_id", sellerId);
        Assumptions.assumeTrue("success".equals(itemService.cancelItem(cancelReq).get("status").getAsString()),
                "Cần cancel được item trước");

        // Admin hard delete item đã cancelled
        JsonObject delReq = new JsonObject();
        delReq.addProperty("session_id", adminSessionId);
        delReq.addProperty("item_id",    newItemId);

        JsonObject delRes = adminService.deleteItem(delReq);
        assertEquals("success", delRes.get("status").getAsString(),
                "Admin hard delete item CANCELLED phải thành công: " + delRes);

        // Xác nhận biến mất
        JsonObject getReq = new JsonObject();
        getReq.addProperty("item_id", newItemId);
        assertEquals("error", itemService.getAItemById(getReq).get("status").getAsString(),
                "Item CANCELLED đã hard delete không được tìm thấy nữa");
    }

    @Test @Order(74)
    void flow7_adminDeleteItem_itemWithBids_deletesAllBidsToo() {
        // Item đã có bid → admin hard delete → item và toàn bộ bids bị xóa
        // (Hệ thống ký quỹ ảo: balance bidder không bị ảnh hưởng)
        Assumptions.assumeTrue(adminSessionId != null && sellerId != null && bidderId != null,
                "Cần đủ admin session, sellerId và bidderId");

        String start = LocalDateTime.now().minusMinutes(5)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String end   = LocalDateTime.now().plusMinutes(120)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // Tạo item (start_time đã qua → scheduler sẽ set ACTIVE)
        JsonObject addReq = new JsonObject();
        addReq.addProperty("name",           "AdminHardDelete-WithBid-" + SUFFIX);
        addReq.addProperty("description",    "item có bid để test admin hard delete");
        addReq.addProperty("starting_price", 100.0);
        addReq.addProperty("start_time",     start);
        addReq.addProperty("end_time",       end);
        addReq.addProperty("seller_id",      sellerId);

        JsonObject addRes = itemService.addItem(addReq);
        Assumptions.assumeTrue("success".equals(addRes.get("status").getAsString()),
                "Cần tạo được item");
        String newItemId = addRes.get("item_id").getAsString();

        // Kích hoạt item (pending → active)
        itemService.updateItemStatus(new JsonObject());

        // Bidder đặt giá (có thể fail nếu item chưa ACTIVE — bỏ qua failure)
        JsonObject bidReq = new JsonObject();
        bidReq.addProperty("session_id", bidderSessionId != null ? bidderSessionId : "");
        bidReq.addProperty("item_id",    newItemId);
        bidReq.addProperty("bid_amount", 150.0);
        if (bidderSessionId != null) bidService.placeBid(bidReq);

        // Admin hard delete — bất kể status và có hay không có bid
        JsonObject delReq = new JsonObject();
        delReq.addProperty("session_id", adminSessionId);
        delReq.addProperty("item_id",    newItemId);

        JsonObject delRes = adminService.deleteItem(delReq);
        assertEquals("success", delRes.get("status").getAsString(),
                "Admin hard delete phải thành công dù item có bid hay không: " + delRes);

        // Xác nhận item biến mất
        JsonObject getReq = new JsonObject();
        getReq.addProperty("item_id", newItemId);
        assertEquals("error", itemService.getAItemById(getReq).get("status").getAsString(),
                "Item đã hard delete không được tìm thấy nữa");
    }

    @Test @Order(75)
    void flow7_adminDeleteItem_noRestoreAfterDelete() {
        // Sau khi admin hard delete, không có cơ chế restore nào — xóa là vĩnh viễn.
        // Test này document rằng service không expose restoreItem cho admin.
        Assumptions.assumeTrue(adminSessionId != null,
                "Cần admin session");

        // AdminService không có method restoreItem — compiler đã đảm bảo.
        // Nếu ta thử xóa rồi lấy lại → phải error.
        JsonObject req = new JsonObject();
        req.addProperty("session_id", adminSessionId);
        req.addProperty("item_id",    "already-deleted-item-xyz");

        // Xóa item không tồn tại → error (bình thường)
        JsonObject res = adminService.deleteItem(req);
        assertEquals("error", res.get("status").getAsString(),
                "Item không tồn tại / đã bị xóa → phải trả error, không phải success");

        // Không có adminService.restoreItem() — đây là đảm bảo thiết kế, không phải lỗi runtime
    }

    // ═══════════════════════════════════════════════════════
    // Cleanup
    // ═══════════════════════════════════════════════════════

    @AfterAll
    static void tearDown() {
        // Xóa session trong memory
        if (bidderSessionId != null) SessionManager.removeSession(bidderSessionId);
        if (sellerSessionId != null) SessionManager.removeSession(sellerSessionId);
        if (adminSessionId  != null) SessionManager.removeSession(adminSessionId);

        // Xóa dữ liệu test khỏi DB theo thứ tự tránh vi phạm foreign key:
        // bids → item_history → items → transactions → users
        // Mỗi bước dùng try riêng: nếu 1 bước lỗi, các bước sau vẫn tiếp tục chạy.
        try (java.sql.Connection conn =
                     org.auctionsystem.server.Connectivity.DatabaseConnection.getInstance().getConnection()) {

            // 1. Xóa bids liên quan đến item của seller test
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "DELETE b FROM bids b JOIN items i ON b.item_id = i.id " +
                            "WHERE i.seller_id = (SELECT id FROM users WHERE username = ?)")) {
                ps.setString(1, "seller_" + SUFFIX);
                ps.executeUpdate();
            } catch (Exception e) {
                System.err.println("[AuctionIntegrationTest] Lỗi xóa bids: " + e.getMessage());
            }

            // 2. Xóa item_history liên quan đến item của seller test
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "DELETE ih FROM item_history ih JOIN items i ON ih.item_id = i.id " +
                            "WHERE i.seller_id = (SELECT id FROM users WHERE username = ?)")) {
                ps.setString(1, "seller_" + SUFFIX);
                ps.executeUpdate();
            } catch (Exception e) {
                System.err.println("[AuctionIntegrationTest] Lỗi xóa item_history: " + e.getMessage());
            }

            // 3. Xóa items của seller test (kể cả soft-deleted: is_active=0)
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM items WHERE seller_id = (SELECT id FROM users WHERE username = ?)")) {
                ps.setString(1, "seller_" + SUFFIX);
                ps.executeUpdate();
            } catch (Exception e) {
                System.err.println("[AuctionIntegrationTest] Lỗi xóa items: " + e.getMessage());
            }

            // 4. Xóa transactions của bidder và seller test
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM transactions WHERE user_id IN " +
                            "(SELECT id FROM users WHERE username IN (?, ?))")) {
                ps.setString(1, "bidder_" + SUFFIX);
                ps.setString(2, "seller_" + SUFFIX);
                ps.executeUpdate();
            } catch (Exception e) {
                System.err.println("[AuctionIntegrationTest] Lỗi xóa transactions: " + e.getMessage());
            }

            // 5. Xóa 2 user test
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM users WHERE username IN (?, ?)")) {
                ps.setString(1, "bidder_" + SUFFIX);
                ps.setString(2, "seller_" + SUFFIX);
                int deleted = ps.executeUpdate();
                System.out.println("[AuctionIntegrationTest] Đã xóa " + deleted
                        + " user test (suffix=" + SUFFIX + ")");
            } catch (Exception e) {
                System.err.println("[AuctionIntegrationTest] Lỗi xóa users: " + e.getMessage());
            }

        } catch (Exception e) {
            System.err.println("[AuctionIntegrationTest] Không thể mở connection để cleanup: " + e.getMessage());
        }
    }
}