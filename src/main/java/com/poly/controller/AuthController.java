package com.poly.controller;

import com.poly.exception.BusinessException;
import com.poly.service.AuthService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService) { this.authService = authService; }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error,
                        @RequestParam(required = false) String logout, Model model) {
        if (error != null) model.addAttribute("errorMessage", "Tên đăng nhập hoặc mật khẩu chưa đúng.");
        if (logout != null) model.addAttribute("successMessage", "Bạn đã đăng xuất.");
        return "auth/login";
    }

    @GetMapping("/register")
    public String register() { return "auth/register"; }

    @PostMapping("/register")
    public String createAccount(@RequestParam String username, @RequestParam String password,
                                @RequestParam String confirmPassword, RedirectAttributes flash, Model model) {
        if (!password.equals(confirmPassword)) {
            model.addAttribute("errorMessage", "Mật khẩu nhập lại chưa khớp.");
            model.addAttribute("username", username);
            return "auth/register";
        }
        try {
            authService.register(username, password);
            flash.addFlashAttribute("successMessage", "Tạo tài khoản thành công. Hãy đăng nhập để tiếp tục.");
            return "redirect:/login";
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("username", username);
            return "auth/register";
        }
    }
}
