package org.auctionsystem.server.handler;

import com.google.gson.JsonObject;
import org.auctionsystem.server.service.TransactionService;

public class TransactionHandler {

    private final TransactionService transactionService = new TransactionService();

    /** Nạp tiền vào tài khoản. */
    public JsonObject handleDeposit(JsonObject request) {
        return transactionService.deposit(request);
    }

    /** Rút tiền khỏi tài khoản. */
    public JsonObject handleWithdraw(JsonObject request) {
        return transactionService.withdraw(request);
    }

    /** User xem toàn bộ lịch sử biến động số dư của chính mình. */
    public JsonObject handleGetMyTransactions(JsonObject request) {
        return transactionService.getMyTransactions(request);
    }

    /** User xem lịch sử biến động lọc theo loại (DEPOSIT / WITHDRAW / BID_DEDUCT / BID_CREDIT). */
    public JsonObject handleGetMyTransactionsByType(JsonObject request) {
        return transactionService.getMyTransactionsByType(request);
    }
}
