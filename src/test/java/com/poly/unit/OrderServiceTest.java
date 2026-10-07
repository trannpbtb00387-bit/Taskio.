package com.poly.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.poly.service.BusinessService;
import com.poly.service.OrderService;

class OrderServiceTest {

    @Test
    void checkoutDelegatesAllArgumentsAndReturnsCreatedOrderId() {
        BusinessService business = mock(BusinessService.class);
        OrderService service = new OrderService(business);
        Map<Integer, Integer> cart = Map.of(4, 2);
        when(business.checkout(cart, 7, "TASKIO10", 3)).thenReturn(125);

        int orderId = service.checkout(cart, 7, "TASKIO10", 3);

        assertEquals(125, orderId);
        verify(business).checkout(cart, 7, "TASKIO10", 3);
    }
}
