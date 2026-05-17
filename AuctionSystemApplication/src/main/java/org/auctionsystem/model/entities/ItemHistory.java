package org.auctionsystem.model.entities;

import java.time.LocalDateTime;
import java.util.UUID;

public class ItemHistory {
    private String id;
    private String itemId;
    private String sellerId;
    private String buyerId;
    private double soldPrice;
    private LocalDateTime soldTime;

    public ItemHistory() {}

    public ItemHistory(String itemId, String sellerId, String buyerId, double soldPrice) {
        this.id        = UUID.randomUUID().toString();
        this.itemId    = itemId;
        this.sellerId  = sellerId;
        this.buyerId   = buyerId;
        this.soldPrice = soldPrice;
        this.soldTime  = LocalDateTime.now();
    }

    // Getters
    public String getId()                { return id; }
    public String getItemId()            { return itemId; }
    public String getSellerId()          { return sellerId; }
    public String getBuyerId()           { return buyerId; }
    public double getSoldPrice()         { return soldPrice; }
    public LocalDateTime getSoldTime()   { return soldTime; }

    // Setters
    public void setId(String id)                       { this.id = id; }
    public void setItemId(String itemId)               { this.itemId = itemId; }
    public void setSellerId(String sellerId)           { this.sellerId = sellerId; }
    public void setBuyerId(String buyerId)             { this.buyerId = buyerId; }
    public void setSoldPrice(double soldPrice)         { this.soldPrice = soldPrice; }
    public void setSoldTime(LocalDateTime soldTime)    { this.soldTime = soldTime; }
}