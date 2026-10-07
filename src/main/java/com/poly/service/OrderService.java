package com.poly.service;

import java.util.Map;

import org.springframework.stereotype.Service;

/** Checkout-facing service kept separate from page/controller code. */
@Service
public class OrderService {
    private final BusinessService business;
    public OrderService(BusinessService business) { this.business = business; }

    public int checkout(Map<Integer, Integer> cart, Integer userId, String promotionCode, Integer customerId) {
        return business.checkout(cart, userId, promotionCode, customerId);
    }
}
