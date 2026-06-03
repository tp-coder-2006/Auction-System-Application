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

    public JsonObject handleGetMyProfile(JsonObject request) {
        return userService.getMyProfile(request);
    }

    // [THÊM MỚI] Cập nhật thông tin cá nhân (Tên, Email, SĐT, Avatar)
    public JsonObject handleUpdateProfile(JsonObject request) {
        return userService.updateProfile(request);
    }

    public JsonObject handleUpdatePassword(JsonObject request) {
        return userService.updatePassword(request);
    }

    // [MỚI] Đánh giá seller — chỉ bidder đã từng mua hàng mới được gọi
    public JsonObject handleUpdateRating(JsonObject request) {
        return userService.updateRating(request);
    }

    public JsonObject handleGetOtherProfile(JsonObject request) {
        return userService.getOtherProfle(request);
    }

    public JsonObject handleSearchUsers(JsonObject request) {
        return userService.searchUsers(request);
    }

    public JsonObject handleGetAllActiveUsers(JsonObject request) {
        return userService.getAllActiveUsers(request);
    }

    public JsonObject handleUploadAvatar(JsonObject request) {
        return userService.uploadAvatar(request);
    }
}