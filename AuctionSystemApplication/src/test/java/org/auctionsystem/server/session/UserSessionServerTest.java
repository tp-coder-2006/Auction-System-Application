package org.auctionsystem.server.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Server UserSession Tests")
class UserSessionServerTest {

    private UserSession session;

    @BeforeEach
    void setUp() {
        session = new UserSession(
                "session-id-001",
                "user-id-001",
                "Nguyen Van A",
                "vana",
                "vana@email.com",
                "BIDDER",
                1000.0,
                "0901234567",
                null
        );
    }

    // ═══════════════════════════════════════════════════════════
    // Getter — kiểm tra constructor gán đúng
    // ═══════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Getters")
    class GetterTests {

        @Test @DisplayName("getSessionId()") void sessionId()  { assertEquals("session-id-001", session.getSessionId()); }
        @Test @DisplayName("getUserId()")    void userId()     { assertEquals("user-id-001",    session.getUserId()); }
        @Test @DisplayName("getName()")      void name()       { assertEquals("Nguyen Van A",   session.getName()); }
        @Test @DisplayName("getUsername()")  void username()   { assertEquals("vana",           session.getUsername()); }
        @Test @DisplayName("getEmail()")     void email()      { assertEquals("vana@email.com", session.getEmail()); }
        @Test @DisplayName("getRole()")      void role()       { assertEquals("BIDDER",         session.getRole()); }
        @Test @DisplayName("getBalance()")   void balance()    { assertEquals(1000.0,           session.getBalance()); }
        @Test @DisplayName("getPhone()")     void phone()      { assertEquals("0901234567",     session.getPhone()); }
        @Test @DisplayName("getRating() null khi không phải Seller") void rating() { assertNull(session.getRating()); }
    }

    // ═══════════════════════════════════════════════════════════
    // Setters
    // ═══════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Setters")
    class SetterTests {

        @Test @DisplayName("setBalance()") void balance() { session.setBalance(2000.0); assertEquals(2000.0, session.getBalance()); }
        @Test @DisplayName("setPhone()")   void phone()   { session.setPhone("0999999999"); assertEquals("0999999999", session.getPhone()); }
        @Test @DisplayName("setRating()")  void rating()  { session.setRating(4.5); assertEquals(4.5, session.getRating()); }
        @Test @DisplayName("setName()")    void name()    { session.setName("New Name"); assertEquals("New Name", session.getName()); }
        @Test @DisplayName("setEmail()")   void email()   { session.setEmail("new@email.com"); assertEquals("new@email.com", session.getEmail()); }
    }

    // ═══════════════════════════════════════════════════════════
    // isExpired() và resetLastActiveTime()
    // ═══════════════════════════════════════════════════════════
    @Nested
    @DisplayName("isExpired() và resetLastActiveTime()")
    class ExpiryTests {

        @Test
        @DisplayName("Session mới tạo chưa hết hạn")
        void newSession_notExpired() {
            assertFalse(session.isExpired());
        }

        @Test
        @DisplayName("lastActiveTime được cập nhật sau resetLastActiveTime()")
        void resetLastActiveTime_updatesTime() throws InterruptedException {
            long before = session.getLastActiveTime();
            Thread.sleep(10); // đảm bảo thời gian thay đổi
            session.resetLastActiveTime();
            assertTrue(session.getLastActiveTime() > before);
        }

        @Test
        @DisplayName("Session vẫn chưa hết hạn sau reset")
        void afterReset_stillNotExpired() {
            session.resetLastActiveTime();
            assertFalse(session.isExpired());
        }
    }
}