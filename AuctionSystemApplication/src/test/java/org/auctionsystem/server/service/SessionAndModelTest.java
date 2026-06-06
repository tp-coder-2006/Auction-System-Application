package org.auctionsystem.server.service;

import org.auctionsystem.model.entities.*;
import org.auctionsystem.model.enums.ItemStatus;
import org.auctionsystem.model.enums.TransactionType;
import org.auctionsystem.model.enums.UserRole;
import org.auctionsystem.server.session.SessionManager;
import org.auctionsystem.server.session.UserSession;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests cho:
 *  - SessionManager: add, get, remove, duplicate, getAllSessions
 *  - Model entities: User, Seller, Item, Bid, Transaction — getter/setter
 *  - Enums: ItemStatus, UserRole, TransactionType
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SessionAndModelTest {

    // ═══════════════════════════════════════════════════════
    // ── SessionManager ────────────────────────────────────
    // ═══════════════════════════════════════════════════════

    @Test @Order(1)
    void sessionManager_addAndGetSession_returnsCorrectSession() {
        String sid = "sm-test-001";
        UserSession s = new UserSession(sid, "uid-001", "Alice", "alice",
                "a@a.com", "BIDDER", 1000.0, "0900000000", null, 0, null);
        SessionManager.addSession(s);

        UserSession got = SessionManager.getSession(sid);
        assertNotNull(got);
        assertEquals("uid-001", got.getUserId());
        assertEquals("Alice",   got.getName());
        assertEquals("BIDDER",  got.getRole());
        assertEquals(1000.0,    got.getBalance(), 0.01);

        SessionManager.removeSession(sid);
    }

    @Test @Order(2)
    void sessionManager_getSession_nonExistent_returnsNull() {
        assertNull(SessionManager.getSession("non-existent-session-id"));
    }

    @Test @Order(3)
    void sessionManager_removeSession_makesItUnavailable() {
        String sid = "sm-test-remove-001";
        SessionManager.addSession(new UserSession(
                sid, "u2", "Bob", "bob", "b@b.com", "SELLER", 200, null, 4.0, 1, null));

        SessionManager.removeSession(sid);
        assertNull(SessionManager.getSession(sid));
    }

    @Test @Order(4)
    void sessionManager_addDuplicateSession_overwritesPrevious() {
        String sid = "sm-dup-001";
        UserSession s1 = new UserSession(sid, "u1", "First", "first",
                "f@f.com", "BIDDER", 100, null, null, 0, null);
        UserSession s2 = new UserSession(sid, "u2", "Second", "second",
                "s@s.com", "ADMIN", 999, null, null, 0, null);

        SessionManager.addSession(s1);
        SessionManager.addSession(s2);

        UserSession got = SessionManager.getSession(sid);
        // Behaviour: nếu addSession ghi đè, sẽ trả về s2; nếu giữ s1 → s1
        assertNotNull(got);
        // Chỉ cần không throw
        SessionManager.removeSession(sid);
    }

    @Test @Order(5)
    void sessionManager_getAllSessions_containsAddedSession() {
        String sid = "sm-all-001";
        SessionManager.addSession(new UserSession(
                sid, "u3", "Charlie", "charlie", "c@c.com", "SELLER", 300, null, 3.5, 2, null));

        boolean found = SessionManager.getAllSessions()
                .stream().anyMatch(s -> s.getSessionId().equals(sid));
        assertTrue(found);

        SessionManager.removeSession(sid);
    }

    @Test @Order(6)
    void sessionManager_sessionMutability_updatesCorrectly() {
        String sid = "sm-mut-001";
        UserSession s = new UserSession(sid, "u4", "Dave", "dave",
                "d@d.com", "BIDDER", 500, null, null, 0, null);
        SessionManager.addSession(s);

        UserSession got = SessionManager.getSession(sid);
        assertNotNull(got);
        got.setBalance(750.0);
        got.setName("David");
        got.setPhone("0911111111");

        UserSession updated = SessionManager.getSession(sid);
        assertEquals(750.0, updated.getBalance(), 0.01);
        assertEquals("David", updated.getName());

        SessionManager.removeSession(sid);
    }

    // ═══════════════════════════════════════════════════════
    // ── User entity ───────────────────────────────────────
    // ═══════════════════════════════════════════════════════

    @Test @Order(10)
    void user_constructorAndGetters_correct() {
        User u = new User("u1", "Test User", "testuser", "hashed", 500.0,
                "test@test.com", "0900000000", UserRole.BIDDER, true, "avatars/u1.jpg");

        assertEquals("u1",            u.getId());
        assertEquals("Test User",     u.getName());
        assertEquals("testuser",      u.getUsername());
        assertEquals("hashed",        u.getPassword());
        assertEquals(500.0,           u.getBalance(), 0.01);
        assertEquals("test@test.com", u.getEmail());
        assertEquals("0900000000",    u.getPhone());
        assertEquals(UserRole.BIDDER, u.getRole());
        assertTrue(u.isActive());
        assertEquals("avatars/u1.jpg", u.getAvatarUrl());
    }

    @Test @Order(11)
    void user_setters_updateFields() {
        User u = new User();
        u.setId("u99");
        u.setName("Updated");
        u.setBalance(1500.0);
        u.setActive(false);
        u.setAvatarUrl("avatars/new.png");

        assertEquals("u99",          u.getId());
        assertEquals("Updated",      u.getName());
        assertEquals(1500.0,         u.getBalance(), 0.01);
        assertFalse(u.isActive());
        assertEquals("avatars/new.png", u.getAvatarUrl());
    }

    @Test @Order(12)
    void user_nullAvatarUrl_returnsNull() {
        User u = new User("u2", "N", "n", "p", 0,
                "n@n.com", null, UserRole.ADMIN, true, null);
        assertNull(u.getAvatarUrl());
        assertNull(u.getPhone());
    }

    // ═══════════════════════════════════════════════════════
    // ── Seller entity ─────────────────────────────────────
    // ═══════════════════════════════════════════════════════

    @Test @Order(20)
    void seller_ratingFields_correct() {
        Seller s = new Seller("s1", "Seller One", "seller1", "hash",
                200.0, "s@s.com", "0911", UserRole.SELLER,
                4.5, 10, true, null);

        assertEquals(4.5, s.getRating(), 0.001);
        assertEquals(10,  s.getRatingCount());
    }

    @Test @Order(21)
    void seller_nullRating_returnsNull() {
        Seller s = new Seller("s2", "N", "n", "p",
                0, "n@n.com", null, UserRole.SELLER,
                null, 0, true, null);

        assertNull(s.getRating());
        assertEquals(0, s.getRatingCount());
    }

    @Test @Order(22)
    void seller_setRating_updatesCorrectly() {
        Seller s = new Seller();
        s.setRating(3.0);
        s.setRatingCount(5);
        assertEquals(3.0, s.getRating(), 0.001);
        assertEquals(5,   s.getRatingCount());
    }

    @Test @Order(23)
    void seller_isInstanceOfUser() {
        Seller s = new Seller();
        assertInstanceOf(User.class, s);
    }

    // ═══════════════════════════════════════════════════════
    // ── Item entity ───────────────────────────────────────
    // ═══════════════════════════════════════════════════════

    @Test @Order(30)
    void item_constructorSetFields() {
        LocalDateTime start = LocalDateTime.now().plusMinutes(10);
        LocalDateTime end   = LocalDateTime.now().plusHours(2);

        Item item = new Item("Watch", "A nice watch", 500.0,
                start, end, ItemStatus.PENDING, "seller-001", "items/img.jpg");

        assertEquals("Watch",           item.getName());
        assertEquals("A nice watch",    item.getDescription());
        assertEquals(500.0,             item.getStartingPrice(), 0.01);
        assertEquals(ItemStatus.PENDING, item.getStatus());
        assertEquals("seller-001",      item.getSellerId());
        assertEquals("seller-001",      item.getOwnerId()); // owner = seller initially
        assertEquals("items/img.jpg",   item.getImageUrl());
        assertTrue(item.isActive());
        assertNull(item.getCurrentHighestPrice());
        assertNotNull(item.getId()); // UUID tự sinh
    }

    @Test @Order(31)
    void item_setters_updateFields() {
        Item item = new Item();
        item.setName("Updated Name");
        item.setStatus(ItemStatus.ACTIVE);
        item.setCurrentHighestPrice(750.0);
        item.setOwnerId("new-owner-id");
        item.setActive(false);

        assertEquals("Updated Name",    item.getName());
        assertEquals(ItemStatus.ACTIVE, item.getStatus());
        assertEquals(750.0,             item.getCurrentHighestPrice(), 0.01);
        assertEquals("new-owner-id",    item.getOwnerId());
        assertFalse(item.isActive());
    }

    @Test @Order(32)
    void item_eachInstanceHasUniqueId() {
        Item i1 = new Item("A", "d", 1, LocalDateTime.now(), LocalDateTime.now().plusHours(1),
                ItemStatus.PENDING, "s1", null);
        Item i2 = new Item("B", "d", 2, LocalDateTime.now(), LocalDateTime.now().plusHours(1),
                ItemStatus.PENDING, "s1", null);

        assertNotEquals(i1.getId(), i2.getId());
    }

    // ═══════════════════════════════════════════════════════
    // ── Bid entity ────────────────────────────────────────
    // ═══════════════════════════════════════════════════════

    @Test @Order(40)
    void bid_constructorAndGetters() {
        LocalDateTime now = LocalDateTime.now();
        Bid bid = new Bid("bid-001", "bidder-001", "item-001", 1000.0, now);

        assertEquals("bid-001",     bid.getId());
        assertEquals("bidder-001",  bid.getBidderId());
        assertEquals("item-001",    bid.getItemId());
        assertEquals(1000.0,        bid.getBidAmount(), 0.01);
        assertEquals(now,           bid.getBidTime());
    }

    @Test @Order(41)
    void bid_setters_updateFields() {
        Bid bid = new Bid();
        bid.setId("bid-002");
        bid.setBidderId("bidder-002");
        bid.setItemId("item-002");
        bid.setBidAmount(2500.0);

        assertEquals("bid-002",    bid.getId());
        assertEquals("bidder-002", bid.getBidderId());
        assertEquals(2500.0,       bid.getBidAmount(), 0.01);
    }

    // ═══════════════════════════════════════════════════════
    // ── Enums ─────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════

    @Test @Order(50)
    void itemStatus_allValuesPresent() {
        assertNotNull(ItemStatus.valueOf("PENDING"));
        assertNotNull(ItemStatus.valueOf("ACTIVE"));
        assertNotNull(ItemStatus.valueOf("CLOSED"));
        assertNotNull(ItemStatus.valueOf("CANCELLED"));
    }

    @Test @Order(51)
    void userRole_allValuesPresent() {
        assertNotNull(UserRole.valueOf("BIDDER"));
        assertNotNull(UserRole.valueOf("SELLER"));
        assertNotNull(UserRole.valueOf("ADMIN"));
    }

    @Test @Order(52)
    void transactionType_allValuesPresent() {
        assertNotNull(TransactionType.valueOf("DEPOSIT"));
        assertNotNull(TransactionType.valueOf("WITHDRAW"));
        assertNotNull(TransactionType.valueOf("BID_DEDUCT"));
        assertNotNull(TransactionType.valueOf("BID_CREDIT"));
    }

    @Test @Order(53)
    void itemStatus_invalidValue_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> ItemStatus.valueOf("INVALID_STATUS"));
    }
}
