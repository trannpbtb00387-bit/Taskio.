package com.poly.controller;

import com.poly.service.BusinessService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {
    private final BusinessService business;
    public DashboardController(BusinessService business) { this.business = business; }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("summary", business.reportSummary());
        model.addAttribute("lowStock", business.lowStockProducts());
        model.addAttribute("view", "dashboard");
        return "dashboard";
    }
}
