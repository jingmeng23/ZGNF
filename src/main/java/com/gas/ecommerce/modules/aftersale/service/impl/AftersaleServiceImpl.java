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

    public AftersaleServiceImpl(UserMapper users, OrderMapper orders, PaymentMapper payments) {
        this.users = users; this.orders = orders; this.payments = payments;
    }

    @Override @Transactional
    public AftersaleVO create(Long userId, AftersaleCreateRequest request) {
        var user = users.lockActiveUser(userId);
        if (user == null || !"active".equals(user.getStatus())) throw new BusinessException("用户不存在或已停用");
        Aftersale duplicate = getOne(Wrappers.<Aftersale>lambdaQuery().eq(Aftersale::getUserId, userId)
                .eq(Aftersale::getRequestId, request.requestId()));
        if (duplicate != null) return view(duplicate);

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
        return view(row);
    }

    @Override @Transactional
    public AftersaleVO review(Long id, Long adminId, AftersaleReviewRequest request) {
        Aftersale row = require(id, true);
        if (!"pending".equals(row.getStatus())) throw new BusinessException("仅待审核售后可审核");
        row.setStatus(request.status()); row.setReviewRemark(request.remark().trim());
        row.setReviewedBy(adminId); updateById(row);
        return view(row);
    }

    @Override @Transactional
    public AftersaleVO complete(Long id, AftersaleCompleteRequest request) {
        Aftersale row = require(id, true);
        if ("completed".equals(row.getStatus())) {
            if (request.refundReference().equals(row.getRefundReference())) return view(row);
            throw new BusinessException("售后已完成，不可修改退款流水号");
        }
        if (!"approved".equals(row.getStatus())) throw new BusinessException("仅审核通过的售后可完成退款");
        Order order = orders.lockById(row.getOrderId());
        if (order == null || !row.getUserId().equals(order.getUserId())) throw new BusinessException("关联订单不存在");
        Payment payment = payments.lockSuccessfulForOrder(order.getId());
        if (payment == null || payment.getAmount().compareTo(row.getAmount()) != 0)
            throw new BusinessException("待退款支付记录不存在或金额不一致");
        payment.setStatus("refunded"); payments.updateById(payment);
        order.setStatus("refunded"); orders.updateById(order);
        row.setStatus("completed"); row.setRefundReference(request.refundReference().trim());
        row.setCompletionRemark(request.remark()); row.setCompletedTime(LocalDateTime.now());
        updateById(row);
        return view(row);
    }

    private PageResult<AftersaleVO> pageRows(Long userId, AftersaleQueryRequest query) {
        var wrapper = Wrappers.<Aftersale>lambdaQuery().eq(userId != null, Aftersale::getUserId, userId)
                .eq(query.getStatus() != null, Aftersale::getStatus, query.getStatus())
                .eq(query.getAftersaleNo() != null && !query.getAftersaleNo().isBlank(),
                        Aftersale::getAftersaleNo, query.getAftersaleNo())
                .orderByDesc(Aftersale::getCreateTime).orderByDesc(Aftersale::getId);
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
                row.getCreateTime(), row.getUpdateTime());
    }
}
