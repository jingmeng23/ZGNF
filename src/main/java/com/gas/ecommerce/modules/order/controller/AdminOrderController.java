package com.gas.ecommerce.modules.order.controller;

import com.gas.ecommerce.common.annotation.RequiresPermission;
import com.gas.ecommerce.common.result.*;
import com.gas.ecommerce.modules.order.dto.OrderQueryRequest;
import com.gas.ecommerce.modules.order.dto.AdminOrderNoteRequest;
import com.gas.ecommerce.modules.order.dto.OrderShipmentRequest;
import com.gas.ecommerce.modules.order.service.AdminOrderManagementService;
import com.gas.ecommerce.common.security.SecurityUtils;
import com.gas.ecommerce.modules.order.service.OrderService;
import com.gas.ecommerce.modules.order.vo.OrderVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {
    private final OrderService orders;
    private final AdminOrderManagementService management;
    public AdminOrderController(OrderService orders,AdminOrderManagementService management) { this.orders = orders; this.management=management; }

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
        return Result.success(management.process(id,SecurityUtils.currentUserId()));
    }
    @PutMapping("/{id}/cancel") @RequiresPermission("order:write")
    public Result<OrderVO> cancel(@PathVariable Long id,@Valid @RequestBody AdminOrderNoteRequest request) {
        return Result.success(management.cancel(id,SecurityUtils.currentUserId(),request.remark()));
    }
    @PutMapping("/{id}/shipment") @RequiresPermission("order:write")
    public Result<OrderVO> ship(@PathVariable Long id,@Valid @RequestBody OrderShipmentRequest request) {
        return Result.success(management.ship(id,SecurityUtils.currentUserId(),request));
    }
    @GetMapping("/{id}/operations") @RequiresPermission("order:read")
    public Result<java.util.List<com.gas.ecommerce.modules.order.vo.AdminOrderOperationVO>> operations(@PathVariable Long id) {
        return Result.success(management.operations(id));
    }
    @PostMapping("/{id}/notes") @RequiresPermission("order:write")
    public Result<java.util.List<com.gas.ecommerce.modules.order.vo.AdminOrderOperationVO>> note(@PathVariable Long id,@Valid @RequestBody AdminOrderNoteRequest request) {
        return Result.success(management.note(id,SecurityUtils.currentUserId(),request.remark()));
    }
}
