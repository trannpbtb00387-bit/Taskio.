package com.poly.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.poly.service.BusinessService;
import com.poly.service.OrderService;

@Controller
public class PosController {
    private static final String CART_KEY = "taskioCart";
    private final BusinessService business;
    private final OrderService orderService;

    public PosController(BusinessService business, OrderService orderService) {
        this.business = business;
        this.orderService = orderService;
    }

    @GetMapping("/pos")
    public String pos(@RequestParam(required = false) String q, HttpSession session, Model model) {
        Map<Integer, Integer> cart = cart(session);
        var products = business.productsForInventory();
        if (q != null && !q.isBlank()) {
            String term = q.toLowerCase();
            products = products.stream().filter(p -> String.valueOf(p.get("sku")).toLowerCase().contains(term)
                    || String.valueOf(p.get("name")).toLowerCase().contains(term)).toList();
        }
        model.addAttribute("products", products);
        model.addAttribute("customers", business.customerOptions());
        var cartDetails = business.cartDetails(cart);
        model.addAttribute("cart", cartDetails);
        model.addAttribute("cartTotal", cartDetails.stream().map(row -> (java.math.BigDecimal) row.get("line_total"))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        model.addAttribute("cartEmpty", cart.isEmpty());
        model.addAttribute("view", "pos");
        return "pos/index";
    }

    @PostMapping("/pos/cart/add")
    public String add(@RequestParam int productId, @RequestParam int quantity, HttpSession session,
                      RedirectAttributes flash) {
        if (quantity <= 0) {
            flash.addFlashAttribute("errorMessage", "Số lượng phải lớn hơn 0.");
            return "redirect:/pos";
        }
        try {
            Map<String, Object> product = business.productForSale(productId);
            Map<Integer, Integer> cart = cart(session);
            int next = cart.getOrDefault(productId, 0) + quantity;
            if (((Number) product.get("stock")).intValue() < next) {
                flash.addFlashAttribute("errorMessage", "Số lượng vượt quá tồn kho hiện tại.");
            } else {
                cart.put(productId, next);
                session.setAttribute(CART_KEY, cart);
                flash.addFlashAttribute("successMessage", "Đã thêm sản phẩm vào giỏ.");
            }
        } catch (RuntimeException ex) { flash.addFlashAttribute("errorMessage", ex.getMessage()); }
        return "redirect:/pos";
    }

    @PostMapping("/pos/cart/remove")
    public String remove(@RequestParam int productId, HttpSession session) {
        cart(session).remove(productId);
        return "redirect:/pos";
    }

    @PostMapping("/pos/checkout")
    public String checkout(
            @RequestParam(required = false) String promotionCode,
            @RequestParam(required = false) Integer customerId,
            @RequestParam(required = false) String newCustomerName,
            @RequestParam(required = false) String newCustomerPhone,
            HttpSession session,
            RedirectAttributes flash) {

        try {
            Map<Integer, Integer> cart = cart(session);
            Object user = session.getAttribute("userId");
            Integer userId = user instanceof Number number
                    ? number.intValue()
                    : null;

            Integer selectedCustomerId = customerId;

            if (newCustomerName != null && !newCustomerName.isBlank()) {
                selectedCustomerId =
                        business.createCustomerForCheckout(
                                newCustomerName,
                                newCustomerPhone
                        );
            } else if (newCustomerPhone != null
                    && !newCustomerPhone.isBlank()) {
                throw new IllegalArgumentException(
                        "Nhập tên khách hàng mới hoặc xóa số điện thoại."
                );
            }

            int orderId = orderService.checkout(
                    cart,
                    userId,
                    promotionCode,
                    selectedCustomerId
            );

            session.removeAttribute(CART_KEY);
            flash.addFlashAttribute(
                    "successMessage",
                    "Thanh toán thành công. Mã đơn hàng: #" + orderId
            );
        } catch (RuntimeException ex) {
            flash.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/pos";
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Integer> cart(HttpSession session) {
        Object value = session.getAttribute(CART_KEY);
        if (value instanceof Map<?, ?> map) return (Map<Integer, Integer>) map;
        Map<Integer, Integer> fresh = new LinkedHashMap<>();
        session.setAttribute(CART_KEY, fresh);
        return fresh;
    }
}
