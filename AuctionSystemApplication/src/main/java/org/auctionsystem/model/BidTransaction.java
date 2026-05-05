package org.auctionsystem.model;

import org.auctionsystem.igeneric.base.items.Item;
import org.auctionsystem.igeneric.interfaces.user.Bidder;

import java.time.LocalDateTime;
import java.util.UUID;

public class BidTransaction {
    private String transactionId;
    private Bidder bidder;
    private Item item;
    private LocalDateTime bidTime;
    private double bidPrice;

    // --- CONSTRUCTORS ---
    public BidTransaction() {}

    public BidTransaction(Bidder bidder, Item item, LocalDateTime bidTime, double bidPrice) {
        this.transactionId = UUID.randomUUID().toString();
        this.bidder = bidder;
        this.item = item;
        this.bidTime = bidTime;
        this.bidPrice = bidPrice;
    }

    public BidTransaction(BidTransaction bidTransaction) {
        this.transactionId = bidTransaction.transactionId;
        this.bidder = bidTransaction.bidder;
        this.item = bidTransaction.item;
        this.bidTime = bidTransaction.bidTime;
        this.bidPrice = bidTransaction.bidPrice;
    }

    // --- GETTERS ---
    public String getTransactionId() {
        return transactionId;
    }

    public Bidder getBidder() {
        return bidder;
    }

    public Item getItem() {
        return item;
    }

    public LocalDateTime getBidTime() {
        return bidTime;
    }

    public double getBidPrice() {
        return bidPrice;
    }

    // --- SETTERS ---
    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public void setBidder(Bidder bidder) {
        this.bidder = bidder;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public void setBidTime(LocalDateTime bidTime) {
        this.bidTime = bidTime;
    }

    public void setBidPrice(double bidPrice) {
        this.bidPrice = bidPrice;
    }
}