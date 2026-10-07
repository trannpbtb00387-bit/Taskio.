package com.poly.controller;

import java.math.BigDecimal;

import com.poly.exception.BusinessException;
import com.poly.service.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProductController {
    private final ProductService products;
    public ProductController(ProductService products) { this.products = products; }

    @GetMapping("/products")
    public String list(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("items", products.search(q));
        model.addAttribute("q", q == null ? "" : q);
        return "products/list";
    }

    @GetMapping("/products/new")
    public String createForm(Model model) {
        java.util.Map<String, Object> product = new java.util.HashMap<>();
        product.put("id", null); 
        product.put("sku", "");
        product.put("name", "");
        product.put("price", 0);
        product.put("stock", 0);

        model.addAttribute("product", product);
        model.addAttribute("formTitle", "Thêm sản phẩm");

        return "products/form";
    }

    @GetMapping("/products/{id}/edit")
    public String editForm(@PathVariable int id, Model model) {
        model.addAttribute("product", products.find(id));
        model.addAttribute("formTitle", "Chỉnh sửa sản phẩm");
        return "products/form";
    }

    @PostMapping("/products/save")
    public String save(@RequestParam(required = false) Integer id, @RequestParam String sku, @RequestParam String name,
                       @RequestParam BigDecimal price, @RequestParam int stock, RedirectAttributes flash) {
        try {
            products.save(id, sku, name, price, stock);
            flash.addFlashAttribute("successMessage", id == null ? "Đã thêm sản phẩm." : "Đã cập nhật sản phẩm.");
            return "redirect:/products";
        } catch (BusinessException ex) {
            flash.addFlashAttribute("errorMessage", ex.getMessage());
            return id == null ? "redirect:/products/new" : "redirect:/products/" + id + "/edit";
        }
    }

    @PostMapping("/products/{id}/delete")
    public String delete(@PathVariable int id, RedirectAttributes flash) {
        try { products.delete(id); flash.addFlashAttribute("successMessage", "Đã xóa sản phẩm."); }
        catch (BusinessException ex) { flash.addFlashAttribute("errorMessage", ex.getMessage()); }
        return "redirect:/products";
    }
}
