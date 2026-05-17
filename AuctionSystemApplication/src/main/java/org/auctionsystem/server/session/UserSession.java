package org.auctionsystem.server.session;

public class UserSession {
    private String sessionId;
    private String userId;
    private String name;     // [MỚI]
    private String username;
    private String email;    // [MỚI]
    private String role;
    private double balance;
    private String phone;
    private Double rating;   // nullable — chỉ có giá trị khi role = SELLER
    private long   lastActiveTime;

    public UserSession(String sessionId, String userId, String name, String username,
                       String email, String role, double balance, String phone, Double rating) {
        this.sessionId      = sessionId;
        this.userId         = userId;
        this.name           = name;
        this.username       = username;
        this.email          = email;
        this.role           = role;
        this.balance        = balance;
        this.phone          = phone;
        this.rating         = rating;
        this.lastActiveTime = System.currentTimeMillis();
    }

    // --- GETTERS ---
    public String getSessionId()      { return sessionId; }
    public String getUserId()         { return userId; }
    public String getName()           { return name; }
    public String getUsername()       { return username; }
    public String getEmail()          { return email; }
    public String getRole()           { return role; }
    public double getBalance()        { return balance; }
    public String getPhone()          { return phone; }
    public Double getRating()         { return rating; }
    public long   getLastActiveTime() { return lastActiveTime; }

    // --- SETTERS ---
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public void setUserId(String userId)       { this.userId = userId; }
    public void setName(String name)           { this.name = name; }
    public void setUsername(String username)   { this.username = username; }
    public void setEmail(String email)         { this.email = email; }
    public void setRole(String role)           { this.role = role; }
    public void setBalance(double balance)     { this.balance = balance; }
    public void setPhone(String phone)         { this.phone = phone; }
    public void setRating(Double rating)       { this.rating = rating; }

    public void resetLastActiveTime() {
        lastActiveTime = System.currentTimeMillis();
    }

    public boolean isExpired() {
        long timeout = 30 * 60 * 1000;
        return System.currentTimeMillis() - this.lastActiveTime > timeout;
    }
}