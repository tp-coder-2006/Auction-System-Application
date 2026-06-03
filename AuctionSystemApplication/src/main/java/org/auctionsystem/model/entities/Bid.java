package org.auctionsystem.model.entities;

import java.time.LocalDateTime;
import java.util.UUID;

public class Bid {

    private String        id;
    private double        bidAmount;
    private LocalDateTime bidTime;
    private String        bidderId;
    private String        itemId;
    private String        itemName;    // join từ items.name, dùng cho lịch sử đấu giá
    private String        itemStatus;  // join từ items.status, dùng cho lịch sử đấu giá
    private String        bidderName;  // join từ users.username, dùng cho lịch sử bid trong phòng
    private String        sellerUsername; // join từ users.username của seller, dùng cho đánh giá
    private LocalDateTime itemEndTime; // join từ items.end_time, dùng cho dashboard widget

    public Bid() {}

    public Bid(String id, String bidderId, String itemId, double bidAmount, LocalDateTime bidTime) {
        this.id        = id;
        this.bidderId  = bidderId;
        this.itemId    = itemId;
        this.bidAmount = bidAmount;
        this.bidTime   = bidTime;
    }

    // Getters
    public String        getId()        { return id; }
    public double        getBidAmount() { return bidAmount; }
    public LocalDateTime getBidTime()   { return bidTime; }
    public String        getBidderId()  { return bidderId; }
    public String        getItemId()    { return itemId; }
    public String        getItemName()   { return itemName; }
    public String        getItemStatus() { return itemStatus; }
    public String        getBidderName() { return bidderName; }
    public String        getSellerUsername() { return sellerUsername; }
    public LocalDateTime getItemEndTime() { return itemEndTime; }

    // Setters
    public void setId(String id)                  { this.id = id; }
    public void setBidAmount(double bidAmount)     { this.bidAmount = bidAmount; }
    public void setBidTime(LocalDateTime time)     { this.bidTime = time; }
    public void setBidderId(String bidderId)       { this.bidderId = bidderId; }
    public void setItemId(String itemId)           { this.itemId = itemId; }
    public void setItemName(String itemName)       { this.itemName = itemName; }
    public void setItemStatus(String itemStatus)   { this.itemStatus = itemStatus; }
    public void setBidderName(String bidderName)   { this.bidderName = bidderName; }
    public void setSellerUsername(String sellerUsername) { this.sellerUsername = sellerUsername; }
    public void setItemEndTime(LocalDateTime t)    { this.itemEndTime = t; }
}