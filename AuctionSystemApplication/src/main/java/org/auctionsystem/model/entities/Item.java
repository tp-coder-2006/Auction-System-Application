package org.auctionsystem.model.entities;

import org.auctionsystem.model.enums.ItemStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public class Item {

    private String id;
    private String name;
    private String description;
    private double startingPrice;
    private Double currentHighestPrice;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private ItemStatus status;
    private String sellerId;
    private String ownerId;

    public Item() {}

    public Item(String name, String description, double startingPrice,
                LocalDateTime startTime, LocalDateTime endTime,
                ItemStatus itemStatus, String sellerId) {
        this.id                  = UUID.randomUUID().toString();
        this.name                = name;
        this.description         = description;
        this.startingPrice       = startingPrice;
        this.currentHighestPrice = null;
        this.startTime           = startTime;
        this.endTime             = endTime;
        this.status              = itemStatus;
        this.sellerId            = sellerId;
        this.ownerId             = sellerId; // owner ban đầu = seller
    }

    // Getters
    public String getId()                  { return id; }
    public String getName()                { return name; }
    public String getDescription()         { return description; }
    public double getStartingPrice()       { return startingPrice; }
    public Double getCurrentHighestPrice() { return currentHighestPrice; }
    public LocalDateTime getStartTime()    { return startTime; }
    public LocalDateTime getEndTime()      { return endTime; }
    public ItemStatus getStatus()          { return status; }
    public String getSellerId()            { return sellerId; }
    public String getOwnerId()             { return ownerId; }

    // Setters
    public void setId(String id)                             { this.id = id; }
    public void setName(String name)                         { this.name = name; }
    public void setDescription(String description)           { this.description = description; }
    public void setStartingPrice(double startingPrice)       { this.startingPrice = startingPrice; }
    public void setCurrentHighestPrice(Double price)         { this.currentHighestPrice = price; }
    public void setStartTime(LocalDateTime startTime)        { this.startTime = startTime; }
    public void setEndTime(LocalDateTime endTime)            { this.endTime = endTime; }
    public void setStatus(ItemStatus status)                 { this.status = status; }
    public void setSellerId(String sellerId)                 { this.sellerId = sellerId; }
    public void setOwnerId(String ownerId)                   { this.ownerId = ownerId; }
}