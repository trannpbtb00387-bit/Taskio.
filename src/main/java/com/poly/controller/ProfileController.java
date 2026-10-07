package com.poly.controller;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.poly.service.AuthService;
import com.poly.service.AvatarStorageService;

@Controller
public class ProfileController {
    private final AuthService authService;
    private final AvatarStorageService avatarStorage;

    public ProfileController(AuthService authService, AvatarStorageService avatarStorage) {
        this.authService = authService;
        this.avatarStorage = avatarStorage;
    }

    @GetMapping("/profile")
    public String profile(Principal principal, Model model) {
        model.addAttribute("profile", authService.profileFor(principal.getName()));
        return "profile";
    }

    @PostMapping("/profile")
    public String updateProfile(Principal principal,
                                @RequestParam String displayName,
                                @RequestParam(required = false, defaultValue = "") String newPassword,
                                @RequestParam(required = false, defaultValue = "") String confirmPassword,
                                @RequestParam(required = false) MultipartFile avatar,
                                RedirectAttributes flash) {
        if (!newPassword.equals(confirmPassword)) {
            flash.addFlashAttribute("errorMessage", "Mật khẩu nhập lại chưa khớp.");
            return "redirect:/profile";
        }
        try {
            String avatarPath = avatarStorage.store(avatar);
            Object previousAvatar = authService.profileFor(principal.getName()).get("avatar_path");
            authService.updateProfile(principal.getName(), displayName, newPassword, avatarPath);
            if (avatarPath != null && previousAvatar != null) avatarStorage.delete(previousAvatar.toString());
            flash.addFlashAttribute("successMessage", "Đã cập nhật hồ sơ cá nhân.");
        } catch (RuntimeException ex) {
            flash.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/profile";
    }
}
