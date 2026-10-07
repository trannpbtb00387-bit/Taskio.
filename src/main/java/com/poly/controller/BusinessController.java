package com.poly.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.security.Principal;

import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.poly.service.BusinessService;
import com.poly.service.AuthService;

@Controller
public class BusinessController {
    private final BusinessService business;
    private final AuthService authService;

    public BusinessController(BusinessService business, AuthService authService) {
        this.business = business;
        this.authService = authService;
    }

    @GetMapping("/orders")
    public String orders(Model model, Principal principal) {
        model.addAttribute("view", "orders");
        model.addAttribute("title", "Danh sách đơn hàng");
        model.addAttribute("description", "Theo dõi hóa đơn, trạng thái xử lý và doanh thu từng đơn.");
        addOrderData(model, principal, business.orders(currentUserId(principal), isAdmin(principal)));
        return "business/module";
    }

    @GetMapping("/orders/uncompleted")
    public String unfinishedOrders(Model model, Principal principal) {
        model.addAttribute("view", "uncompleted");
        model.addAttribute("title", "Đơn chưa hoàn tất");
        model.addAttribute("description", "Các đơn đang chờ xác nhận hoặc đang xử lý.");
        addOrderData(model, principal, business.unfinishedOrders(currentUserId(principal), isAdmin(principal)));
        return "business/module";
    }

    @GetMapping("/orders/returns")
    public String salesReturns(Model model) {
        model.addAttribute("view", "sales-returns");
        model.addAttribute("title", "Trả hàng khách mua");
        model.addAttribute("description", "Ghi nhận hàng khách trả; số lượng được cộng lại kho sau khi lưu.");
        model.addAttribute("returns", business.salesReturns());
        model.addAttribute("products", business.productsForInventory());
        return "business/module";
    }

    @PostMapping("/orders/returns")
    public String createSalesReturn(@RequestParam int orderId, @RequestParam int productId,
                                    @RequestParam int quantity, @RequestParam String reason,
                                    HttpSession session, RedirectAttributes flash) {
        return runAndRedirect(() -> business.createSalesReturn(orderId, productId, quantity, reason, userId(session)),
                "/orders/returns", flash, "Đã tạo phiếu trả hàng và cập nhật tồn kho.");
    }

    @GetMapping("/orders/process/{step}")
    public String orderProcessing(@PathVariable String step, Model model, Principal principal) {
        Map<String, String> statuses = Map.of(
                "waiting-confirmation", "PENDING",
                "processing", "PROCESSING");
        String status = statuses.get(step);
        if (status == null) return "redirect:/orders";
        model.addAttribute("view", "orders");
        model.addAttribute("title", switch (step) {
            case "waiting-confirmation" -> "Chờ xác nhận";
            default -> "Đang xử lý";
        });
        model.addAttribute("description", "Cập nhật trạng thái để cả cửa hàng nắm được tiến độ đơn.");
        addOrderData(model, principal, business.ordersByStatus(status, currentUserId(principal), isAdmin(principal)));
        return "business/module";
    }

    @PostMapping("/orders/{id}/status")
    public String updateOrderStatus(@PathVariable int id, @RequestParam String status,
                                    Principal principal, RedirectAttributes flash) {
        return runAndRedirect(() -> business.updateOrderStatus(id, status,
                        currentUserId(principal), isAdmin(principal)),
                "/orders", flash, "Đã cập nhật trạng thái đơn hàng.");
    }

    @PostMapping("/orders/{id}/assign")
    public String assignOnlineOrder(@PathVariable int id, @RequestParam int employeeId,
                                    RedirectAttributes flash) {
        return runAndRedirect(() -> business.assignOnlineOrder(id, employeeId),
                "/orders", flash, "Đã phân công đơn hàng cho nhân viên.");
    }

    @GetMapping("/inventory")
    public String inventory(Model model) {
        model.addAttribute("view", "inventory");
        model.addAttribute("title", "Tồn kho");
        model.addAttribute("description", "Tồn hiện tại được cập nhật theo bán hàng, nhập hàng và trả hàng.");
        model.addAttribute("products", business.productsForInventory());
        model.addAttribute("lowStock", business.lowStockProducts());
        return "business/module";
    }

    @GetMapping("/inventory/receipts")
    public String receipts(Model model) {
        model.addAttribute("view", "receipts");
        model.addAttribute("title", "Nhập hàng");
        model.addAttribute("description", "Tạo phiếu nhập và cộng số lượng vào kho ngay khi lưu.");
        model.addAttribute("products", business.productsForInventory());
        model.addAttribute("transactions", business.inventoryTransactions("RECEIPT"));
        return "business/module";
    }

    @PostMapping("/inventory/receipts")
    public String receiveStock(@RequestParam int productId, @RequestParam int quantity,
                               @RequestParam BigDecimal unitCost, @RequestParam(required = false) String note,
                               HttpSession session, RedirectAttributes flash) {
        return runAndRedirect(() -> business.receiveStock(productId, quantity, unitCost, note, userId(session)),
                "/inventory/receipts", flash, "Đã nhập hàng và cập nhật tồn kho.");
    }

    @GetMapping("/inventory/returns")
    public String supplierReturns(Model model) {
        model.addAttribute("view", "supplier-returns");
        model.addAttribute("title", "Trả hàng nhà cung cấp");
        model.addAttribute("description", "Ghi nhận hàng trả nhà cung cấp; hệ thống kiểm tra tồn trước khi trừ kho.");
        model.addAttribute("products", business.productsForInventory());
        model.addAttribute("transactions", business.inventoryTransactions("SUPPLIER_RETURN"));
        return "business/module";
    }

    @PostMapping("/inventory/returns")
    public String returnToSupplier(@RequestParam int productId, @RequestParam int quantity,
                                   @RequestParam(required = false) String note, HttpSession session,
                                   RedirectAttributes flash) {
        return runAndRedirect(() -> business.returnStockToSupplier(productId, quantity, note, userId(session)),
                "/inventory/returns", flash, "Đã lưu phiếu trả nhà cung cấp và cập nhật tồn kho.");
    }

    @GetMapping("/customers")
    public String customers(Model model) {
        model.addAttribute("view", "customers");
        model.addAttribute("title", "Khách hàng");
        model.addAttribute("description", "Lưu thông tin liên hệ để phục vụ chăm sóc và tra cứu lịch sử mua.");
        model.addAttribute("customers", business.customers(false));
        return "business/module";
    }

    @PostMapping("/customers")
    public String createCustomer(@RequestParam String fullName, @RequestParam(required = false) String phone,
                                 @RequestParam(required = false) String email, @RequestParam(required = false) String address,
                                 RedirectAttributes flash) {
        return runAndRedirect(() -> business.createCustomer(fullName, phone, email, address),
                "/customers", flash, "Đã thêm khách hàng.");
    }

    @GetMapping("/customers/loyalty")
    public String loyalCustomers(Model model) {
        model.addAttribute("view", "loyal-customers");
        model.addAttribute("title", "Khách hàng thân thiết");
        model.addAttribute("description", "Khách có tổng chi tiêu từ 1.000.000 ₫ trở lên được đưa vào danh sách này.");
        model.addAttribute("customers", business.customers(true));
        return "business/module";
    }

    @GetMapping("/promotions")
    public String promotions(Model model) {
        model.addAttribute("view", "promotions");
        model.addAttribute("title", "Khuyến mãi");
        model.addAttribute("description", "Tạo mã giảm theo phần trăm hoặc số tiền; có thời gian và đơn tối thiểu.");
        model.addAttribute("promotions", business.promotions());
        return "business/module";
    }

    @PostMapping("/promotions")
    public String createPromotion(@RequestParam String code, @RequestParam String name,
                                  @RequestParam String discountType, @RequestParam BigDecimal discountValue,
                                  @RequestParam(required = false) BigDecimal minimumOrderAmount,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startsAt,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endsAt,
                                  RedirectAttributes flash) {
        return runAndRedirect(() -> business.createPromotion(code, name, discountType, discountValue,
                minimumOrderAmount, startsAt, endsAt), "/promotions", flash, "Đã tạo chương trình khuyến mãi.");
    }

    @GetMapping("/reports")
    public String reports(Model model) {
        model.addAttribute("view", "reports");
        model.addAttribute("title", "Báo cáo cửa hàng");
        model.addAttribute("description", "Tổng hợp doanh thu đơn hoàn tất, hoạt động bán và tình hình tồn kho.");
        model.addAttribute("summary", business.reportSummary());
        model.addAttribute("lowStock", business.lowStockProducts());
        return "business/module";
    }

    private String runAndRedirect(Runnable action, String path, RedirectAttributes flash, String success) {
        try {
            action.run();
            flash.addFlashAttribute("successMessage", success);
        } catch (RuntimeException ex) {
            flash.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:" + path;
    }

    private Integer userId(HttpSession session) {
        Object id = session.getAttribute("userId");
        return id instanceof Number number ? number.intValue() : null;
    }

    private Integer currentUserId(Principal principal) {
        return principal == null ? null : authService.userId(principal.getName());
    }

    private boolean isAdmin(Principal principal) {
        return principal != null && "ADMIN".equalsIgnoreCase(authService.roleFor(principal.getName()));
    }

    private void addOrderData(Model model, Principal principal, java.util.List<Map<String, Object>> orders) {
        model.addAttribute("orders", orders);
        model.addAttribute("currentUserId", currentUserId(principal));
        model.addAttribute("staffOptions", isAdmin(principal) ? authService.staffOptions() : java.util.List.of());
    }
}
