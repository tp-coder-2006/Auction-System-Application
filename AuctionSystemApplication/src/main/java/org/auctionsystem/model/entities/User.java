package org.auctionsystem.model.entities;

import org.auctionsystem.model.enums.UserRole;

public class User {

    private String id;
    private String name;
    private String username;
    private String password;
    private double balance;
    private boolean isActive;
    private String email;
    private UserRole role;

    public User() {}

    public User(String id, String name, String username, String password, double balance, String email, UserRole role) {
        this.id       = id;
        this.name     = name;
        this.username = username;
        this.password = password;
        this.email    = email;
        this.role     = role;
        this.balance  = balance;
        this.isActive = true;
    }

    // =========================================
    // GETTERS
    // =========================================
    public String getId()       { return id; }
    public String getName()     { return name; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public double getBalance()  { return balance; }
    public boolean isActive()   { return isActive; }
    public String getEmail()    { return email; }
    public UserRole getRole()   { return role; }

    // =========================================
    // SETTERS
    // =========================================
    public void setId(String id)                 { this.id = id; }
    public void setName(String name)             { this.name = name; }
    public void setUsername(String username)     { this.username = username; }
    public void setPassword(String password)     { this.password = password; }
    public void setBalance(double balance)       { this.balance = balance; }
    public void setActive(boolean isActive)      { this.isActive = isActive; }
    public void setEmail(String email)           { this.email = email; }
    public void setRole(UserRole role)           { this.role = role; }
}