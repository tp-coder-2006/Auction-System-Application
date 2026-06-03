package org.auctionsystem.model.entities;

import org.auctionsystem.model.enums.TransactionType;

import java.time.LocalDateTime;

/**
 * Đại diện cho một bản ghi biến động số dư của tài khoản.
 *
 * Các trường:
 *   id            — UUID của giao dịch
 *   userId        — tài khoản bị ảnh hưởng
 *   type          — loại giao dịch (DEPOSIT / WITHDRAW / BID_DEDUCT / BID_CREDIT)
 *   amount        — số tiền biến động (luôn dương; chiều +/- được suy từ type)
 *   balanceBefore — số dư trước giao dịch
 *   balanceAfter  — số dư sau giao dịch
 *   relatedItemId — item liên quan (null nếu không liên quan đến đấu giá)
 *   note          — ghi chú tự do
 *   createdAt     — thời điểm giao dịch
 */
public class Transaction {

    private String          id;
    private String          userId;
    private TransactionType type;
    private double          amount;
    private double          balanceBefore;
    private double          balanceAfter;
    private String          relatedItemId;
    private String          note;
    private LocalDateTime   createdAt;

    public Transaction() {}

    public Transaction(String id, String userId, TransactionType type,
                       double amount, double balanceBefore, double balanceAfter,
                       String relatedItemId, String note, LocalDateTime createdAt) {
        this.id            = id;
        this.userId        = userId;
        this.type          = type;
        this.amount        = amount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter  = balanceAfter;
        this.relatedItemId = relatedItemId;
        this.note          = note;
        this.createdAt     = createdAt;
    }

    // ── Getters ──────────────────────────────────────────────────────────────
    public String          getId()            { return id; }
    public String          getUserId()        { return userId; }
    public TransactionType getType()          { return type; }
    public double          getAmount()        { return amount; }
    public double          getBalanceBefore() { return balanceBefore; }
    public double          getBalanceAfter()  { return balanceAfter; }
    public String          getRelatedItemId() { return relatedItemId; }
    public String          getNote()          { return note; }
    public LocalDateTime   getCreatedAt()     { return createdAt; }

    // ── Setters ──────────────────────────────────────────────────────────────
    public void setId(String id)                          { this.id            = id; }
    public void setUserId(String userId)                  { this.userId        = userId; }
    public void setType(TransactionType type)             { this.type          = type; }
    public void setAmount(double amount)                  { this.amount        = amount; }
    public void setBalanceBefore(double balanceBefore)    { this.balanceBefore = balanceBefore; }
    public void setBalanceAfter(double balanceAfter)      { this.balanceAfter  = balanceAfter; }
    public void setRelatedItemId(String relatedItemId)    { this.relatedItemId = relatedItemId; }
    public void setNote(String note)                      { this.note          = note; }
    public void setCreatedAt(LocalDateTime createdAt)     { this.createdAt     = createdAt; }
}
