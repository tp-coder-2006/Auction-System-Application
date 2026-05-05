package org.auctionsystem.model.user;

import org.auctionsystem.igeneric.base.items.Item;
import org.auctionsystem.igeneric.base.users.User;
import org.auctionsystem.igeneric.interfaces.user.*;
import org.auctionsystem.model.BidTransaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SystemUser extends User implements Bidder, Seller, Password, Email {

    // 1. Thuộc tính định danh & Bảo mật
    private String email;
    private String password;

    // 2. Thuộc tính trạng thái (Dữ liệu thô)
    private double balance = 0.0;
    private double rating = 5.0;

    // 3. Danh sách dữ liệu (Nên khởi tạo sẵn để tránh NullPointerException)
    private final List<BidTransaction> bidTransactions = new ArrayList<>();
    private final List<Item> wonItems = new ArrayList<>();
    private final List<Item> auctionItems = new ArrayList<>();

    // --- CONSTRUCTORS ---
    public SystemUser(String name, String email, String password) {
        super(name);
        this.email = email;
        this.password = password;
    }

    public SystemUser() {
        super();
    }

    // --- INTERFACE IMPLEMENTATIONS (Logic chính của vai trò) ---

    @Override
    public String getBidderName() {
        return getName();
    }

    @Override
    public String getSellerName() {
        return getName();
    }

    // --- OPERATIONAL METHODS (Thao tác danh sách & Tiền nong) ---

    public void deposit(double money) {
        if (money > 0) this.balance += money;
    }

    public boolean pay(double money) {
        if (money > 0 && this.balance >= money) {
            this.balance -= money;
            return true;
        }
        return false;
    }

    public void addTransaction(BidTransaction transaction) {
        this.bidTransactions.add(transaction);
    }

    public void addWonItem(Item item) {
        this.wonItems.add(item);
    }

    public void addAuctionItem(Item item) {
        this.auctionItems.add(item);
    }

    public void removeAuctionItem(Item item) {
        this.auctionItems.remove(item);
    }

    // --- GETTERS & SETTERS (Chỉ truy xuất dữ liệu thô) ---

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public double getBalance() { return balance; }

    public double getRating() { return rating; }

    // Bảo vệ List bằng Unmodifiable để tránh tầng ngoài tự ý add/remove sai logic
    public List<Item> getAuctionItems() { return Collections.unmodifiableList(auctionItems); }
    public List<Item> getWonItems() { return Collections.unmodifiableList(wonItems); }
    public List<BidTransaction> getBidHistory() { return Collections.unmodifiableList(bidTransactions); }
}