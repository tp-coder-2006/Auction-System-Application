package org.auctionsystem.model.entities;

import java.time.LocalDateTime;
import java.util.UUID;

public class Bid {

    private String id;
    private double bidAmount;
    private LocalDateTime bidTime;
    private String bidderId;
    private String itemId;

    public Bid(){}

    public Bid(String id, String bidderId, String itemId, double bidAmount, LocalDateTime bidTime) {
        this.id        = id;
        this.bidderId    = bidderId;
        this.itemId      = itemId;
        this.bidAmount = bidAmount;
        this.bidTime   = bidTime;
    }

    // Getters
    public String getId()             { return id; }
    public double getBidAmount()      { return bidAmount; }
    public LocalDateTime getBidTime() { return bidTime; }
    public String getBidderId()           { return bidderId; }
    public String getItemId()             { return itemId; }

    // Setters
    public void setId(String id)               { this.id = id; }
    public void setBidAmount(double bidAmount)  { this.bidAmount = bidAmount; }
    public void setBidTime(LocalDateTime time)  { this.bidTime = time; }
    public void setBidderId(String bidderId)          { this.bidderId = bidderId; }
    public void setItemId(String itemId)              { this.itemId = itemId; }
}