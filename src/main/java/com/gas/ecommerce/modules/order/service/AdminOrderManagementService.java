package com.gas.ecommerce.modules.order.service;

import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.modules.order.dto.OrderShipmentRequest;
import com.gas.ecommerce.modules.order.entity.Order;
import com.gas.ecommerce.modules.order.mapper.OrderMapper;
import com.gas.ecommerce.modules.order.vo.*;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOrderManagementService {
    private final OrderService orders;
    private final OrderMapper mapper;
    private final JdbcTemplate db;
    public AdminOrderManagementService(OrderService orders, OrderMapper mapper, JdbcTemplate db) {
        this.orders=orders; this.mapper=mapper; this.db=db;
    }
    public List<AdminOrderOperationVO> operations(Long id) {
        if (mapper.selectById(id)==null) throw new BusinessException("订单不存在");
        return db.query("SELECT id,operator_id,action,remark,occurred_at FROM order_admin_operation WHERE order_id=? ORDER BY id DESC",
                (r,n)->new AdminOrderOperationVO(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getTimestamp(5).toLocalDateTime()),id);
    }
    @Transactional public OrderVO process(Long id,Long operator) {
        Order order=lock(id);
        boolean wasPaid="paid".equals(order.getStatus());
        OrderVO result=orders.process(id);
        if (wasPaid && "processing".equals(result.status())) record(id,operator,"process","开始履约处理");
        return result;
    }
    @Transactional public OrderVO cancel(Long id,Long operator,String remark) {
        Order order=lock(id);
        boolean wasPending="pending_payment".equals(order.getStatus());
        OrderVO result=orders.cancelAdmin(id);
        if (wasPending && "cancelled".equals(result.status())) record(id,operator,"cancel",remark.strip());
        return result;
    }
    @Transactional public List<AdminOrderOperationVO> note(Long id,Long operator,String remark) {
        lock(id); record(id,operator,"note",remark.strip()); return operations(id);
    }
    @Transactional public OrderVO ship(Long id,Long operator,OrderShipmentRequest request) {
        Order order=lock(id);
        if (db.queryForObject("SELECT COUNT(*) FROM aftersale WHERE order_id=? AND status IN ('pending','approved') AND is_deleted=0",Integer.class,id)>0)
            throw new BusinessException("订单存在处理中售后，请先处理售后再发货");
        if (!List.of("paid","processing").contains(order.getStatus())) throw new BusinessException("仅已支付或处理中订单可发货或修改物流");
        String carrier=request.carrier().strip(), tracking=request.trackingNo();
        var existing=db.queryForList("SELECT carrier,tracking_no FROM order_shipment WHERE order_id=?",id);
        if (existing.isEmpty()) {
            // Existing processing entry consumes inventory exactly once under the same order lock.
            orders.process(id);
            db.update("INSERT INTO order_shipment(order_id,carrier,tracking_no) VALUES(?,?,?)",id,carrier,tracking);
            record(id,operator,"ship",carrier+" / "+tracking);
        } else if (!carrier.equals(existing.getFirst().get("carrier")) || !tracking.equals(existing.getFirst().get("tracking_no"))) {
            db.update("UPDATE order_shipment SET carrier=?,tracking_no=?,updated_at=CURRENT_TIMESTAMP WHERE order_id=?",carrier,tracking,id);
            record(id,operator,"shipment_update",existing.getFirst().get("carrier")+" / "+existing.getFirst().get("tracking_no")+" → "+carrier+" / "+tracking);
        }
        return orders.detailAdmin(id);
    }
    private Order lock(Long id) {
        Order order=mapper.lockById(id);
        if (order==null) throw new BusinessException("订单不存在");
        return order;
    }
    private void record(Long id,Long operator,String action,String remark) {
        db.update("INSERT INTO order_admin_operation(order_id,operator_id,action,remark) VALUES(?,?,?,?)",id,operator,action,remark);
    }
}
