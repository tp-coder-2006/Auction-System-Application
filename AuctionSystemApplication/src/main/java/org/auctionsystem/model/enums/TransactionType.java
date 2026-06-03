package org.auctionsystem.model.enums;

public enum TransactionType {
    DEPOSIT,    // Nạp tiền
    WITHDRAW,   // Rút tiền
    BID_DEDUCT, // Trừ tiền khi thắng đấu giá (bidder)
    BID_CREDIT  // Cộng tiền khi bán được hàng (seller)
}
