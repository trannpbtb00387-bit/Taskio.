package com.poly.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.poly.exception.BusinessException;
import com.poly.service.AuthService;

class AuthServiceTest {
    private JdbcTemplate jdbc;
    private AuthService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new AuthService(jdbc);
    }

    @Test
    @Tag("smoke")
    void registerRejectsUsernameShorterThanThreeCharacters() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.register("ab", "123456"));

        assertEquals("Tên đăng nhập cần tối thiểu 3 ký tự.", error.getMessage());
        verify(jdbc, never()).update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }

    @Test
    void registerRejectsPasswordShorterThanSixCharacters() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.register("staff01", "123"));

        assertEquals("Mật khẩu cần tối thiểu 6 ký tự.", error.getMessage());
        verify(jdbc, never()).update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }

    @Test
    void registerRejectsAnAlreadyUsedUsername() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("staff01"))).thenReturn(1);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.register("staff01", "123456"));

        assertEquals("Tên đăng nhập này đã được sử dụng.", error.getMessage());
        verify(jdbc, never()).update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }

    @Test
    void employeeCommissionRateMustBeWithinZeroAndOneHundredPercent() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateEmployee(2, "Nhân viên", "", new BigDecimal("101")));

        assertEquals("Hoa hồng phải nằm trong khoảng từ 0% đến 100%.", error.getMessage());
        verify(jdbc, never()).update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }
}
