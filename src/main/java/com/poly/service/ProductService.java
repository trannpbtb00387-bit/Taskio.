package com.poly.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.poly.exception.BusinessException;

@Service
public class ProductService {
    private final JdbcTemplate jdbc;
    public ProductService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String,Object>> search(String keyword) {
        if (!StringUtils.hasText(keyword)) return jdbc.queryForList("SELECT id,sku,name,price,stock FROM dbo.products ORDER BY id DESC");
        String term = "%" + keyword.trim() + "%";
        return jdbc.queryForList("SELECT id,sku,name,price,stock FROM dbo.products WHERE sku LIKE ? OR name LIKE ? ORDER BY id DESC", term, term);
    }

    public Map<String,Object> find(int id) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT id,sku,name,price,stock FROM dbo.products WHERE id=?", id);
        if (rows.isEmpty()) throw new BusinessException("Không tìm thấy sản phẩm.");
        return rows.get(0);
    }

    @Transactional
    public void save(Integer id, String sku, String name, BigDecimal price, int stock) {
        if (!StringUtils.hasText(sku) || !StringUtils.hasText(name) || price == null || price.signum() < 0 || stock < 0)
            throw new BusinessException("Vui lòng nhập mã, tên, giá không âm và tồn kho hợp lệ.");
        Integer duplicate = id == null
                ? jdbc.queryForObject("SELECT COUNT(*) FROM dbo.products WHERE sku=?", Integer.class, sku.trim())
                : jdbc.queryForObject("SELECT COUNT(*) FROM dbo.products WHERE sku=? AND id<>?", Integer.class, sku.trim(), id);
        if (duplicate != null && duplicate > 0) throw new BusinessException("Mã SKU đã tồn tại.");
        if (id == null) jdbc.update("INSERT INTO dbo.products(sku,name,price,stock) VALUES(?,?,?,?)", sku.trim(), name.trim(), price, stock);
        else {
            int changed = jdbc.update("UPDATE dbo.products SET sku=?,name=?,price=?,stock=? WHERE id=?", sku.trim(), name.trim(), price, stock, id);
            if (changed == 0) throw new BusinessException("Không tìm thấy sản phẩm cần sửa.");
        }
    }

    @Transactional
    public void delete(int id) {
        try {
            int changed = jdbc.update("DELETE FROM dbo.products WHERE id=?", id);
            if (changed == 0) throw new BusinessException("Không tìm thấy sản phẩm.");
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("Không thể xóa sản phẩm đã xuất hiện trong đơn hàng hoặc lịch sử kho.");
        }
    }
}
