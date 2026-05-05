package org.auctionsystem.model;

import org.auctionsystem.igeneric.base.Entity;
import org.auctionsystem.igeneric.base.items.Item;
import org.auctionsystem.igeneric.interfaces.user.Bidder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Auction extends Entity {
    private String id;
    private Item item;
    private List<BidTransaction> bidHistory = new ArrayList<>();
    private Bidder highestBidder;
    private AuctionStatus status;
    private transient ReadWriteLock lock = new ReentrantReadWriteLock();
    public enum AuctionStatus {
        OPEN, RUNNING, FINISHED, PAID, REFUNDED, CANCELED;
    }
    public Auction(String name, Item item) {
        super(name);
        this.id = UUID.randomUUID().toString();
        this.item = item;
        this.status = AuctionStatus.OPEN;
    }

    // --- GETTERS ---
    public String getAuctionId() { return this.id; }
    public Item getItem() { return this.item; }
    public List<BidTransaction> getBidHistory() {
        if (lock == null) {
            lock = new ReentrantReadWriteLock();
        }
        lock.readLock().lock();
        try {
            return new ArrayList<>(this.bidHistory);
        } finally {
            lock.readLock().unlock();
        }
    }
    public Bidder getHighestBidder() { return this.highestBidder; }
    public AuctionStatus getStatus() { return this.status; }

    // --- SETTERS & STATE MUTATIONS (Dành cho Service và Repository gọi) ---
    public void setStatus(AuctionStatus status) {
        this.status = status;
    }

    public void addSuccessfulBid(BidTransaction transaction) {
        if (lock == null) {
            lock = new ReentrantReadWriteLock();
        }
        lock.writeLock().lock();
        try {
            this.bidHistory.add(transaction);
            this.highestBidder = transaction.getBidder();
        } finally {
            lock.writeLock().unlock();
        }
    }
}