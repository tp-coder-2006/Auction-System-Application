package org.auctionsystem.model.entities;

import org.auctionsystem.model.enums.UserRole;

public class Seller extends User {

    private double rating;

    public Seller(){
        super();
    }

    public Seller(String id, String name, String username, String password, double balance, String email, UserRole role, double rating) {
        super(id, name, username, password, balance, email, role);
        this.rating = rating;
    }

    public double getRating()            { return rating; }
    public void setRating(double rating) { this.rating = rating; }
}