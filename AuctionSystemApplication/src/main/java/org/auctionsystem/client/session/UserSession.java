package org.auctionsystem.client.session;

import java.io.StringReader;

public class UserSession {
    private static UserSession instance=null;

    private String sessionId;
    private String userId;
    private String username;
    private String role;
    private double balance;

    private UserSession() {
    }

    public static UserSession getInstance(){
        if(instance==null){
            instance=new UserSession();
        }
        return instance;
    }

    // --- GETTERS ---

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public double getBalance() {
        return balance;
    }

    // --- SETTERS ---

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public void clear() {
        this.sessionId = null;
        this.userId    = null;
        this.username  = null;
        this.role      = null;
        this.balance   = 0;
    }
}
