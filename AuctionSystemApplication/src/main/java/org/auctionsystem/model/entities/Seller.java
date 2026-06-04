package org.auctionsystem.model.entities;

import org.auctionsystem.model.enums.UserRole;

public class Seller extends User {

    private Double rating;
    private int ratingCount; // số lần được đánh giá — dùng để tính rating trung bình

    public Seller() {
        super();
    }

    public Seller(String id, String name, String username, String password,
                  double balance, String email, String phone, UserRole role,
                  Double rating, int ratingCount, boolean active, String avatarUrl) {
        super(id, name, username, password, balance, email, phone, role, active, avatarUrl);
        this.rating      = rating;
        this.ratingCount = ratingCount;
    }

    public Double getRating()              { return rating; }
    public void   setRating(Double rating) { this.rating = rating; }

    public int  getRatingCount()             { return ratingCount; }
    public void setRatingCount(int count)    { this.ratingCount = count; }
}