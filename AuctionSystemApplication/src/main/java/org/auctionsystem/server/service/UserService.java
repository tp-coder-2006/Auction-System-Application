package org.auctionsystem.server.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.commons.validator.routines.EmailValidator;
import org.auctionsystem.server.session.SessionManager;
import org.auctionsystem.server.session.UserSession;
import org.auctionsystem.model.entities.User;
import org.auctionsystem.server.Connectivity.DatabaseConnection;
import org.auctionsystem.server.DAO.UserDAO;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UserService {
    private static boolean isSpecialChar(char c) {
        String specialChars = "@#$%^&+=!";
        return specialChars.contains(String.valueOf(c));
    }

    public boolean isEmailValid(String email){
        EmailValidator validator = EmailValidator.getInstance();
        return validator.isValid(email);
    }

    public static String hashPassword(String plainPassword) {
        String salt = BCrypt.gensalt(10);
        return BCrypt.hashpw(plainPassword, salt);
    }

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

    public JsonObject registerUser(JsonObject request) {
        JsonObject response = new JsonObject();
        UserDAO userDAO = new UserDAO();

        try {
            String username = request.get("username").getAsString();
            String password = request.get("password").getAsString();
            String name = request.get("name").getAsString();
            String email = request.has("email") ? request.get("email").getAsString() : "";
            // [SỬA] Phải kiểm tra cả isJsonNull vì client có thể gửi "phone": null
            // request.has("phone") chỉ kiểm tra field có tồn tại không — không kiểm tra giá trị
            // Nếu chỉ dùng has() mà không kiểm tra null → getAsString() trên JsonNull → exception
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

            // Kiểm tra trùng lặp trước khi gửi vào DAO — để trả message cụ thể cho client
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
            // [MỚI] Kiểm tra số điện thoại trùng — chỉ khi có nhập
            if (phone != null && !phone.isBlank() && userDAO.isPhoneExist(phone)) {
                response.addProperty("status", "error");
                response.addProperty("message", "Số điện thoại đã được đăng ký!");
                return response;
            }

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
        } catch (Exception e) {
            System.err.println("Lỗi đăng ký: " + e.getMessage());
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi server: " + e.getMessage());
        }

        return response;
    }

    public JsonObject loginUser(JsonObject request) {
        JsonObject response = new JsonObject();
        UserDAO userDAO = new UserDAO();

        try {
            String username = request.get("username").getAsString();
            String password = request.get("password").getAsString();
            String expectedRole = request.has("role") ? request.get("role").getAsString() : "";

            User loggedInUser = userDAO.loginUser(username, password);

            if (loggedInUser != null) {
                String actualRole = loggedInUser.getRole().toString();

                if (!expectedRole.isEmpty() && !actualRole.equalsIgnoreCase(expectedRole)) {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Tài khoản của bạn không có quyền đăng nhập với vai trò này!");
                    return response;
                }

                String sessionId= UUID.randomUUID().toString();

                // Lấy phone và rating — casting sang Seller nếu role là SELLER
                String phone = loggedInUser.getPhone();
                Double rating = null;
                if (loggedInUser instanceof org.auctionsystem.model.entities.Seller) {
                    rating = ((org.auctionsystem.model.entities.Seller) loggedInUser).getRating();
                }

                response.addProperty("status", "success");
                response.addProperty("message", "Đăng nhập thành công!");
                response.addProperty("session_id", sessionId);
                response.addProperty("user_id", loggedInUser.getId());
                response.addProperty("name", loggedInUser.getName());
                response.addProperty("username", loggedInUser.getUsername());
                response.addProperty("email", loggedInUser.getEmail());
                response.addProperty("role", loggedInUser.getRole().name());
                response.addProperty("balance", loggedInUser.getBalance());
                // [MỚI] Thêm phone và rating vào response để client lưu vào UserSession
                if (phone != null) response.addProperty("phone", phone);
                else response.addProperty("phone", (String) null);
                if (rating != null) response.addProperty("rating", rating);
                else response.addProperty("rating", (String) null);

                UserSession userSession = new UserSession(
                        sessionId,
                        loggedInUser.getId(),
                        loggedInUser.getName(),    // [MỚI]
                        loggedInUser.getUsername(),
                        loggedInUser.getEmail(),   // [MỚI]
                        loggedInUser.getRole().name(),
                        loggedInUser.getBalance(),
                        phone,
                        rating
                );

                SessionManager.addSession(userSession);

            } else {
                response.addProperty("status", "error");
                response.addProperty("message", "Sai tên đăng nhập hoặc mật khẩu!");
            }

        } catch (Exception e) {
            System.err.println("Lỗi đăng nhập: " + e.getMessage());
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi máy chủ: " + e.getMessage());
        }

        return response;
    }

    public JsonObject getProfile(JsonObject request) {
        JsonObject response = new JsonObject();
        UserDAO userDAO = new UserDAO();
        Gson gson = new Gson();

        try {
            String id = request.get("user_id").getAsString();
            User user = userDAO.getUserById(id);
            if (user == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Không tìm thấy thông tin người dùng!");
            } else {
                if (request.has("session_id") && !request.get("session_id").isJsonNull()) {
                    UserSession session = SessionManager.getSession(
                            request.get("session_id").getAsString());
                    if (session != null) {
                        // Chỉ đồng bộ những field có thể thay đổi trong phiên
                        // name và email không thay đổi cho đến khi có updateProfile()
                        session.setBalance(user.getBalance());
                        session.setPhone(user.getPhone());
                        if (user instanceof org.auctionsystem.model.entities.Seller) {
                            session.setRating(((org.auctionsystem.model.entities.Seller) user).getRating());
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

    public JsonObject deposit(JsonObject request) {
        JsonObject response = new JsonObject();
        UserDAO userDAO = new UserDAO();

        try {
            String userId = request.get("user_id").getAsString();
            double amount = request.get("amount").getAsDouble();

            if (amount <= 0) {
                response.addProperty("status", "error");
                response.addProperty("message", "Số tiền không hợp lệ!");
                return response;
            }

            // Dùng connection riêng — 1 câu SQL không cần transaction
            try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
                if (userDAO.updateBalance(userId, amount, conn)) {
                    response.addProperty("status", "success");
                    response.addProperty("message", "Nạp tiền thành công!");
                } else {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Nạp tiền thất bại!");
                }
            }
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return response;
    }

    public JsonObject withdraw(JsonObject request) {
        JsonObject response = new JsonObject();
        UserDAO userDAO = new UserDAO();

        try {
            String userId = request.get("user_id").getAsString();
            double amount = request.get("amount").getAsDouble();

            if (amount <= 0) {
                response.addProperty("status", "error");
                response.addProperty("message", "Số tiền không hợp lệ!");
                return response;
            }

            try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
                if (userDAO.updateBalance(userId, -amount, conn)) {
                    response.addProperty("status", "success");
                    response.addProperty("message", "Rút tiền thành công!");
                } else {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Số dư không đủ!");
                }
            }
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", "Lỗi hệ thống: " + e.getMessage());
        }
        return response;
    }

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

            User user = userDAO.getUserById(userId);
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

    //Thiếu logic cập nhật profile và đánh giá seller
}