package com.poly.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.poly.exception.BusinessException;

@Service
public class AuthService implements UserDetailsService {
    private final JdbcTemplate jdbc;

    public AuthService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return jdbc.query("SELECT username,password,role FROM dbo.users WHERE username=?", rs -> {
            if (!rs.next()) throw new UsernameNotFoundException("Không tìm thấy tài khoản.");
            String role = rs.getString("role");
            if (!"ADMIN".equalsIgnoreCase(role) && !"STAFF".equalsIgnoreCase(role)) role = "STAFF";
            return User.withUsername(rs.getString("username"))
                    .password(rs.getString("password"))
                    .roles(role.toUpperCase())
                    .build();
        }, username);
    }

    @Transactional
    public void register(String username, String password) {
        if (!StringUtils.hasText(username) || username.trim().length() < 3)
            throw new BusinessException("Tên đăng nhập cần tối thiểu 3 ký tự.");
        if (!StringUtils.hasText(password) || password.length() < 6)
            throw new BusinessException("Mật khẩu cần tối thiểu 6 ký tự.");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.users WHERE username=?", Integer.class, username.trim());
        if (count != null && count > 0) throw new BusinessException("Tên đăng nhập này đã được sử dụng.");
        jdbc.update("INSERT INTO dbo.users(username,password,role) VALUES(?,?,'STAFF')", username.trim(), password);
    }

    public Integer userId(String username) {
        return jdbc.queryForObject("SELECT id FROM dbo.users WHERE username=?", Integer.class, username);
    }

    public String roleFor(String username) {
        return jdbc.queryForObject("SELECT role FROM dbo.users WHERE username=?", String.class, username);
    }

    public java.util.Map<String, Object> profileFor(String username) {
        return jdbc.queryForMap("SELECT username, COALESCE(NULLIF(LTRIM(RTRIM(display_name)), ''), username) AS display_name, role, avatar_path "
                + "FROM dbo.users WHERE username=?", username);
    }

    @Transactional
    public void updateProfile(String username, String displayName, String newPassword, String avatarPath) {
        if (!StringUtils.hasText(displayName) || displayName.trim().length() > 100) {
            throw new BusinessException("Tên hiển thị không được để trống và tối đa 100 ký tự.");
        }
        if (StringUtils.hasText(newPassword) && newPassword.length() < 6) {
            throw new BusinessException("Mật khẩu mới cần tối thiểu 6 ký tự.");
        }
        StringBuilder sql = new StringBuilder("UPDATE dbo.users SET display_name=?");
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(displayName.trim());
        if (StringUtils.hasText(newPassword)) { sql.append(", password=?"); args.add(newPassword); }
        if (StringUtils.hasText(avatarPath)) { sql.append(", avatar_path=?"); args.add(avatarPath); }
        sql.append(" WHERE username=?"); args.add(username);
        jdbc.update(sql.toString(), args.toArray());
    }

    public java.util.List<java.util.Map<String, Object>> employeesWithSales() {
        return jdbc.queryForList("SELECT u.id, u.username, COALESCE(NULLIF(LTRIM(RTRIM(u.display_name)), ''), u.username) AS display_name, "
                + "u.commission_rate, COUNT(o.id) AS completed_orders, "
                + "COALESCE(SUM(o.total_amount-COALESCE(r.returned_amount,0)),0) AS revenue, "
                + "CAST(COALESCE(SUM(o.total_amount-COALESCE(r.returned_amount,0)),0)*u.commission_rate/100.0 AS DECIMAL(18,2)) AS commission_amount "
                + "FROM dbo.users u LEFT JOIN dbo.orders o ON "
                + "COALESCE(o.handled_by_user_id, CASE WHEN o.order_channel='POS' THEN o.user_id END)=u.id "
                + "AND o.status='COMPLETED' "
                + "LEFT JOIN (SELECT order_id,SUM(refund_amount) AS returned_amount FROM dbo.sales_returns "
                + "WHERE status='ACCEPTED' GROUP BY order_id) r ON r.order_id=o.id "
                + "WHERE u.role='STAFF' GROUP BY u.id, u.username, u.display_name, u.commission_rate "
                + "ORDER BY revenue DESC, u.username");
    }

    public java.util.List<java.util.Map<String, Object>> staffOptions() {
        return jdbc.queryForList("SELECT id, username, "
                + "COALESCE(NULLIF(LTRIM(RTRIM(display_name)), ''), username) AS display_name "
                + "FROM dbo.users WHERE role='STAFF' ORDER BY display_name, username");
    }

    public java.util.Map<String, Object> employeeForEdit(long id) {
        return jdbc.queryForMap("SELECT id, username, commission_rate, COALESCE(NULLIF(LTRIM(RTRIM(display_name)), ''), username) AS display_name "
                + "FROM dbo.users WHERE id=? AND role='STAFF'", id);
    }

    @Transactional
    public void updateEmployee(long id, String displayName, String newPassword, java.math.BigDecimal commissionRate) {
        if (!StringUtils.hasText(displayName) || displayName.trim().length() > 100)
            throw new BusinessException("Tên nhân viên không được để trống và tối đa 100 ký tự.");
        if (StringUtils.hasText(newPassword) && newPassword.length() < 6)
            throw new BusinessException("Mật khẩu mới cần tối thiểu 6 ký tự.");
        if (commissionRate == null || commissionRate.signum() < 0
                || commissionRate.compareTo(java.math.BigDecimal.valueOf(100)) > 0)
            throw new BusinessException("Hoa hồng phải nằm trong khoảng từ 0% đến 100%.");
        int updated = StringUtils.hasText(newPassword)
                ? jdbc.update("UPDATE dbo.users SET display_name=?, password=?, commission_rate=? WHERE id=? AND role='STAFF'", displayName.trim(), newPassword, commissionRate, id)
                : jdbc.update("UPDATE dbo.users SET display_name=?, commission_rate=? WHERE id=? AND role='STAFF'", displayName.trim(), commissionRate, id);
        if (updated == 0) throw new BusinessException("Không tìm thấy nhân viên cần cập nhật.");
    }
}
