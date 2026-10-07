package com.poly.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;


import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.poly.exception.BusinessException;

/** Business operations behind orders, inventory, customers, promotions and reports. */
@Service
public class BusinessService {
    private final JdbcTemplate jdbc;

    public BusinessService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> orders(Integer userId, boolean admin) {
        return jdbc.queryForList("SELECT TOP 200 o.id, o.created_date, o.total_amount, o.status, "
                + "o.order_channel, o.shipping_address, o.handled_by_user_id, "
                + "u.username, c.full_name AS customer_name FROM dbo.orders o "
                + "LEFT JOIN dbo.users u ON u.id=COALESCE(o.handled_by_user_id, CASE WHEN o.order_channel='POS' THEN o.user_id END) "
                + "LEFT JOIN dbo.customers c ON c.id=o.customer_id "
                + (admin ? "" : "WHERE o.handled_by_user_id=? OR (o.order_channel='POS' AND o.user_id=?) ")
                + "ORDER BY o.created_date DESC, o.id DESC", admin ? new Object[] {} : new Object[] {userId, userId});
    }

    public List<Map<String, Object>> ordersByStatus(String status, Integer userId, boolean admin) {
        return jdbc.queryForList("SELECT TOP 200 o.id, o.created_date, o.total_amount, o.status, "
                + "o.order_channel, o.shipping_address, o.handled_by_user_id, "
                + "u.username, c.full_name AS customer_name FROM dbo.orders o "
                + "LEFT JOIN dbo.users u ON u.id=COALESCE(o.handled_by_user_id, CASE WHEN o.order_channel='POS' THEN o.user_id END) "
                + "LEFT JOIN dbo.customers c ON c.id=o.customer_id "
                + "WHERE o.status=? "
                + (admin ? "" : "AND (o.handled_by_user_id=? OR (o.order_channel='POS' AND o.user_id=?)) ")
                + "ORDER BY o.created_date DESC, o.id DESC",
                admin ? new Object[] {status} : new Object[] {status, userId, userId});
    }

    public List<Map<String, Object>> unfinishedOrders(Integer userId, boolean admin) {
        return jdbc.queryForList("SELECT TOP 200 o.id, o.created_date, o.total_amount, o.status, "
                + "o.order_channel, o.shipping_address, o.handled_by_user_id, "
                + "u.username, c.full_name AS customer_name "
                + "FROM dbo.orders o "
                + "LEFT JOIN dbo.users u ON u.id=COALESCE(o.handled_by_user_id, CASE WHEN o.order_channel='POS' THEN o.user_id END) "
                + "LEFT JOIN dbo.customers c ON c.id=o.customer_id "
                + "WHERE o.status NOT IN ('COMPLETED','CANCELLED') "
                + (admin ? "" : "AND (o.handled_by_user_id=? OR (o.order_channel='POS' AND o.user_id=?)) ")
                + "ORDER BY o.created_date DESC, o.id DESC",
                admin ? new Object[] {} : new Object[] {userId, userId});
    }

    @Transactional
    public void updateOrderStatus(int orderId, String status, Integer actingUserId, boolean admin) {
        if (!List.of("PENDING", "PROCESSING", "COMPLETED", "CANCELLED").contains(status)) {
            throw new BusinessException("Trạng thái đơn hàng không hợp lệ.");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT status, order_channel, handled_by_user_id FROM dbo.orders WITH (UPDLOCK, ROWLOCK) WHERE id=?", orderId);
        if (rows.isEmpty()) throw new BusinessException("Không tìm thấy đơn hàng.");
        String current = String.valueOf(rows.get(0).get("status"));
        String channel = String.valueOf(rows.get(0).get("order_channel"));
        Object assignedUserId = rows.get(0).get("handled_by_user_id");
        if (!admin && (actingUserId == null || assignedUserId == null
                || ((Number) assignedUserId).intValue() != actingUserId)) {
            throw new BusinessException("Bạn chỉ được xử lý đơn online đã được giao cho mình.");
        }
        if (current.equals(status)) return;
        boolean allowed = ("PENDING".equals(current)
                && ("PROCESSING".equals(status) || "CANCELLED".equals(status)))
                || ("PROCESSING".equals(current)
                && ("COMPLETED".equals(status) || "CANCELLED".equals(status)));
        if (!allowed) throw new BusinessException("Không thể chuyển đơn từ trạng thái hiện tại sang trạng thái đã chọn.");
        if ("ONLINE".equalsIgnoreCase(channel) && "CANCELLED".equals(status)) {
            List<Map<String, Object>> details = jdbc.queryForList(
                    "SELECT product_id, quantity, unit_cost FROM dbo.order_details WHERE order_id=?", orderId);
            for (Map<String, Object> detail : details) {
                int productId = ((Number) detail.get("product_id")).intValue();
                int quantity = ((Number) detail.get("quantity")).intValue();
                jdbc.update("UPDATE dbo.products SET stock=stock+? WHERE id=?",
                        quantity, productId);
                jdbc.update("INSERT INTO dbo.inventory_transactions(product_id,user_id,transaction_type,quantity,unit_cost,reference_code,note) "
                                + "VALUES(?,NULL,'ONLINE_CANCEL_RETURN',?,?,?,?)",
                        productId, quantity, detail.get("unit_cost"), "ORD-" + orderId,
                        "Hoàn tồn do hủy đơn online #" + orderId);
            }
        }
        jdbc.update("UPDATE dbo.orders SET status=? WHERE id=?", status, orderId);
    }

    @Transactional
    public void assignOnlineOrder(int orderId, int employeeId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT status, order_channel FROM dbo.orders WITH (UPDLOCK, ROWLOCK) WHERE id=?", orderId);
        if (rows.isEmpty()) throw new BusinessException("Không tìm thấy đơn hàng.");
        Map<String, Object> order = rows.get(0);
        if (!"ONLINE".equalsIgnoreCase(String.valueOf(order.get("order_channel"))))
            throw new BusinessException("Chỉ có thể phân công đơn đặt hàng online.");
        if (!List.of("PENDING", "PROCESSING").contains(String.valueOf(order.get("status"))))
            throw new BusinessException("Chỉ có thể phân công đơn đang chờ hoặc đang xử lý.");
        Integer staffCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.users WHERE id=? AND role='STAFF'", Integer.class, employeeId);
        if (staffCount == null || staffCount == 0)
            throw new BusinessException("Không tìm thấy nhân viên hợp lệ.");
        jdbc.update("UPDATE dbo.orders SET handled_by_user_id=? WHERE id=?", employeeId, orderId);
    }

    public List<Map<String, Object>> onlineShopProducts() {
        return jdbc.queryForList("SELECT id, sku, name, price, stock FROM dbo.products WHERE stock > 0 ORDER BY name");
    }

    @Transactional
    public int createOnlineOrder(Map<Integer, Integer> cart, String customerName, String phone, String address) {
        if (cart == null || cart.isEmpty()) throw new BusinessException("Vui lòng chọn ít nhất một sản phẩm.");
        if (!StringUtils.hasText(customerName) || customerName.trim().length() > 120)
            throw new BusinessException("Nhập tên người nhận (tối đa 120 ký tự).");
        if (!StringUtils.hasText(phone) || phone.trim().length() > 20)
            throw new BusinessException("Nhập số điện thoại hợp lệ (tối đa 20 ký tự).");
        if (!StringUtils.hasText(address) || address.trim().length() > 300)
            throw new BusinessException("Nhập địa chỉ giao hàng (tối đa 300 ký tự).");

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Integer, Integer> item : cart.entrySet()) {
            int quantity = item.getValue() == null ? 0 : item.getValue();
            if (quantity <= 0) throw new BusinessException("Số lượng sản phẩm không hợp lệ.");
            Map<String, Object> product = productForSale(item.getKey());
            if (((Number) product.get("stock")).intValue() < quantity)
                throw new BusinessException("Sản phẩm " + product.get("name") + " không đủ tồn kho.");
            BigDecimal price = (BigDecimal) product.get("price");
            total = total.add(price.multiply(BigDecimal.valueOf(quantity)));
        }

        final BigDecimal orderTotal = total;

        Integer customerId = createCustomerForOnline(customerName.trim(), phone.trim(), address.trim());
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement(
                    "INSERT INTO dbo.orders(user_id,handled_by_user_id,customer_id,created_date,subtotal_amount,discount_amount,total_amount,status,order_channel,shipping_address) "
                            + "VALUES((SELECT TOP 1 id FROM dbo.users ORDER BY CASE WHEN role='ADMIN' THEN 0 ELSE 1 END,id),NULL,?,?,?,0,?,'PENDING','ONLINE',?)",
                    new String[] {"id"});
            ps.setInt(1, customerId);
            ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            ps.setBigDecimal(3, orderTotal);
            ps.setBigDecimal(4, orderTotal);
            ps.setString(5, address.trim());
            return ps;
        }, key);
        Number generatedId = key.getKey();
        if (generatedId == null) throw new BusinessException("Không thể tạo đơn hàng online.");
        int orderId = generatedId.intValue();

        for (Map.Entry<Integer, Integer> item : cart.entrySet()) {
            int productId = item.getKey();
            int quantity = item.getValue();
            Map<String, Object> product = productForSaleWithCost(productId);
            BigDecimal price = (BigDecimal) product.get("price");
            BigDecimal unitCost = (BigDecimal) product.get("cost_price");
            int changed = jdbc.update("UPDATE dbo.products SET stock=stock-? WHERE id=? AND stock>=?",
                    quantity, productId, quantity);
            if (changed == 0) throw new BusinessException("Tồn kho vừa thay đổi; vui lòng đặt lại đơn.");
            jdbc.update("INSERT INTO dbo.order_details(order_id,product_id,quantity,price,unit_cost) VALUES(?,?,?,?,?)",
                    orderId, productId, quantity, price, unitCost);
            jdbc.update("INSERT INTO dbo.inventory_transactions(product_id,user_id,transaction_type,quantity,unit_cost,reference_code,note) "
                            + "VALUES(?,NULL,'ONLINE_ORDER',?,?,?,?)",
                    productId, quantity, unitCost, "ORD-" + orderId,
                    "Giữ/trừ tồn khi khách đặt đơn online #" + orderId);
        }
        return orderId;
    }

    private Integer createCustomerForOnline(String fullName, String phone, String address) {
        List<Integer> existing = jdbc.query("SELECT id FROM dbo.customers WHERE phone=?",
                (rs, row) -> rs.getInt("id"), phone);
        if (!existing.isEmpty()) {
            jdbc.update("UPDATE dbo.customers SET full_name=?, address=? WHERE id=?",
                    fullName, address, existing.get(0));
            return existing.get(0);
        }
        return jdbc.queryForObject(
                "INSERT INTO dbo.customers(full_name,phone,address) OUTPUT INSERTED.id VALUES(?,?,?)",
                Integer.class, fullName, phone, address);
    }

    public List<Map<String, Object>> salesReturns() {
        return jdbc.queryForList("SELECT TOP 200 r.id, r.order_id, r.reason, r.status, r.refund_amount, r.created_at, "
                + "u.username FROM dbo.sales_returns r LEFT JOIN dbo.users u ON u.id=r.user_id "
                + "ORDER BY r.created_at DESC");
    }

    @Transactional
    public void createSalesReturn(int orderId, int productId, int quantity, String reason, Integer userId) {
        if (quantity <= 0 || !StringUtils.hasText(reason)) throw new BusinessException("Vui lòng nhập số lượng và lý do trả hàng.");
        List<Map<String, Object>> soldRows = jdbc.queryForList("SELECT od.quantity, od.price, od.unit_cost, "
                + "o.subtotal_amount, o.discount_amount FROM dbo.order_details od "
                + "JOIN dbo.orders o WITH (UPDLOCK, ROWLOCK) ON o.id=od.order_id "
                + "WHERE od.order_id=? AND od.product_id=? AND o.status='COMPLETED'",
                orderId, productId);
        if (soldRows.isEmpty()) throw new BusinessException("Không tìm thấy sản phẩm trong hóa đơn đã hoàn tất.");
        int sold = ((Number) soldRows.get(0).get("quantity")).intValue();
        Integer alreadyReturned = jdbc.queryForObject("SELECT COALESCE(SUM(d.quantity),0) FROM dbo.sales_return_details d "
                + "JOIN dbo.sales_returns r ON r.id=d.return_id WHERE r.order_id=? AND d.product_id=? AND r.status='ACCEPTED'",
                Integer.class, orderId, productId);
        if (quantity > sold - (alreadyReturned == null ? 0 : alreadyReturned)) {
            throw new BusinessException("Số lượng trả vượt quá số lượng còn có thể trả của hóa đơn.");
        }
        BigDecimal unitPrice = (BigDecimal) soldRows.get(0).get("price");
        BigDecimal unitCost = (BigDecimal) soldRows.get(0).get("unit_cost");
        BigDecimal subtotal = (BigDecimal) soldRows.get(0).get("subtotal_amount");
        BigDecimal discount = (BigDecimal) soldRows.get(0).get("discount_amount");
        BigDecimal grossRefund = unitPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal discountShare = subtotal.signum() == 0 ? BigDecimal.ZERO
                : discount.multiply(grossRefund).divide(subtotal, 2, RoundingMode.HALF_UP);
        BigDecimal refundAmount = grossRefund.subtract(discountShare).max(BigDecimal.ZERO);
        Integer user = userId;
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement("INSERT INTO dbo.sales_returns(order_id,user_id,reason,status,refund_amount) "
                    + "VALUES(?,?,?,'ACCEPTED',?)", new String[] {"id"});
            ps.setInt(1, orderId); ps.setObject(2, user); ps.setString(3, reason);
            ps.setBigDecimal(4, refundAmount);
            return ps;
        }, key);
        Number returnId = key.getKey();
        jdbc.update("INSERT INTO dbo.sales_return_details(return_id,product_id,quantity,unit_price) VALUES(?,?,?,?)",
                returnId.intValue(), productId, quantity, unitPrice);
        jdbc.update("UPDATE dbo.products SET stock=stock+? WHERE id=?", quantity, productId);
        insertInventoryTransaction(productId, quantity, unitCost, "CUSTOMER_RETURN",
                "Nhập lại kho từ phiếu trả hàng " + returnId, user, "RET-" + returnId);
    }

    public List<Map<String, Object>> productsForInventory() {
        return jdbc.queryForList("SELECT id,sku,name,price,stock FROM dbo.products ORDER BY name");
    }

    public Map<String, Object> productForSale(int productId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,sku,name,price,stock FROM dbo.products WHERE id=?", productId);
        if (rows.isEmpty()) throw new BusinessException("Không tìm thấy sản phẩm.");
        return rows.get(0);
    }

    private Map<String, Object> productForSaleWithCost(int productId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,sku,name,price,stock,cost_price FROM dbo.products WHERE id=?", productId);
        if (rows.isEmpty()) throw new BusinessException("Không tìm thấy sản phẩm.");
        return rows.get(0);
    }

    public List<Map<String, Object>> customerOptions() {
        return jdbc.queryForList("SELECT id,full_name,phone FROM dbo.customers ORDER BY full_name");
    }

    public List<Map<String, Object>> cartDetails(Map<Integer, Integer> cart) {
        if (cart == null || cart.isEmpty()) return List.of();
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (var item : cart.entrySet()) {
            Map<String, Object> product = productForSale(item.getKey());
            product.put("quantity", item.getValue());
            product.put("line_total", ((BigDecimal) product.get("price")).multiply(BigDecimal.valueOf(item.getValue())));
            result.add(product);
        }
        return result;
    }

    @Transactional
    public int checkout(Map<Integer, Integer> cart, Integer userId, String promotionCode, Integer customerId) {
        if (cart == null || cart.isEmpty()) throw new BusinessException("Giỏ hàng đang trống.");
        if (userId == null) throw new BusinessException("Phiên đăng nhập hết hạn. Hãy đăng nhập lại.");
        BigDecimal total = BigDecimal.ZERO;
        for (var item : cart.entrySet()) {
            int quantity = item.getValue() == null ? 0 : item.getValue();
            if (quantity <= 0) throw new BusinessException("Số lượng trong giỏ không hợp lệ.");
            Map<String, Object> product = productForSale(item.getKey());
            if (((Number) product.get("stock")).intValue() < quantity)
                throw new BusinessException("Sản phẩm " + product.get("name") + " không đủ tồn kho.");
            total = total.add(((BigDecimal) product.get("price")).multiply(BigDecimal.valueOf(quantity)));
        }
        Long promotionKey = null;
        BigDecimal discount = BigDecimal.ZERO;
        if (StringUtils.hasText(promotionCode)) {
            List<Map<String, Object>> matches = jdbc.queryForList("SELECT id,discount_type,discount_value,minimum_order_amount,usage_limit "
                    + "FROM dbo.promotions WHERE code=? AND active=1 AND (starts_at IS NULL OR starts_at<=SYSDATETIME()) "
                    + "AND (ends_at IS NULL OR ends_at>=SYSDATETIME())", promotionCode.trim().toUpperCase());
            if (matches.isEmpty()) throw new BusinessException("Mã khuyến mãi không tồn tại hoặc đã hết hạn.");
            Map<String, Object> promo = matches.get(0);
            BigDecimal minimum = (BigDecimal) promo.get("minimum_order_amount");
            if (total.compareTo(minimum) < 0) throw new BusinessException("Đơn hàng chưa đạt giá trị tối thiểu của mã khuyến mãi.");
            BigDecimal discountValue = (BigDecimal) promo.get("discount_value");
            if ("PERCENT".equalsIgnoreCase(String.valueOf(promo.get("discount_type")))) {
                discount = total.multiply(discountValue).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            } else {
                discount = discountValue.min(total);
            }
            promotionKey = ((Number) promo.get("id")).longValue();
            int used = jdbc.update("UPDATE dbo.promotions SET usage_count=usage_count+1 WHERE id=? AND active=1 "
                    + "AND (usage_limit IS NULL OR usage_count<usage_limit)", promotionKey);
            if (used == 0) throw new BusinessException("Mã khuyến mãi đã hết lượt sử dụng.");
        }
        final BigDecimal finalAmount = total.subtract(discount).max(BigDecimal.ZERO);
        final BigDecimal subtotalAmount = total;
        final Long selectedPromotionId = promotionKey;
        final BigDecimal selectedDiscount = discount;
        if (customerId != null) {
            Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.customers WHERE id=?", Integer.class, customerId);
            if (exists == null || exists == 0) throw new BusinessException("Không tìm thấy khách hàng đã chọn.");
        }
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement("INSERT INTO dbo.orders(user_id,handled_by_user_id,created_date,total_amount,status,subtotal_amount,discount_amount,promotion_id,customer_id,order_channel) "
                    + "VALUES(?,?,?,?,'COMPLETED',?,?,?,?,'POS')", new String[] {"id"});
            ps.setObject(1, userId);
            ps.setObject(2, userId);
            ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            ps.setBigDecimal(4, finalAmount); ps.setBigDecimal(5, subtotalAmount); ps.setBigDecimal(6, selectedDiscount);
            ps.setObject(7, selectedPromotionId); ps.setObject(8, customerId);
            return ps;
        }, key);
        int orderId = key.getKey().intValue();
        for (var item : cart.entrySet()) {
            Map<String, Object> product = productForSaleWithCost(item.getKey());
            int quantity = item.getValue();
            BigDecimal price = (BigDecimal) product.get("price");
            BigDecimal unitCost = (BigDecimal) product.get("cost_price");
            int changed = jdbc.update("UPDATE dbo.products SET stock=stock-? WHERE id=? AND stock>=?", quantity, item.getKey(), quantity);
            if (changed == 0) throw new BusinessException("Tồn kho vừa thay đổi; hãy tải lại giỏ hàng trước khi thanh toán.");
            jdbc.update("INSERT INTO dbo.order_details(order_id,product_id,quantity,price,unit_cost) VALUES(?,?,?,?,?)",
                    orderId, item.getKey(), quantity, price, unitCost);
            insertInventoryTransaction(item.getKey(), quantity, unitCost, "POS_SALE",
                    "Bán tại quầy theo đơn #" + orderId, userId, "ORD-" + orderId);
        }
        return orderId;
    }

    public List<Map<String, Object>> inventoryTransactions(String type) {
        if (type == null) {
            return jdbc.queryForList("SELECT TOP 200 t.id,t.transaction_type,t.quantity,t.unit_cost,t.reference_code,t.note,t.created_at, "
                    + "p.sku,p.name,u.username FROM dbo.inventory_transactions t JOIN dbo.products p ON p.id=t.product_id "
                    + "LEFT JOIN dbo.users u ON u.id=t.user_id ORDER BY t.created_at DESC");
        }
        return jdbc.queryForList("SELECT TOP 200 t.id,t.transaction_type,t.quantity,t.unit_cost,t.reference_code,t.note,t.created_at, "
                + "p.sku,p.name,u.username FROM dbo.inventory_transactions t JOIN dbo.products p ON p.id=t.product_id "
                + "LEFT JOIN dbo.users u ON u.id=t.user_id WHERE t.transaction_type=? ORDER BY t.created_at DESC", type);
    }

    @Transactional
    public void receiveStock(int productId, int quantity, BigDecimal unitCost, String note, Integer userId) {
        if (quantity <= 0 || unitCost == null || unitCost.signum() < 0) throw new BusinessException("Số lượng nhập phải lớn hơn 0 và giá vốn không được âm.");
        requireProduct(productId);
        jdbc.update("UPDATE dbo.products SET stock=stock+?, cost_price=CASE "
                        + "WHEN stock=0 OR cost_price IS NULL THEN ? "
                        + "ELSE (cost_price*stock + ?*?)/(stock+?) END WHERE id=?",
                quantity, unitCost, unitCost, quantity, quantity, productId);
        insertInventoryTransaction(productId, quantity, unitCost, "RECEIPT", note, userId);
    }

    @Transactional
    public void returnStockToSupplier(int productId, int quantity, String note, Integer userId) {
        if (quantity <= 0) throw new BusinessException("Số lượng trả phải lớn hơn 0.");
        int changed = jdbc.update("UPDATE dbo.products SET stock=stock-? WHERE id=? AND stock>=?", quantity, productId, quantity);
        if (changed == 0) throw new BusinessException("Sản phẩm không tồn tại hoặc tồn kho không đủ để trả nhà cung cấp.");
        insertInventoryTransaction(productId, quantity, null, "SUPPLIER_RETURN", note, userId);
    }

    private void requireProduct(int productId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.products WHERE id=?", Integer.class, productId);
        if (count == null || count == 0) throw new BusinessException("Không tìm thấy sản phẩm.");
    }

    private void insertInventoryTransaction(int productId, int quantity, BigDecimal cost, String type, String note, Integer userId) {
        jdbc.update("INSERT INTO dbo.inventory_transactions(product_id,user_id,transaction_type,quantity,unit_cost,note) "
                        + "VALUES(?,?,?,?,?,?)", productId, userId, type, quantity, cost,
                StringUtils.hasText(note) ? note.trim() : null);
    }

    private void insertInventoryTransaction(int productId, int quantity, BigDecimal cost, String type,
                                            String note, Integer userId, String referenceCode) {
        jdbc.update("INSERT INTO dbo.inventory_transactions(product_id,user_id,transaction_type,quantity,unit_cost,reference_code,note) "
                        + "VALUES(?,?,?,?,?,?,?)", productId, userId, type, quantity, cost,
                referenceCode, StringUtils.hasText(note) ? note.trim() : null);
    }

    public List<Map<String, Object>> customers(boolean loyalOnly) {
        String sql = "SELECT c.id,c.full_name,c.phone,c.email,c.address,COUNT(o.id) AS order_count, "
                + "COALESCE(SUM(CASE WHEN o.status='COMPLETED' THEN o.total_amount ELSE 0 END),0) AS spent "
                + "FROM dbo.customers c LEFT JOIN dbo.orders o ON o.customer_id=c.id GROUP BY c.id,c.full_name,c.phone,c.email,c.address "
                + (loyalOnly ? "HAVING COALESCE(SUM(CASE WHEN o.status='COMPLETED' THEN o.total_amount ELSE 0 END),0)>=1000000 " : "")
                + "ORDER BY spent DESC,c.full_name";
        return jdbc.queryForList(sql);
    }

    @Transactional
    public void createCustomer(String fullName, String phone, String email, String address) {
        if (!StringUtils.hasText(fullName)) throw new BusinessException("Tên khách hàng không được để trống.");
        jdbc.update("INSERT INTO dbo.customers(full_name,phone,email,address) VALUES(?,?,?,?)", fullName.trim(),
                blankToNull(phone), blankToNull(email), blankToNull(address));
    }

    @Transactional
    public Integer createCustomerForCheckout(String fullName, String phone) {
        if (!StringUtils.hasText(fullName)) throw new BusinessException("Nhập tên khách hàng mới.");
        if (fullName.trim().length() > 120) throw new BusinessException("Tên khách hàng tối đa 120 ký tự.");
        if (StringUtils.hasText(phone) && phone.trim().length() > 20)
            throw new BusinessException("Số điện thoại tối đa 20 ký tự.");

        if (StringUtils.hasText(phone)) {
            List<Integer> existing = jdbc.query("SELECT id FROM dbo.customers WHERE phone=?", (rs, row) -> rs.getInt("id"), phone.trim());
            if (!existing.isEmpty()) return existing.get(0);
        }

        return jdbc.queryForObject(
                "INSERT INTO dbo.customers(full_name,phone) OUTPUT INSERTED.id VALUES(?,?)",
                Integer.class, fullName.trim(), blankToNull(phone));
    }

    public List<Map<String, Object>> promotions() {
        return jdbc.queryForList("SELECT id,code,name,description,discount_type,discount_value,minimum_order_amount, "
                + "starts_at,ends_at,usage_limit,usage_count,active FROM dbo.promotions ORDER BY created_at DESC");
    }

    @Transactional
    public void createPromotion(String code, String name, String type, BigDecimal value, BigDecimal minimum,
                                LocalDateTime startsAt, LocalDateTime endsAt) {
        if (!StringUtils.hasText(code) || !StringUtils.hasText(name) || value == null || value.signum() <= 0)
            throw new BusinessException("Nhập mã, tên chương trình và mức giảm hợp lệ.");
        if (!List.of("PERCENT", "AMOUNT").contains(type)) throw new BusinessException("Kiểu giảm giá không hợp lệ.");
        if ("PERCENT".equals(type) && value.compareTo(BigDecimal.valueOf(100)) > 0)
            throw new BusinessException("Mức giảm theo phần trăm không thể vượt quá 100%.");
        if (startsAt != null && endsAt != null && endsAt.isBefore(startsAt))
            throw new BusinessException("Thời điểm kết thúc phải sau thời điểm bắt đầu.");
        jdbc.update("INSERT INTO dbo.promotions(code,name,discount_type,discount_value,minimum_order_amount,starts_at,ends_at) "
                        + "VALUES(?,?,?,?,?,?,?)", code.trim().toUpperCase(), name.trim(), type, value,
                minimum == null ? BigDecimal.ZERO : minimum, startsAt == null ? null : Timestamp.valueOf(startsAt),
                endsAt == null ? null : Timestamp.valueOf(endsAt));
    }

    public Map<String, Object> reportSummary() {
        return jdbc.queryForMap("WITH returned_orders AS ("
                + "SELECT r.order_id,SUM(r.refund_amount) AS refund_amount FROM dbo.sales_returns r WHERE r.status='ACCEPTED' GROUP BY r.order_id), "
                + "returned_lines AS (SELECT r.order_id,d.product_id,SUM(d.quantity) AS returned_quantity "
                + "FROM dbo.sales_returns r JOIN dbo.sales_return_details d ON d.return_id=r.id "
                + "WHERE r.status='ACCEPTED' GROUP BY r.order_id,d.product_id), "
                + "completed_orders AS (SELECT o.id,o.total_amount-COALESCE(ro.refund_amount,0) AS net_revenue "
                + "FROM dbo.orders o LEFT JOIN returned_orders ro ON ro.order_id=o.id WHERE o.status='COMPLETED'), "
                + "revenue_summary AS (SELECT COALESCE(SUM(net_revenue),0) AS revenue FROM completed_orders), "
                + "cost_summary AS (SELECT "
                + "COALESCE(SUM(CASE WHEN od.quantity-COALESCE(rl.returned_quantity,0)>0 "
                + "THEN (od.quantity-COALESCE(rl.returned_quantity,0))*od.unit_cost ELSE 0 END),0) AS cost_of_goods, "
                + "SUM(CASE WHEN od.unit_cost IS NULL AND od.quantity-COALESCE(rl.returned_quantity,0)>0 THEN 1 ELSE 0 END) AS missing_cost_count "
                + "FROM dbo.order_details od JOIN completed_orders co ON co.id=od.order_id "
                + "LEFT JOIN returned_lines rl ON rl.order_id=od.order_id AND rl.product_id=od.product_id) "
                + "SELECT (SELECT COUNT(*) FROM dbo.products) AS product_count, "
                + "(SELECT COUNT(*) FROM dbo.orders) AS order_count, "
                + "(SELECT revenue FROM revenue_summary) AS revenue, "
                + "(SELECT COALESCE(SUM(quantity*COALESCE(unit_cost,0)),0) FROM dbo.inventory_transactions WHERE transaction_type='RECEIPT') AS inventory_spend, "
                + "CASE WHEN COALESCE((SELECT missing_cost_count FROM cost_summary),0)>0 THEN CAST(NULL AS DECIMAL(18,2)) "
                + "ELSE (SELECT revenue FROM revenue_summary)-(SELECT cost_of_goods FROM cost_summary) END AS estimated_profit, "
                + "CASE WHEN COALESCE((SELECT missing_cost_count FROM cost_summary),0)>0 THEN 0 ELSE 1 END AS cost_data_complete, "
                + "(SELECT COUNT(*) FROM dbo.products WHERE stock<5) AS low_stock_count, "
                + "(SELECT COUNT(*) FROM dbo.customers) AS customer_count, "
                + "(SELECT COUNT(*) FROM dbo.orders WHERE status NOT IN ('COMPLETED','CANCELLED')) AS pending_count");
    }

    public List<Map<String, Object>> lowStockProducts() {
        return jdbc.queryForList("SELECT id,sku,name,price,stock FROM dbo.products WHERE stock<5 ORDER BY stock,name");
    }

    private String blankToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
}
