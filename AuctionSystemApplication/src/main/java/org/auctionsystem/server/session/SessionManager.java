package org.auctionsystem.server.session;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    private static final Map<String, UserSession> sessions = new ConcurrentHashMap<>();

    public static void addSession(UserSession userSession) {
        sessions.put(userSession.getSessionId(), userSession);
    }

    public static void removeSession(String sessionId) {
        sessions.remove(sessionId);
    }

    public static UserSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public static Collection<UserSession> getAllSessions() {
        return sessions.values();
    }

    /**
     * Tìm sessionId của user đang online theo userId.
     * Trả về null nếu user không có session nào đang hoạt động.
     * Dùng cho ConnectedClientRegistry.sendTo() khi cần gửi event chỉ đến 1 user cụ thể.
     */
    public static String findSessionIdByUserId(String userId) {
        return sessions.values().stream()
                .filter(s -> s.getUserId().equals(userId))
                .map(UserSession::getSessionId)
                .findFirst()
                .orElse(null);
    }
}