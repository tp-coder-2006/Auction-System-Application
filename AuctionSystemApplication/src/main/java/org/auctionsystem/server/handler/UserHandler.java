package org.auctionsystem.server.handler;

import com.google.gson.JsonObject;
import org.auctionsystem.server.service.UserService;

public class UserHandler {
    private final UserService userService = new UserService();

    public JsonObject handleLogin(JsonObject request) {
        return userService.loginUser(request);
    }

    public JsonObject handleRegister(JsonObject request) {
        return userService.registerUser(request);
    }

    public JsonObject handleGetProfile(JsonObject request) {
        return userService.getProfile(request);
    }

    public JsonObject handleUpdatePassword(JsonObject request) {
        return userService.updatePassword(request);
    }

    public JsonObject handleDeposit(JsonObject request) {
        return userService.deposit(request);
    }

    public JsonObject handleWithdraw(JsonObject request) {
        return userService.withdraw(request);
    }
}