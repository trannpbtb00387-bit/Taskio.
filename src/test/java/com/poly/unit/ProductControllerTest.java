package com.poly.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.poly.controller.ProductController;
import com.poly.exception.BusinessException;
import com.poly.service.ProductService;

class ProductControllerTest {
    private ProductService products;
    private ProductController controller;

    @BeforeEach
    void setUp() {
        products = mock(ProductService.class);
        controller = new ProductController(products);
    }

    @Test
    @Tag("smoke")
    void createFormRendersProductFormWithEmptyProduct() {
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.createForm(model);

        assertEquals("products/form", view);
        assertEquals("Thêm sản phẩm", model.get("formTitle"));
        assertTrue(((Map<?, ?>) model.get("product")).containsKey("sku"));
    }

    @Test
    void saveRedirectsToProductListWhenServiceSucceeds() {
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();

        String view = controller.save(null, "SKU-01", "Áo", new BigDecimal("100000"), 5, flash);

        assertEquals("redirect:/products", view);
        assertEquals("Đã thêm sản phẩm.", flash.getFlashAttributes().get("successMessage"));
        verify(products).save(null, "SKU-01", "Áo", new BigDecimal("100000"), 5);
    }

    @Test
    void saveReturnsToCreateFormWhenBusinessValidationFails() {
        doThrow(new BusinessException("SKU đã tồn tại."))
                .when(products).save(eq(null), eq("SKU-01"), eq("Áo"), any(BigDecimal.class), eq(5));
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();

        String view = controller.save(null, "SKU-01", "Áo", new BigDecimal("100000"), 5, flash);

        assertEquals("redirect:/products/new", view);
        assertEquals("SKU đã tồn tại.", flash.getFlashAttributes().get("errorMessage"));
    }
}
