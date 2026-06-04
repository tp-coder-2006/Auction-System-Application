package org.auctionsystem.server.session;

public class UserSession {
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
    private String avatarUrl; // [MỚI]

    public UserSession(String sessionId, String userId, String name, String username,
                       String email, String role, double balance,
                       String phone, Double rating, int ratingCount, String avatarUrl) {
        this.sessionId      = sessionId;
        this.userId         = userId;
        this.name           = name;
        this.username       = username;
        this.email          = email;
        this.role           = role;
        this.balance        = balance;
        this.phone          = phone;
        this.rating         = rating;
        this.ratingCount    = ratingCount;
        this.avatarUrl      = avatarUrl; // [MỚI]
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
    public int    getRatingCount()    { return ratingCount; }
    public String getAvatarUrl()      { return avatarUrl; } // [MỚI]

    // --- SETTERS ---
    public void setName(String name)           { this.name = name; }
    public void setEmail(String email)         { this.email = email; }
    public void setBalance(double balance)     { this.balance = balance; }
    public void setPhone(String phone)         { this.phone = phone; }
    public void setRating(Double rating)       { this.rating = rating; }
    public void setRatingCount(int count)      { this.ratingCount = count; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; } // [MỚI]
}