package com.gas.ecommerce.modules.workorder.controller;
import com.gas.ecommerce.common.result.*;
import com.gas.ecommerce.common.security.SecurityUtils;
import com.gas.ecommerce.modules.workorder.dto.*;
import com.gas.ecommerce.modules.workorder.vo.*;
import com.gas.ecommerce.modules.workorder.service.WorkOrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/work-orders")
public class WorkOrderController {
    private final WorkOrderService service;
    public WorkOrderController(WorkOrderService service) { this.service=service; }
    @GetMapping("/stores") public Result<java.util.List<WorkOrderOptionsVO.StoreOption>> stores() {return Result.success(service.stores());}
    @PostMapping public Result<WorkOrderVO> create(@Valid @RequestBody WorkOrderCreateRequest r) {return Result.success(service.create(SecurityUtils.currentUserId(),r));}
    @GetMapping public Result<PageResult<WorkOrderVO>> list(@Valid @ModelAttribute WorkOrderQuery q) {return Result.success(service.listMine(SecurityUtils.currentUserId(),q));}
    @GetMapping("/{id}") public Result<WorkOrderVO> detail(@PathVariable Long id) {return Result.success(service.detail(SecurityUtils.currentUserId(),id));}
    @GetMapping("/{id}/logs") public Result<PageResult<WorkOrderLogVO>> logs(@PathVariable Long id,@Valid @ModelAttribute PageRequest q) {return Result.success(service.logs(SecurityUtils.currentUserId(),id,q));}
    @PutMapping("/{id}/cancel") public Result<WorkOrderVO> cancel(@PathVariable Long id,@Valid @RequestBody WorkOrderActionRequest r) {return Result.success(service.cancel(SecurityUtils.currentUserId(),id,r));}
}
