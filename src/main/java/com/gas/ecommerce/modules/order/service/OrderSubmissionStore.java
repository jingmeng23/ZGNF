package com.gas.ecommerce.modules.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.common.utils.*;
import com.gas.ecommerce.modules.order.dto.OrderCreateRequest;
import com.gas.ecommerce.modules.order.entity.Order;
import com.gas.ecommerce.modules.order.mapper.OrderMapper;
import com.gas.ecommerce.modules.user.mapper.UserMapper;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class OrderSubmissionStore {
    public record Claim(String orderNo, String owner, boolean replay, boolean busy) { }
    private final JdbcTemplate db;
    private final UserMapper users;
    private final OrderMapper orders;
    private final OrderNoGenerator numbers;
    private final CheckoutTicketService tickets;
    private final ObjectMapper json;
    public OrderSubmissionStore(JdbcTemplate db, UserMapper users, OrderMapper orders,
            OrderNoGenerator numbers, CheckoutTicketService tickets, ObjectMapper json) {
        this.db=db; this.users=users; this.orders=orders; this.numbers=numbers; this.tickets=tickets; this.json=json;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Claim claim(Long userId, OrderCreateRequest request) {
        var user=users.lockActiveUser(userId);
        if (user==null || !"active".equals(user.getStatus())) throw new BusinessException("用户不存在或已停用");
        if (!request.isIdempotencyKeyPresent()) throw new BusinessException("缺少结算幂等键");
        String hash=requestHash(request);
        var submissions=db.queryForList("SELECT order_no,owner,phase,request_hash FROM order_submission WHERE user_id=? AND request_key=? FOR UPDATE",userId,request.idempotencyKey());
        if (!submissions.isEmpty()) {
            var row=submissions.getFirst();
            if (!hash.equals(row.get("request_hash"))) throw new BusinessException("幂等键不能用于不同的提交参数");
            String phase=(String)row.get("phase");
            if ("FAILED".equals(phase)) throw new CheckoutConflictException("本次提交已失败并释放资源，请重新预览取得新Token");
            return new Claim((String)row.get("order_no"),(String)row.get("owner"),"SUCCEEDED".equals(phase),true);
        }
        // Replay orders created by the old implementation without requiring a new ticket.
        Order existing=orders.selectOne(Wrappers.<Order>lambdaQuery().eq(Order::getUserId,userId).eq(Order::getRequestId,request.idempotencyKey()));
        if (existing!=null) {
            if (request.orderNo()!=null) throw new BusinessException("该订单没有可验证的结算Token");
            return new Claim(existing.getOrderNo(),null,true,false);
        }
        tickets.validateIdentity(userId,request);
        String number=request.orderNo()==null?numbers.next():request.orderNo(), owner=UUID.randomUUID().toString();
        LocalDateTime lease=LocalDateTime.now().plusMinutes(2);
        db.update("INSERT INTO order_submission(order_no,user_id,request_key,token_hash,request_hash,owner,phase,lease_until,next_attempt_time) VALUES(?,?,?,?,?,?,'PROCESSING',?,?)",
                number,userId,request.idempotencyKey(),request.checkoutToken()==null?null:CheckoutDigest.sha256(request.checkoutToken()),hash,owner,lease,lease);
        return new Claim(number,owner,false,false);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void validateQuote(Long userId, OrderCreateRequest request, String fingerprint) { tickets.validate(userId,request,fingerprint); }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void heartbeat(Claim claim) {
        LocalDateTime lease=LocalDateTime.now().plusMinutes(2);
        if (db.update("UPDATE order_submission SET lease_until=?,next_attempt_time=? WHERE order_no=? AND owner=? AND phase='PROCESSING' AND lease_until>CURRENT_TIMESTAMP",lease,lease,claim.orderNo(),claim.owner())!=1)
            throw new CheckoutConflictException("提交执行权已过期，正在释放资源，请重新结算");
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void lockForCommit(Claim claim) {
        var row=db.queryForMap("SELECT owner,phase,lease_until FROM order_submission WHERE order_no=? FOR UPDATE",claim.orderNo());
        if (!claim.owner().equals(row.get("owner")) || !"PROCESSING".equals(row.get("phase"))
                || !leaseTime(row.get("lease_until")).isAfter(LocalDateTime.now()))
            throw new CheckoutConflictException("提交已进入补偿，不能保存订单");
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void succeeded(Claim claim) {
        if (db.update("UPDATE order_submission SET phase='SUCCEEDED' WHERE order_no=? AND owner=? AND phase='PROCESSING'",claim.orderNo(),claim.owner())!=1)
            throw new CheckoutConflictException("订单提交状态冲突");
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean fail(Claim claim, Throwable failure) {
        // An immediately runnable job must not be rounded into the future by a DATETIME column.
        LocalDateTime ready=LocalDateTime.now().minusSeconds(1);
        int changed=db.update("UPDATE order_submission SET phase='COMPENSATING',lease_until=?,next_attempt_time=?,last_error=? WHERE order_no=? AND owner=? AND phase='PROCESSING'",
                ready,ready,failure.getClass().getSimpleName(),claim.orderNo(),claim.owner());
        if (changed==1) compensationMessage(claim.orderNo());
        return changed==1;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public String claimCompensation(String number) {
        var rows=db.queryForList("SELECT phase FROM order_submission WHERE order_no=? AND lease_until<=CURRENT_TIMESTAMP AND next_attempt_time<=CURRENT_TIMESTAMP FOR UPDATE",number);
        if (rows.isEmpty()) return null;
        var row=rows.getFirst(); String phase=(String)row.get("phase");
        if ("FAILED".equals(phase) || "SUCCEEDED".equals(phase)) return null;
        // The order and SUCCEEDED marker are committed atomically. This also protects historical/manual recovery.
        if (orders.selectCount(Wrappers.<Order>lambdaQuery().eq(Order::getOrderNo,number))>0) {
            db.update("UPDATE order_submission SET phase='SUCCEEDED' WHERE order_no=?",number); return null;
        }
        String owner=UUID.randomUUID().toString();
        db.update("UPDATE order_submission SET phase='COMPENSATING',owner=?,lease_until=? WHERE order_no=?",owner,LocalDateTime.now().plusMinutes(2),number);
        compensationMessage(number);
        return owner;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void compensated(String number, String owner, String error) {
        db.update("UPDATE order_submission SET phase=?,attempts=attempts+1,last_error=?,lease_until=CURRENT_TIMESTAMP,next_attempt_time=? WHERE order_no=? AND owner=? AND phase='COMPENSATING'",
                error==null?"FAILED":"COMPENSATING",error,LocalDateTime.now().plusSeconds(30),number,owner);
    }
    public List<String> recoverable() {
        return db.queryForList("SELECT order_no FROM order_submission WHERE phase IN ('PROCESSING','COMPENSATING') AND lease_until<=CURRENT_TIMESTAMP AND next_attempt_time<=CURRENT_TIMESTAMP ORDER BY next_attempt_time LIMIT 100",String.class);
    }
    private void compensationMessage(String number) {
        db.update("INSERT INTO order_outbox(order_no,event_type,status,attempts,next_attempt_time) VALUES(?,'order.compensate','pending',0,CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE order_no=order_no",number);
    }
    private LocalDateTime leaseTime(Object value) {
        return value instanceof LocalDateTime time ? time : ((java.sql.Timestamp) value).toLocalDateTime();
    }
    private String requestHash(OrderCreateRequest request) {
        try { return CheckoutDigest.sha256(json.writeValueAsString(request)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }
}
