package org.auctionsystem.client.session;

public class UserSession {
    private static UserSession instance = null;

    private String sessionId;
    private String userId;
    private String name;
    private String username;
    private String email;
    private String role;
    private double balance;
    private String phone;
    private Double rating;
    private int    ratingCount;
    private String avatarUrl;

    private UserSession() {}

    public static UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
        }
        return instance;
    }

    // --- GETTERS ---
    public String getSessionId()   { return sessionId; }
    public String getUserId()      { return userId; }
    public String getName()        { return name; }
    public String getUsername()    { return username; }
    public String getEmail()       { return email; }
    public String getRole()        { return role; }
    public double getBalance()     { return balance; }
    public String getPhone()       { return phone; }
    public Double getRating()      { return rating; }
    public int    getRatingCount() { return ratingCount; }
    public String getAvatarUrl()   { return avatarUrl; }

    // --- SETTERS ---
    public void setSessionId(String sessionId)   { this.sessionId   = sessionId; }
    public void setUserId(String userId)         { this.userId      = userId; }
    public void setName(String name)             { this.name        = name; }
    public void setUsername(String username)     { this.username    = username; }
    public void setEmail(String email)           { this.email       = email; }
    public void setRole(String role)             { this.role        = role; }
    public void setBalance(double balance)       { this.balance     = balance; }
    public void setPhone(String phone)           { this.phone       = phone; }
    public void setRating(Double rating)         { this.rating      = rating; }
    public void setRatingCount(int ratingCount)  { this.ratingCount = ratingCount; }
    public void setAvatarUrl(String avatarUrl)   { this.avatarUrl   = avatarUrl; }

    public void clear() {
        this.sessionId   = null;
        this.userId      = null;
        this.name        = null;
        this.username    = null;
        this.email       = null;
        this.role        = null;
        this.balance     = 0;
        this.phone       = null;
        this.rating      = null;
        this.ratingCount = 0;
        this.avatarUrl   = null;
    }
}
