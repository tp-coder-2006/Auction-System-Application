package org.auctionsystem.server.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import org.auctionsystem.server.util.GsonConfig;
import com.google.gson.JsonObject;
import org.apache.commons.validator.routines.EmailValidator;
import org.auctionsystem.model.entities.Seller;
import org.auctionsystem.server.session.SessionManager;
import org.auctionsystem.server.session.UserSession;
import org.auctionsystem.model.entities.User;
import org.auctionsystem.server.DAO.UserDAO;
import org.mindrot.jbcrypt.BCrypt;

import org.auctionsystem.server.DAO.ImageDAO;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UserService {

    // ─── GIỮ NGUYÊN ──────────────────────────────────────────────────────────
    private static boolean isSpecialChar(char c) {
        String specialChars = "@#$%^&+=!";
        return specialChars.contains(String.valueOf(c));
    }

    // ─── GIỮ NGUYÊN ──────────────────────────────────────────────────────────
    public boolean isEmailValid(String email){
        EmailValidator validator = EmailValidator.getInstance();
        return validator.isValid(email);
    }

    // ─── GIỮ NGUYÊN ──────────────────────────────────────────────────────────
    public static String hashPassword(String plainPassword) {
        String salt = BCrypt.gensalt(10);
        return BCrypt.hashpw(plainPassword, salt);
    }

    // ─── GIỮ NGUYÊN ──────────────────────────────────────────────────────────
    public static List<String> validatePassword(String password) {
        List<String> errors = new ArrayList<>();

        if (password == null || password.isEmpty()) {
            errors.add("Mật khẩu không được để trống");
            return errors;
        }

        if (password.length() < 8)
            errors.add("Mật khẩu phải có ít nhất 8 ký tự");

        boolean hasUpper   = false;
        boolean hasLower   = false;
        boolean hasDigit   = false;
        boolean hasSpecial = false;

        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);

            if (Character.isWhitespace(c)) {
                errors.add("Mật khẩu không được chứa khoảng trắng");
                break;
            }

            if (Character.isUpperCase(c)) hasUpper   = true;
            else if (Character.isLowerCase(c)) hasLower   = true;
            else if (Character.isDigit(c))     hasDigit   = true;
            else if (isSpecialChar(c))         hasSpecial = true;
        }

        if (!hasUpper)   errors.add("Mật khẩu phải có ít nhất 1 chữ hoa (A-Z)");
        if (!hasLower)   errors.add("Mật khẩu phải có ít nhất 1 chữ thường (a-z)");
        if (!hasDigit)   errors.add("Mật khẩu phải có ít nhất 1 chữ số (0-9)");
        if (!hasSpecial) errors.add("Mật khẩu phải có ít nhất 1 ký tự đặc biệt (!@#$...)");

        return errors;
    }

    // ─── GIỮ NGUYÊN (Avatar mặc định là NULL trong DAO) ──────────────────────
    public JsonObject registerUser(JsonObject request) {
        JsonObject response = new JsonObject();
        UserDAO userDAO = new UserDAO();

        try {
            // Kiểm tra các field bắt buộc trước khi xử lý
            for (String field : new String[]{"username", "password", "name", "role"}) {
                if (!request.has(field) || request.get(field).isJsonNull()
                        || request.get(field).getAsString().trim().isEmpty()) {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Thiếu thông tin bắt buộc: " + field);
                    return response;
                }
            }

            String username = request.get("username").getAsString().trim();
            String password = request.get("password").getAsString();
            String name = request.get("name").getAsString().trim();
            String email = request.has("email") ? request.get("email").getAsString() : "";
            String phone = request.has("phone") && !request.get("phone").isJsonNull()
                    ? request.get("phone").getAsString() : null;
            String roleString = request.get("role").getAsString().toUpperCase();

            if (!this.isEmailValid(email)) {
                response.addProperty("status", "error");
                response.addProperty("message", "Email không hợp lệ!");
                return response;
            }

            List<String> checkPassword = validatePassword(password);
            if(checkPassword.size()>0) {
                response.addProperty("status", "error");
                response.addProperty("message", checkPassword.toString());
                return response;
            }

            String hashedPassword = UserService.hashPassword(password);

            if (userDAO.isUsernameExist(username)) {
                response.addProperty("status", "error");
                response.addProperty("message", "Tên đăng nhập đã tồn tại!");
                return response;
            }
            if (userDAO.isEmailExist(email)) {
                response.addProperty("status", "error");
                response.addProperty("message", "Email đã được đăng ký!");
                return response;
            }
            if (phone != null && !phone.isBlank() && userDAO.isPhoneExist(phone)) {
                response.addProperty("status", "error");
                response.addProperty("message", "Số điện thoại đã được đăng ký!");
                return response;
            }

            // DAO sẽ tự gán NULL cho avatar_url theo yêu cầu trước đó của bạn
            boolean isSaved = userDAO.registerUser(name, username, hashedPassword, email, phone, roleString);

            if (isSaved) {
                response.addProperty("status", "success");
                response.addProperty("message", "Đăng ký tài khoản thành công!");
            } else {
                response.addProperty("status", "error");
                response.addProperty("message", "Đăng ký thất bại! Vui lòng thử lại.");
            }

        } catch (IllegalArgumentException e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Vai trò (Role) không hợp lệ!");
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("DUPLICATE_KEY:")) {
                String msg = e.getMessage();
                if (msg.contains("username")) response.addProperty("message", "Tên đăng nhập đã tồn tại!");
                else if (msg.contains("email")) response.addProperty("message", "Email đã được đăng ký!");
                else if (msg.contains("phone")) response.addProperty("message", "Số điện thoại đã được đăng ký!");
                else response.addProperty("message", "Thông tin đã tồn tại trong hệ thống!");
                response.addProperty("status", "error");
            } else {
                response.addProperty("status", "error");
                response.addProperty("message", "Lỗi server!");
            }
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi server!");
        }
        return response;
    }

    // ─── CÓ CẬP NHẬT (THÊM AVATAR_URL VÀO RESPONSE VÀ SESSION) ─────────────────
    public JsonObject loginUser(JsonObject request) {
        JsonObject response = new JsonObject();
        UserDAO userDAO = new UserDAO();

        try {
            if (!request.has("username") || !request.has("password")
                    || request.get("username").isJsonNull()
                    || request.get("password").isJsonNull()) {
                response.addProperty("status", "error");
                response.addProperty("message", "Vui long nhap ten dang nhap va mat khau!");
                return response;
            }

            String username = request.get("username").getAsString().trim();
            String password = request.get("password").getAsString();

            if (username.isEmpty() || password.isEmpty()) {
                response.addProperty("status", "error");
                response.addProperty("message", "Vui long nhap ten dang nhap va mat khau!");
                return response;
            }

            User loggedInUser = userDAO.getProfileByUsername(username);

            if (loggedInUser != null) {
                if (!loggedInUser.isActive()) {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Tài khoản đã bị khóa! Vui lòng liên hệ quản trị viên.");
                    return response;
                }
                if (BCrypt.checkpw(password, loggedInUser.getPassword())){
                    String sessionId= UUID.randomUUID().toString();

                    String phone = loggedInUser.getPhone();
                    Double rating = null;
                    int ratingCount = 0;

                    if (loggedInUser instanceof Seller seller) {
                        rating      = seller.getRating();
                        ratingCount = seller.getRatingCount();
                    }

                    response.addProperty("status",     "success");
                    response.addProperty("message",    "Đăng nhập thành công!");
                    response.addProperty("session_id", sessionId);
                    response.addProperty("user_id",    loggedInUser.getId());
                    response.addProperty("name",       loggedInUser.getName());
                    response.addProperty("username",   loggedInUser.getUsername());
                    response.addProperty("email",      loggedInUser.getEmail());
                    response.addProperty("role",       loggedInUser.getRole().name());
                    response.addProperty("balance",    loggedInUser.getBalance());

                    String avatarUrl = loggedInUser.getAvatarUrl(); // [MỚI]

                    if (phone     != null) response.addProperty("phone",      phone);
                    if (rating    != null) response.addProperty("rating",     rating);
                    if (avatarUrl != null) response.addProperty("avatar_url", avatarUrl); // [MỚI]
                    response.addProperty("rating_count", ratingCount);

                    UserSession userSession = new UserSession(
                            sessionId, loggedInUser.getId(), loggedInUser.getName(),
                            loggedInUser.getUsername(), loggedInUser.getEmail(),
                            loggedInUser.getRole().name(), loggedInUser.getBalance(),
                            phone, rating, ratingCount, avatarUrl // [MỚI]
                    );

                    SessionManager.addSession(userSession);
                }else{
                    response.addProperty("status", "error");
                    response.addProperty("message", "Sai mật khẩu!");
                }
            }else{
                response.addProperty("status", "error");
                response.addProperty("message", "Tai khoan khong ton tai!");
            }

        } catch (Exception e) {
            System.err.println("[loginUser] Lỗi: " + e.getClass().getSimpleName());
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi máy chủ!");
        }
        return response;
    }

    // ─── CÓ CẬP NHẬT (ĐỒNG BỘ AVATAR_URL VÀO SESSION) ──────────────────────────
    public JsonObject getMyProfile(JsonObject request) {
        JsonObject response = new JsonObject();
        UserDAO userDAO = new UserDAO();
        Gson gson = GsonConfig.create();

        try {
            String id = request.get("user_id").getAsString();
            User user = userDAO.getProfileById(id);
            if (user == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Không tìm thấy thông tin người dùng!");
            }else {
                if (request.has("session_id") && !request.get("session_id").isJsonNull()) {
                    UserSession session = SessionManager.getSession(request.get("session_id").getAsString());
                    if (session != null) {
                        session.setBalance(user.getBalance());
                        session.setPhone(user.getPhone());
                        if (user instanceof Seller seller) {
                            session.setRating(seller.getRating());
                            session.setRatingCount(seller.getRatingCount());
                        }
                    }
                }

                JsonObject userJson = gson.toJsonTree(user).getAsJsonObject();
                userJson.remove("password");
                response.addProperty("status", "success");
                response.addProperty("message", "Lấy thông tin thành công!");
                response.add("information", userJson);
            }
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return response;
    }

    // ─── GIỮ NGUYÊN (Gson tự động map avatar_url từ entity) ───────────────────
    public JsonObject getOtherProfle(JsonObject request) {
        JsonObject response = new JsonObject();
        UserDAO userDAO = new UserDAO();
        Gson gson = GsonConfig.create();

        try {
            String username = request.get("username").getAsString();
            User user = userDAO.getProfileByUsername(username);
            if (user == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Không tìm thấy thông tin người dùng!");
            } else if(!user.isActive()){
                response.addProperty("status", "banned");
                response.addProperty("message","Tai khoan da bi khoa!");
            }
            else {
                JsonObject userJson = gson.toJsonTree(user).getAsJsonObject();
                userJson.remove("password");
                response.addProperty("status", "success");
                response.addProperty("message", "Lấy thông tin thành công!");
                response.add("information", userJson);
            }
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return response;
    }

    /**
     * Lấy toàn bộ user đang hoạt động (is_active=true, không gồm admin).
     */
    public JsonObject getAllActiveUsers(JsonObject request) {
        JsonObject response = new JsonObject();
        UserDAO userDAO = new UserDAO();
        Gson gson = GsonConfig.create();
        try {
            List<User> users = userDAO.getAllActiveUsers();
            JsonArray arr = new JsonArray();
            for (User u : users) {
                JsonObject obj = gson.toJsonTree(u).getAsJsonObject();
                obj.remove("password");
                obj.remove("balance");
                arr.add(obj);
            }
            response.addProperty("status", "success");
            response.add("users", arr);
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return response;
    }

    /**
     * Tìm kiếm user theo keyword (username / name / email chứa chuỗi).
     * Trả về mảng JSON các user công khai (không có password/balance).
     */
    public JsonObject searchUsers(JsonObject request) {
        JsonObject response = new JsonObject();
        UserDAO userDAO = new UserDAO();
        Gson gson = GsonConfig.create();
        try {
            String keyword = request.has("keyword") ? request.get("keyword").getAsString().trim() : "";
            if (keyword.isEmpty()) {
                response.addProperty("status", "error");
                response.addProperty("message", "Keyword không được để trống.");
                return response;
            }
            List<User> users = userDAO.searchByKeyword(keyword);
            JsonArray arr = new JsonArray();
            for (User u : users) {
                JsonObject obj = gson.toJsonTree(u).getAsJsonObject();
                obj.remove("password");
                obj.remove("balance");
                arr.add(obj);
            }
            response.addProperty("status", "success");
            response.add("users", arr);
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return response;
    }

    // ─── GIỮ NGUYÊN ──────────────────────────────────────────────────────────
    public JsonObject updatePassword(JsonObject request) {
        JsonObject response = new JsonObject();
        UserDAO userDAO = new UserDAO();

        try {
            String userId      = request.get("user_id").getAsString();
            String oldPassword = request.get("old_password").getAsString();
            String newPassword = request.get("new_password").getAsString();

            List<String> errors = validatePassword(newPassword);
            if (!errors.isEmpty()) {
                response.addProperty("status", "error");
                response.addProperty("message", errors.toString());
                return response;
            }

            User user = userDAO.getProfileById(userId);
            if (user == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Không tìm thấy người dùng!");
                return response;
            }

            if (!BCrypt.checkpw(oldPassword, user.getPassword())) {
                response.addProperty("status", "error");
                response.addProperty("message", "Mật khẩu cũ không đúng!");
                return response;
            }

            if (userDAO.updatePassword(userId, hashPassword(newPassword))) {
                response.addProperty("status", "success");
                response.addProperty("message", "Đổi mật khẩu thành công!");
            } else {
                response.addProperty("status", "error");
                response.addProperty("message", "Đổi mật khẩu thất bại!");
            }
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return response;
    }

    // ─── CẬP NHẬT PROFILE (tích hợp upload avatar, 1 request duy nhất) ────────
    public JsonObject updateProfile(JsonObject request) {
        JsonObject response = new JsonObject();
        UserDAO  userDAO  = new UserDAO();
        ImageDAO imageDAO = new ImageDAO();

        String newAvatarAbsPath = null; // dùng để rollback file nếu có lỗi sau khi ghi

        try {
            String userId = request.get("user_id").getAsString();
            String name   = request.get("name").getAsString();
            String email  = request.get("email").getAsString();
            String phone  = request.has("phone") && !request.get("phone").isJsonNull()
                    ? request.get("phone").getAsString() : null;

            if (!isEmailValid(email)) {
                response.addProperty("status", "error");
                response.addProperty("message", "Email không hợp lệ!");
                return response;
            }

            // ── Xử lý ảnh (nếu có) ───────────────────────────────────────────
            boolean hasImage = request.has("image_data")
                    && !request.get("image_data").isJsonNull()
                    && !request.get("image_data").getAsString().isBlank()
                    && request.has("extension")
                    && !request.get("extension").isJsonNull();

            String finalAvatarUrl = null;

            if (hasImage) {
                String imageData = request.get("image_data").getAsString();
                String extension = request.get("extension").getAsString().toLowerCase();

                // Validate extension
                if (!ImageService.isAllowedExtension(extension)) {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Định dạng ảnh không hợp lệ! Chỉ chấp nhận: jpg, jpeg, png, webp");
                    return response;
                }

                // Decode base64
                byte[] imageBytes = ImageService.decodeBase64(imageData);
                if (imageBytes == null) {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Dữ liệu ảnh base64 không hợp lệ!");
                    return response;
                }
                if (imageBytes.length > 5 * 1024 * 1024L) {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Ảnh quá lớn! Tối đa 5MB.");
                    return response;
                }

                // Tra cứu ảnh cũ trước khi làm bất cứ điều gì
                String oldAvatarPath = imageDAO.getCurrentAvatarPath(userId);

                // Ghi file mới lên disk
                String newImageId  = UUID.randomUUID().toString();
                String newRelative = "avatars/" + newImageId + "." + extension;
                String newAbsolute = "auction_images/" + newRelative;
                newAvatarAbsPath   = newAbsolute;

                try {
                    ImageService.writeFile(newAbsolute, imageBytes);
                } catch (IOException ioEx) {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Lỗi ghi file ảnh: " + ioEx.getMessage());
                    return response;
                }

                // Đăng ký metadata ảnh mới vào DB
                boolean registered = imageDAO.registerImage(newImageId, newRelative, "avatar", userId);
                if (!registered) {
                    ImageService.deleteFileQuietly(newAbsolute);
                    response.addProperty("status", "error");
                    response.addProperty("message", "Lỗi đăng ký ảnh vào database!");
                    return response;
                }

                // Dọn ảnh cũ (chỉ sau khi ảnh mới đã được đăng ký thành công)
                if (oldAvatarPath != null) {
                    ImageService.deleteFileQuietly("auction_images/" + oldAvatarPath);
                    imageDAO.deleteImageRecord(oldAvatarPath);
                }

                finalAvatarUrl   = newRelative;
                newAvatarAbsPath = null; // đã xử lý xong, không cần rollback file nữa
            } else {
                // Không có ảnh mới → giữ nguyên avatar_url hiện tại trong DB
                finalAvatarUrl = imageDAO.getCurrentAvatarPath(userId);
            }

            // ── Cập nhật profile vào DB ───────────────────────────────────────
            if (userDAO.updateProfile(userId, name, email, phone, finalAvatarUrl)) {
                response.addProperty("status", "success");
                response.addProperty("message", "Cập nhật thông tin thành công!");
                if (finalAvatarUrl != null) {
                    response.addProperty("avatar_url", finalAvatarUrl);
                }

                // Cập nhật session
                for (UserSession session : SessionManager.getAllSessions()) {
                    if (session.getUserId().equals(userId)) {
                        session.setName(name);
                        session.setEmail(email);
                        session.setPhone(phone);
                        session.setAvatarUrl(finalAvatarUrl);
                        // Không break — cho phép đăng nhập nhiều thiết bị
                    }
                }
            } else {
                // Rollback: nếu có ảnh mới vừa đăng ký thì xóa đi
                if (hasImage && finalAvatarUrl != null) {
                    ImageService.deleteFileQuietly("auction_images/" + finalAvatarUrl);
                    imageDAO.deleteImageRecord(finalAvatarUrl);
                }
                response.addProperty("status", "error");
                response.addProperty("message", "Cập nhật thất bại!");
            }

        } catch (Exception e) {
            // Rollback file nếu đã ghi nhưng chưa kịp xử lý tiếp
            if (newAvatarAbsPath != null) ImageService.deleteFileQuietly(newAvatarAbsPath);
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi máy chủ: " + e.getMessage());
        }
        return response;
    }

    public JsonObject uploadAvatar(JsonObject request) {
        JsonObject response = new JsonObject();
        UserDAO userDAO = new UserDAO();

        try {
            if (!request.has("user_id") || request.get("user_id").isJsonNull()) {
                response.addProperty("status", "error");
                response.addProperty("message", "Thieu user_id!");
                return response;
            }
            if (!request.has("image_data") || request.get("image_data").isJsonNull()
                    || request.get("image_data").getAsString().isBlank()
                    || !request.has("extension") || request.get("extension").isJsonNull()) {
                response.addProperty("status", "error");
                response.addProperty("message", "Thieu du lieu anh!");
                return response;
            }

            String userId = request.get("user_id").getAsString();
            User user = userDAO.getProfileById(userId);
            if (user == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Khong tim thay nguoi dung!");
                return response;
            }

            JsonObject updateRequest = new JsonObject();
            updateRequest.addProperty("user_id", userId);
            updateRequest.addProperty("name", user.getName());
            updateRequest.addProperty("email", user.getEmail());
            if (user.getPhone() != null) {
                updateRequest.addProperty("phone", user.getPhone());
            } else {
                updateRequest.addProperty("phone", (String) null);
            }
            updateRequest.addProperty("image_data", request.get("image_data").getAsString());
            updateRequest.addProperty("extension", request.get("extension").getAsString());

            return updateProfile(updateRequest);
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Loi may chu: " + e.getMessage());
            return response;
        }
    }


    // ─── GIỮ NGUYÊN ──────────────────────────────────────────────────────────
    public JsonObject updateRating(JsonObject request) {
        JsonObject response = new JsonObject();
        UserDAO userDAO = new UserDAO();
        org.auctionsystem.server.DAO.ItemHistoryDAO historyDAO =
                new org.auctionsystem.server.DAO.ItemHistoryDAO();
        org.auctionsystem.server.DAO.RatingDAO ratingDAO =
                new org.auctionsystem.server.DAO.RatingDAO();

        try {
            double rating = request.get("rating").getAsDouble();
            String sellerUsername = request.get("seller_username").getAsString();

            String sessionId = request.has("session_id") ? request.get("session_id").getAsString() : null;
            UserSession session = (sessionId != null) ? SessionManager.getSession(sessionId) : null;

            if (session == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Phiên đăng nhập không hợp lệ!");
                return response;
            }

            String buyerId = session.getUserId();
            if (!"BIDDER".equalsIgnoreCase(session.getRole())) {
                response.addProperty("status", "error");
                response.addProperty("message", "Chỉ Bidder mới có thể đánh giá Seller!");
                return response;
            }

            if (rating < 1 || rating > 5) {
                response.addProperty("status", "error");
                response.addProperty("message", "Điểm đánh giá phải từ 1 đến 5!");
                return response;
            }

            User sellerProfile = userDAO.getProfileByUsername(sellerUsername);
            if (!(sellerProfile instanceof Seller)) {
                response.addProperty("status", "error");
                response.addProperty("message", "Không thể đánh giá một người không phải là seller!");
                return response;
            }

            if (!historyDAO.hasBuyerPurchasedFromSeller(buyerId, sellerProfile.getId())) {
                response.addProperty("status", "error");
                response.addProperty("message", "Bạn chưa từng mua sản phẩm từ seller này!");
                return response;
            }

            int newScore = (int) Math.round(rating);
            // Kiểm tra bidder đã đánh giá seller này chưa
            int existingScore = ratingDAO.getExistingRating(buyerId, sellerProfile.getId());

            boolean ok;
            if (existingScore == -1) {
                // Lần đầu đánh giá: ghi vào seller_ratings + tăng rating_count
                boolean inserted = ratingDAO.insertRating(buyerId, sellerProfile.getId(), newScore);
                ok = inserted && userDAO.updateRatingInsert(sellerProfile.getId(), newScore);
                if (ok) {
                    response.addProperty("isEdit", false);
                }
            } else {
                // Đã đánh giá rồi: chỉ sửa điểm, không tăng rating_count
                boolean updated = ratingDAO.updateRatingScore(buyerId, sellerProfile.getId(), newScore);
                ok = updated && userDAO.updateRatingEdit(sellerProfile.getId(), existingScore, newScore);
                if (ok) {
                    response.addProperty("isEdit", true);
                }
            }

            if (ok) {
                response.addProperty("status", "success");
                response.addProperty("message",
                        existingScore == -1 ? "Đánh giá thành công!" : "Cập nhật đánh giá thành công!");
            } else {
                response.addProperty("status", "error");
                response.addProperty("message", "Cập nhật đánh giá thất bại!");
            }
        } catch (Exception e) {
            System.err.println("[updateRating] Lỗi hệ thống: " + e.getClass().getSimpleName());
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi hệ thống!");
        }
        return response;
    }
}
