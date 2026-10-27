package com.gas.ecommerce.modules.order.controller;

import com.gas.ecommerce.common.result.*;
import com.gas.ecommerce.common.security.SecurityUtils;
import com.gas.ecommerce.modules.order.dto.*;
import com.gas.ecommerce.modules.order.service.OrderService;
import com.gas.ecommerce.modules.order.vo.OrderVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService orders;
    public OrderController(OrderService orders) { this.orders = orders; }

    @PostMapping("/preview")
    public Result<com.gas.ecommerce.modules.order.vo.OrderPreviewVO> preview(@Valid @RequestBody OrderPreviewRequest request) {
        return Result.success(orders.preview(SecurityUtils.currentUserId(), request));
    }

    @PostMapping
    public Result<OrderVO> create(@Valid @RequestBody OrderCreateRequest request) {
        return Result.success(orders.create(SecurityUtils.currentUserId(), request));
    }
    @GetMapping
    public Result<PageResult<OrderVO>> list(@Valid @ModelAttribute OrderQueryRequest query) {
        return Result.success(orders.listMine(SecurityUtils.currentUserId(), query));
    }
    @GetMapping("/{id}")
    public Result<OrderVO> detail(@PathVariable Long id) {
        return Result.success(orders.detail(SecurityUtils.currentUserId(), id));
    }
    @GetMapping("/scroll")
    public Result<com.gas.ecommerce.modules.order.vo.OrderCursorPage> scroll(@Valid @ModelAttribute OrderCursorRequest query) {
        return Result.success(orders.scrollMine(SecurityUtils.currentUserId(),query));
    }
    @PutMapping("/{id}/cancel")
    public Result<OrderVO> cancel(@PathVariable Long id) {
        return Result.success(orders.cancel(SecurityUtils.currentUserId(), id));
    }
    @PutMapping("/{id}/confirm-receipt")
    public Result<OrderVO> confirmReceipt(@PathVariable Long id) {
        return Result.success(orders.confirmReceipt(SecurityUtils.currentUserId(), id));
    }
}
