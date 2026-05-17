package org.auctionsystem.client.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Client UserSession Tests")
class UserSessionClientTest {

    @BeforeEach
    void setUp() {
        // Reset singleton trước mỗi test để tránh ảnh hưởng lẫn nhau
        UserSession.getInstance().clear();
    }

    // ═══════════════════════════════════════════════════════════
    // Singleton
    // ═══════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Singleton")
    class SingletonTests {

        @Test
        @DisplayName("getInstance() luôn trả về cùng một object")
        void getInstance_returnsSameInstance() {
            UserSession a = UserSession.getInstance();
            UserSession b = UserSession.getInstance();
            assertSame(a, b);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Setters và Getters
    // ═══════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Setters và Getters")
    class SetterGetterTests {

        @Test @DisplayName("setSessionId / getSessionId") void sessionId() {
            UserSession.getInstance().setSessionId("abc-123");
            assertEquals("abc-123", UserSession.getInstance().getSessionId());
        }

        @Test @DisplayName("setUserId / getUserId") void userId() {
            UserSession.getInstance().setUserId("user-001");
            assertEquals("user-001", UserSession.getInstance().getUserId());
        }

        @Test @DisplayName("setName / getName") void name() {
            UserSession.getInstance().setName("Nguyen Van A");
            assertEquals("Nguyen Van A", UserSession.getInstance().getName());
        }

        @Test @DisplayName("setUsername / getUsername") void username() {
            UserSession.getInstance().setUsername("vana");
            assertEquals("vana", UserSession.getInstance().getUsername());
        }

        @Test @DisplayName("setEmail / getEmail") void email() {
            UserSession.getInstance().setEmail("vana@email.com");
            assertEquals("vana@email.com", UserSession.getInstance().getEmail());
        }

        @Test @DisplayName("setRole / getRole") void role() {
            UserSession.getInstance().setRole("BIDDER");
            assertEquals("BIDDER", UserSession.getInstance().getRole());
        }

        @Test @DisplayName("setBalance / getBalance") void balance() {
            UserSession.getInstance().setBalance(5000.0);
            assertEquals(5000.0, UserSession.getInstance().getBalance());
        }

        @Test @DisplayName("setPhone / getPhone — nullable") void phone() {
            UserSession.getInstance().setPhone("0901234567");
            assertEquals("0901234567", UserSession.getInstance().getPhone());
        }

        @Test @DisplayName("setRating / getRating — nullable") void rating() {
            UserSession.getInstance().setRating(4.2);
            assertEquals(4.2, UserSession.getInstance().getRating());
        }

        @Test @DisplayName("getRating trả null khi chưa set") void ratingNull() {
            assertNull(UserSession.getInstance().getRating());
        }

        @Test @DisplayName("getPhone trả null khi chưa set") void phoneNull() {
            assertNull(UserSession.getInstance().getPhone());
        }
    }

    // ═══════════════════════════════════════════════════════════
    // clear()
    // ═══════════════════════════════════════════════════════════
    @Nested
    @DisplayName("clear()")
    class ClearTests {

        @Test
        @DisplayName("clear() reset toàn bộ field về null/0")
        void clear_resetsAllFields() {
            UserSession s = UserSession.getInstance();
            s.setSessionId("abc");
            s.setUserId("user-001");
            s.setName("Nguyen Van A");
            s.setUsername("vana");
            s.setEmail("vana@email.com");
            s.setRole("BIDDER");
            s.setBalance(9999.0);
            s.setPhone("0901234567");
            s.setRating(4.5);

            s.clear();

            assertNull(s.getSessionId());
            assertNull(s.getUserId());
            assertNull(s.getName());
            assertNull(s.getUsername());
            assertNull(s.getEmail());
            assertNull(s.getRole());
            assertEquals(0.0, s.getBalance());
            assertNull(s.getPhone());
            assertNull(s.getRating());
        }
    }
}