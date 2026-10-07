package com.poly.config;

import java.security.Principal;
import java.util.Map;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.poly.service.AuthService;

@ControllerAdvice
public class CurrentUserAdvice {

    private final AuthService authService;

    public CurrentUserAdvice(AuthService authService) {
        this.authService = authService;
    }

    @ModelAttribute("currentUser")
    public Map<String, String> currentUser(Principal principal) {
        if (principal == null) {
            return Map.of(
                "name", "Khách",
                "username", "",
                "role", "Khách",
                "initial", "K",
                "avatar", ""
            );
        }

        String username = principal.getName();
        String displayName = username;
        String role = "STAFF";
        String avatar = "";

        try {
            Map<String, Object> profile = authService.profileFor(username);

            Object nameValue = profile.get("display_name");
            if (nameValue != null && !nameValue.toString().isBlank()) {
                displayName = nameValue.toString();
            }

            Object roleValue = profile.get("role");
            if (roleValue != null) {
                role = roleValue.toString();
            }

            Object avatarValue = profile.get("avatar_path");
            if (avatarValue != null) {
                avatar = avatarValue.toString();
            }
        } catch (RuntimeException ex) {
            // Nếu chưa lấy được hồ sơ, vẫn hiển thị tên đăng nhập và avatar mặc định.
        }

        String displayRole = "ADMIN".equalsIgnoreCase(role)
                ? "Quản trị viên"
                : "Nhân viên";

        return Map.of(
            "name", displayName,
            "username", username,
            "role", displayRole,
            "initial", displayName.substring(0, 1).toUpperCase(),
            "avatar", avatar
        );
    }
}