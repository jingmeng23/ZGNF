package com.gas.ecommerce.common.service;

import com.gas.ecommerce.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

/** Resource-owner utility. All changes run inside that owner's local transaction. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class CheckoutResourceFence {
    private final JdbcTemplate db;
    public CheckoutResourceFence(JdbcTemplate db) { this.db = db; }
    public boolean reserve(String resource, String orderNo, String hash) {
        var row = lock(resource, orderNo);
        if ("released".equals(row.get("status"))) throw new BusinessException("资源预占已撤销，请重新结算");
        if ("reserved".equals(row.get("status"))) {
            if (!hash.equals(row.get("payload_hash"))) throw new BusinessException("同一订单号不能预占不同资源");
            return false;
        }
        db.update("UPDATE checkout_resource_fence SET status='reserved',payload_hash=? WHERE resource_type=? AND order_no=?", hash, resource, orderNo);
        return true;
    }
    public boolean release(String resource, String orderNo) {
        var row = lock(resource, orderNo);
        if ("released".equals(row.get("status"))) return false;
        db.update("UPDATE checkout_resource_fence SET status='released' WHERE resource_type=? AND order_no=?", resource, orderNo);
        return "reserved".equals(row.get("status"));
    }
    public void requireReserved(String resource, String orderNo) {
        if (!"reserved".equals(lock(resource, orderNo).get("status"))) throw new BusinessException("订单资源已释放");
    }
    private java.util.Map<String,Object> lock(String resource, String orderNo) {
        db.update("INSERT INTO checkout_resource_fence(resource_type,order_no,status) VALUES(?,?,'new') ON DUPLICATE KEY UPDATE order_no=order_no", resource, orderNo);
        return db.queryForMap("SELECT status,payload_hash FROM checkout_resource_fence WHERE resource_type=? AND order_no=? FOR UPDATE", resource, orderNo);
    }
}
