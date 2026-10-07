package com.poly.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.poly.service.AuthService;

@Controller
public class EmployeeController {
    private final AuthService authService;

    public EmployeeController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/employees")
    public String employees(Model model) {
        List<Map<String, Object>> employees = authService.employeesWithSales();

        long completedOrders = employees.stream()
                .mapToLong(employee ->
                        ((Number) employee.get("completed_orders")).longValue())
                .sum();

        BigDecimal revenue = employees.stream()
                .map(employee -> toBigDecimal(employee.get("revenue")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCommission = employees.stream()
                .map(employee -> toBigDecimal(employee.get("commission_amount")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("employees", employees);
        model.addAttribute("totalCompletedOrders", completedOrders);
        model.addAttribute("totalRevenue", revenue);
        model.addAttribute("totalCommission", totalCommission);

        return "employees/list";
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }

        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }

        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }

        return new BigDecimal(value.toString());
    }
    @GetMapping("/employees/{id}/edit")
    public String edit(@PathVariable long id, Model model) {
        model.addAttribute("employee", authService.employeeForEdit(id));
        return "employees/form";
    }

    @PostMapping("/employees/{id}/edit")
    public String update(@PathVariable long id,
                         @RequestParam String displayName,
                         @RequestParam(required = false, defaultValue = "") String newPassword,
                         RedirectAttributes flash) {
        try {
            authService.updateEmployee(id, displayName, newPassword, null);
            flash.addFlashAttribute("successMessage", "Đã cập nhật thông tin nhân viên.");
        } catch (RuntimeException ex) {
            flash.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/employees/" + id + "/edit";
        }
        return "redirect:/employees";
    }
}
