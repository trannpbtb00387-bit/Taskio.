package com.poly.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.poly.service.BusinessService;

@Controller
public class OnlineShopController {

    private static final String QUANTITY_PREFIX = "quantity_";

    private final BusinessService business;

    public OnlineShopController(BusinessService business) {
        this.business = business;
    }

    // Mở trang đặt hàng và tải danh sách sản phẩm còn hàng
    @GetMapping("/shop")
    public String shop(Model model) {
        model.addAttribute("products", business.onlineShopProducts());
        return "shop/index";
    }

    // Nhận giỏ hàng và thông tin giao hàng
    @PostMapping("/shop/order")
    public String createOrder(
            @RequestParam Map<String, String> form,
            RedirectAttributes flash) {

        try {
            Map<Integer, Integer> cart = new LinkedHashMap<>();

            for (Map.Entry<String, String> entry : form.entrySet()) {
                if (entry.getKey().startsWith(QUANTITY_PREFIX)
                        && entry.getValue() != null
                        && !entry.getValue().isBlank()) {

                    int quantity = Integer.parseInt(entry.getValue());

                    if (quantity > 0) {
                        String productIdText = entry.getKey()
                                .substring(QUANTITY_PREFIX.length());

                        int productId = Integer.parseInt(productIdText);
                        cart.put(productId, quantity);
                    }
                }
            }

            int orderId = business.createOnlineOrder(
                    cart,
                    form.get("customerName"),
                    form.get("phone"),
                    form.get("shippingAddress")
            );

            flash.addFlashAttribute(
                    "successMessage",
                    "Đặt hàng thành công. Mã đơn của bạn là #" + orderId
                            + ". Nhân viên sẽ xác nhận đơn sớm."
            );

        } catch (NumberFormatException ex) {
            flash.addFlashAttribute(
                    "errorMessage",
                    "Số lượng sản phẩm không hợp lệ."
            );
        } catch (RuntimeException ex) {
            flash.addFlashAttribute(
                    "errorMessage",
                    ex.getMessage()
            );
        }

        return "redirect:/shop";
    }
} 