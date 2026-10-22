package com.gas.ecommerce.modules.order.controller;

import com.gas.ecommerce.common.annotation.RequiresPermission;
import com.gas.ecommerce.common.result.*;
import com.gas.ecommerce.modules.order.dto.OrderQueryRequest;
import com.gas.ecommerce.modules.order.service.OrderService;
import com.gas.ecommerce.modules.order.vo.OrderVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {
    private final OrderService orders;
    public AdminOrderController(OrderService orders) { this.orders = orders; }

    @GetMapping @RequiresPermission("order:read")
    public Result<PageResult<OrderVO>> list(@Valid @ModelAttribute OrderQueryRequest query) {
        return Result.success(orders.listAdmin(query));
    }
    @GetMapping("/{id}") @RequiresPermission("order:read")
    public Result<OrderVO> detail(@PathVariable Long id) {
        return Result.success(orders.detailAdmin(id));
    }
    @PutMapping("/{id}/process") @RequiresPermission("order:write")
    public Result<OrderVO> process(@PathVariable Long id) {
        return Result.success(orders.process(id));
    }
}
