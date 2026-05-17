package org.auctionsystem.server.session;

public class UserSession {
    private String sessionId;
    private String userId;
    private String username;
    private String role;
    private double balance;
    private long lastActiveTime;

    public UserSession(String sessionId, String userId, String username, String role, double balance) {
        this.sessionId = sessionId;
        this.userId    = userId;
        this.username  = username;
        this.role      = role;
        this.balance   = balance;
        this.lastActiveTime = System.currentTimeMillis();
    }

    public String getSessionId() { return sessionId; }
    public String getUserId()    { return userId; }
    public String getUsername()  { return username; }
    public String getRole()      { return role; }
    public double getBalance()   { return balance; }
    public long getLastActiveTime() { return lastActiveTime; }

    public void resetLastActiveTime() {
        lastActiveTime = System.currentTimeMillis();
    }

    public boolean isExpired() {
        long timeout = 30 * 60 * 1000;
        return System.currentTimeMillis() - this.lastActiveTime > timeout;
    }
}
