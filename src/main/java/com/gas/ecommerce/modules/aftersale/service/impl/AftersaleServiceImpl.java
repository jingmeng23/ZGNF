package com.gas.ecommerce.modules.aftersale.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.common.result.PageResult;
import com.gas.ecommerce.modules.aftersale.dto.*;
import com.gas.ecommerce.modules.aftersale.entity.Aftersale;
import com.gas.ecommerce.modules.aftersale.mapper.AftersaleMapper;
import com.gas.ecommerce.modules.aftersale.service.AftersaleService;
import com.gas.ecommerce.modules.aftersale.vo.AftersaleVO;
import com.gas.ecommerce.modules.order.entity.Order;
import com.gas.ecommerce.modules.order.mapper.OrderMapper;
import com.gas.ecommerce.modules.payment.entity.Payment;
import com.gas.ecommerce.modules.payment.mapper.PaymentMapper;
import com.gas.ecommerce.modules.user.mapper.UserMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AftersaleServiceImpl extends ServiceImpl<AftersaleMapper, Aftersale> implements AftersaleService {
    private final UserMapper users;
    private final OrderMapper orders;
    private final PaymentMapper payments;
    private final org.springframework.jdbc.core.JdbcTemplate db;
    private final com.gas.ecommerce.modules.order.service.OrderInventoryService stock;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.gas.ecommerce.modules.payment.wechat.WechatPayGateway> wechat;

    public AftersaleServiceImpl(UserMapper users, OrderMapper orders, PaymentMapper payments,org.springframework.jdbc.core.JdbcTemplate db,
            com.gas.ecommerce.modules.order.service.OrderInventoryService stock) {
        this.users = users; this.orders = orders; this.payments = payments; this.db=db; this.stock=stock;
    }

    @Override @Transactional
    public AftersaleVO create(Long userId, AftersaleCreateRequest request) {
        var user = users.lockActiveUser(userId);
        if (user == null || !"active".equals(user.getStatus())) throw new BusinessException("用户不存在或已停用");
        Aftersale duplicate = getOne(Wrappers.<Aftersale>lambdaQuery().eq(Aftersale::getUserId, userId)
                .eq(Aftersale::getRequestId, request.requestId()));
        if (duplicate != null) {
            if (!request.orderId().equals(duplicate.getOrderId()) || !request.reason().trim().equals(duplicate.getReason()))
                throw new BusinessException("请求编号已用于不同的售后申请");
            return view(duplicate);
        }

        Order order = orders.lockById(request.orderId());
        if (order == null || !userId.equals(order.getUserId())) throw new BusinessException("订单不存在");
        if (!List.of("paid", "processing", "completed").contains(order.getStatus()))
            throw new BusinessException("当前订单状态不支持申请售后");
        if (list(Wrappers.<Aftersale>lambdaQuery().eq(Aftersale::getOrderId, order.getId())
                .ne(Aftersale::getStatus, "rejected")).size() > 0)
            throw new BusinessException("该订单已有售后申请");
        Payment payment = payments.lockSuccessfulForOrder(order.getId());
        if (payment == null || payment.getAmount() == null
                || payment.getAmount().compareTo(order.getPayAmount()) != 0)
            throw new BusinessException("订单支付记录不存在或金额不一致");
        Aftersale aftersale = new Aftersale();
        aftersale.setAftersaleNo("AS" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        aftersale.setOrderId(order.getId()); aftersale.setUserId(userId);
        aftersale.setReason(request.reason().trim()); aftersale.setAmount(order.getPayAmount());
        aftersale.setRequestId(request.requestId()); aftersale.setStatus("pending"); aftersale.setIsDeleted(0);
        save(aftersale);
        record(aftersale, userId, "user", "apply", null, aftersale.getReason());
        return view(aftersale);
    }

    @Override public PageResult<AftersaleVO> listMine(Long userId, AftersaleQueryRequest query) {
        ensureActive(userId);
        return pageRows(userId, query);
    }
    @Override public PageResult<AftersaleVO> listAdmin(AftersaleQueryRequest query) { return pageRows(null, query); }
    @Override public AftersaleVO detailMine(Long userId, Long id) {
        ensureActive(userId);
        Aftersale row = require(id, false);
        if (!userId.equals(row.getUserId())) throw new BusinessException("售后记录不存在");
        return view(row);
    }
    @Override public AftersaleVO detailAdmin(Long id) { return view(require(id, false)); }

    @Override @Transactional
    public AftersaleVO cancelMine(Long userId, Long id) {
        ensureActive(userId);
        Aftersale row = require(id, true);
        if (!userId.equals(row.getUserId())) throw new BusinessException("售后记录不存在");
        if ("rejected".equals(row.getStatus())) return view(row);
        if (!"pending".equals(row.getStatus())) throw new BusinessException("仅待审核售后可撤销");
        row.setStatus("rejected"); row.setReviewRemark("用户撤销申请"); updateById(row);
        record(row, userId, "user", "cancel", "pending", "用户撤销申请");
        return view(row);
    }

    @Override @Transactional
    public AftersaleVO review(Long id, Long adminId, AftersaleReviewRequest request) {
        Aftersale row = require(id, true);
        if (request.status().equals(row.getStatus()) && adminId.equals(row.getReviewedBy())
                && request.remark().trim().equals(row.getReviewRemark())) return view(row);
        if (!"pending".equals(row.getStatus())) throw new BusinessException("仅待审核售后可审核");
        row.setStatus(request.status()); row.setReviewRemark(request.remark().trim());
        row.setReviewedBy(adminId); updateById(row);
        record(row, adminId, "admin", "approved".equals(request.status()) ? "approve" : "reject", "pending", row.getReviewRemark());
        return view(row);
    }

    @Override @Transactional
    public AftersaleVO complete(Long id, Long adminId, AftersaleCompleteRequest request) {
        Aftersale row = require(id, true);
        if ("completed".equals(row.getStatus())) {
            if (request.refundReference().trim().equals(row.getRefundReference())) return view(row);
            throw new BusinessException("售后已完成，不可修改退款流水号");
        }
        if (!"approved".equals(row.getStatus())) throw new BusinessException("仅审核通过的售后可完成退款");
        Order order = orders.lockById(row.getOrderId());
        if (order == null || !row.getUserId().equals(order.getUserId())) throw new BusinessException("关联订单不存在");
        Payment payment = payments.lockSuccessfulForOrder(order.getId());
        if (payment == null || payment.getAmount() == null || payment.getAmount().compareTo(row.getAmount()) != 0
                || order.getPayAmount() == null || order.getPayAmount().compareTo(row.getAmount()) != 0)
            throw new BusinessException("待退款支付记录不存在或金额不一致");
        if ("wechat_mini".equals(payment.getChannel())) {
            String refundNo = "WXAS" + row.getId();
            if (!refundNo.equals(request.refundReference()))
                throw new BusinessException("微信支付订单请使用微信原路退款操作，不能手工登记退款成功");
            var gateway = wechat.getIfAvailable();
            if (gateway == null) throw new BusinessException("微信支付尚未启用");
            if (!gateway.refund(payment, refundNo))
                throw new BusinessException("微信退款处理中，请稍后点击查询退款结果；重复操作使用同一退款编号，不会重复退款");
        }
        // Unfulfilled orders still hold reservations; fulfilled goods require a separate physical return.
        if ("paid".equals(order.getStatus())) stock.release(order);
        payment.setStatus("refunded"); payments.updateById(payment);
        com.gas.ecommerce.modules.order.service.OrderStateMachine.persistTransition(order, "refunded",db);
        row.setStatus("completed"); row.setRefundReference(request.refundReference().trim());
        row.setCompletionRemark(request.remark()); row.setCompletedTime(LocalDateTime.now());
        updateById(row);
        record(row, adminId, "admin", "complete", "approved", request.remark() == null ? "登记渠道退款完成" : request.remark().trim());
        return view(row);
    }

    private PageResult<AftersaleVO> pageRows(Long userId, AftersaleQueryRequest query) {
        Long orderId = null;
        if (query.getOrderNo() != null && !query.getOrderNo().isBlank()) {
            var matches = orders.selectList(Wrappers.<Order>lambdaQuery().eq(Order::getOrderNo,query.getOrderNo().trim()));
            if (matches.isEmpty()) return new PageResult<>(List.of(),0,query.getPage(),query.getSize(),0);
            orderId = matches.getFirst().getId();
        }
        var wrapper = Wrappers.<Aftersale>lambdaQuery().eq(userId != null, Aftersale::getUserId, userId)
                .eq(orderId != null, Aftersale::getOrderId, orderId)
                .eq(query.getStatus() != null, Aftersale::getStatus, query.getStatus())
                .eq(query.getAftersaleNo() != null && !query.getAftersaleNo().isBlank(),
                        Aftersale::getAftersaleNo, query.getAftersaleNo() == null ? null : query.getAftersaleNo().trim())
                .last("ORDER BY " + query.resolveSort(java.util.Map.of("createTime","create_time","updateTime","update_time","id","id","amount","amount"))
                        + ("asc".equals(query.getOrder()) ? " ASC" : " DESC") + ", id " + ("asc".equals(query.getOrder()) ? "ASC" : "DESC"));
        Page<Aftersale> page = page(new Page<>(query.getPage(), query.getSize()), wrapper);
        return new PageResult<>(page.getRecords().stream().map(this::view).toList(),
                page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    private Aftersale require(Long id, boolean lock) {
        Aftersale row = lock ? baseMapper.lockById(id) : getById(id);
        if (row == null) throw new BusinessException("售后记录不存在");
        return row;
    }
    private void ensureActive(Long userId) {
        var user = users.selectById(userId);
        if (user == null || !"active".equals(user.getStatus())) throw new BusinessException("用户不存在或已停用");
    }
    private AftersaleVO view(Aftersale row) {
        Order order = orders.selectById(row.getOrderId());
        return new AftersaleVO(row.getId(), row.getAftersaleNo(), row.getOrderId(),
                order == null ? null : order.getOrderNo(), row.getAmount(), row.getStatus(),
                row.getReason(), row.getReviewRemark(), row.getRefundReference(),
                row.getCreateTime(), row.getUpdateTime(), row.getReviewedBy(), row.getCompletionRemark(), row.getCompletedTime());
    }
    @Override public List<com.gas.ecommerce.modules.aftersale.vo.AftersaleOperationVO> operationsAdmin(Long id) {
        require(id,false); return operations(id);
    }
    @Override public List<com.gas.ecommerce.modules.aftersale.vo.AftersaleOperationVO> operationsMine(Long userId,Long id) {
        detailMine(userId,id); return operations(id);
    }
    private List<com.gas.ecommerce.modules.aftersale.vo.AftersaleOperationVO> operations(Long id) {
        return db.query("SELECT id,action,from_status,to_status,operator_id,operator_type,remark,occurred_at FROM aftersale_operation WHERE aftersale_id=? ORDER BY id",
                (r,n)->new com.gas.ecommerce.modules.aftersale.vo.AftersaleOperationVO(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getLong(5),r.getString(6),r.getString(7),r.getTimestamp(8).toLocalDateTime()),id);
    }
    private void record(Aftersale row,Long operator,String type,String action,String from,String remark) {
        db.update("INSERT INTO aftersale_operation(aftersale_id,operator_id,operator_type,action,from_status,to_status,remark) VALUES(?,?,?,?,?,?,?)",
                row.getId(),operator,type,action,from,row.getStatus(),remark);
    }
}
