package com.poly.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.poly.exception.BusinessException;
import com.poly.service.BusinessService;

class BusinessServiceTest {
    private JdbcTemplate jdbc;
    private BusinessService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new BusinessService(jdbc);
    }

    @Test
    void onlineOrderRequiresAtLeastOneProduct() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createOnlineOrder(Map.of(), "Khách", "0900000000", "Hà Nội"));

        assertEquals("Vui lòng chọn ít nhất một sản phẩm.", error.getMessage());
    }

    @Test
    void orderStatusRejectsUnknownValueBeforeDatabaseAccess() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateOrderStatus(10, "READY", 2, true));

        assertEquals("Trạng thái đơn hàng không hợp lệ.", error.getMessage());
    }

    @Test
    void assigningOrderFailsWhenOrderDoesNotExist() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.assignOnlineOrder(999, 2));

        assertEquals("Không tìm thấy đơn hàng.", error.getMessage());
    }

    @Test
    void returnRequiresPositiveQuantityAndReason() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createSalesReturn(1, 1, 0, "Lý do", 2));

        assertEquals("Vui lòng nhập số lượng và lý do trả hàng.", error.getMessage());
    }
}
