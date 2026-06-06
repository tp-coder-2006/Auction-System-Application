package org.auctionsystem.server.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionManager Tests")
class SessionManagerTest {

    private UserSession session;

    @BeforeEach
    void setUp() {
        // Xóa session cũ trước mỗi test
        SessionManager.removeSession("test-session-001");
        SessionManager.removeSession("test-session-002");

        session = new UserSession(
                "test-session-001",
                "user-001",
                "Nguyen Van A",
                "vana",
                "vana@email.com",
                "BIDDER",
                1000.0,
                null,
                null,
                0,
                null
        );
    }

    // ═══════════════════════════════════════════════════════════
    // addSession / getSession
    // ═══════════════════════════════════════════════════════════
    @Nested
    @DisplayName("addSession() và getSession()")
    class AddGetTests {

        @Test
        @DisplayName("Thêm session rồi lấy lại được")
        void addSession_thenGetSession_returnsSession() {
            SessionManager.addSession(session);
            UserSession result = SessionManager.getSession("test-session-001");
            assertNotNull(result);
            assertEquals("user-001", result.getUserId());
        }

        @Test
        @DisplayName("getSession() với id không tồn tại trả null")
        void getSession_nonExistentId_returnsNull() {
            assertNull(SessionManager.getSession("id-khong-ton-tai"));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // removeSession
    // ═══════════════════════════════════════════════════════════
    @Nested
    @DisplayName("removeSession()")
    class RemoveTests {

        @Test
        @DisplayName("Xóa session — getSession() trả null sau đó")
        void removeSession_thenGetReturnsNull() {
            SessionManager.addSession(session);
            SessionManager.removeSession("test-session-001");
            assertNull(SessionManager.getSession("test-session-001"));
        }

        @Test
        @DisplayName("Xóa session không tồn tại — không throw exception")
        void removeNonExistentSession_doesNotThrow() {
            assertDoesNotThrow(() -> SessionManager.removeSession("id-khong-co"));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // findSessionIdByUserId
    // ═══════════════════════════════════════════════════════════
    @Nested
    @DisplayName("findSessionIdByUserId()")
    class FindSessionTests {

        @Test
        @DisplayName("Tìm được sessionId theo userId đang online")
        void findSessionIdByUserId_existingUser_returnsSessionId() {
            SessionManager.addSession(session);
            assertEquals("test-session-001", SessionManager.findSessionIdByUserId("user-001"));
        }

        @Test
        @DisplayName("User không online trả null")
        void findSessionIdByUserId_missingUser_returnsNull() {
            assertNull(SessionManager.findSessionIdByUserId("user-khong-ton-tai"));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // getAllSessions
    // ═══════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getAllSessions()")
    class GetAllTests {

        @Test
        @DisplayName("getAllSessions() chứa session vừa thêm")
        void getAllSessions_containsAddedSession() {
            SessionManager.addSession(session);
            assertTrue(SessionManager.getAllSessions()
                    .stream()
                    .anyMatch(s -> s.getSessionId().equals("test-session-001")));
        }
    }
}
