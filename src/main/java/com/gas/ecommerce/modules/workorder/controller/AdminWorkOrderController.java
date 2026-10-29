package com.gas.ecommerce.modules.workorder.controller;
import com.gas.ecommerce.common.result.*;
import com.gas.ecommerce.common.security.SecurityUtils;
import com.gas.ecommerce.modules.workorder.dto.*;
import com.gas.ecommerce.modules.workorder.vo.*;
import com.gas.ecommerce.modules.workorder.service.WorkOrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.gas.ecommerce.common.annotation.RequiresPermission;
@RestController @RequestMapping("/api/v1/admin/work-orders")
public class AdminWorkOrderController {
    private final WorkOrderService service;
    public AdminWorkOrderController(WorkOrderService service) {this.service=service;}
    @GetMapping("/options") @RequiresPermission("workorder:assign") public Result<WorkOrderOptionsVO> options() {return Result.success(service.options());}
    @GetMapping("/mine") @RequiresPermission("workorder:process") public Result<PageResult<WorkOrderVO>> mine(@Valid @ModelAttribute WorkOrderQuery q) {return Result.success(service.listAssigned(SecurityUtils.currentUserId(),q));}
    @GetMapping("/mine/{id}") @RequiresPermission("workorder:process") public Result<WorkOrderVO> mineDetail(@PathVariable Long id) {return Result.success(service.detailAssigned(SecurityUtils.currentUserId(),id));}
    @GetMapping("/mine/{id}/logs") @RequiresPermission("workorder:process") public Result<PageResult<WorkOrderLogVO>> mineLogs(@PathVariable Long id,@Valid @ModelAttribute PageRequest q) {return Result.success(service.logsAssigned(SecurityUtils.currentUserId(),id,q));}
    @GetMapping @RequiresPermission("workorder:read") public Result<PageResult<WorkOrderVO>> list(@Valid @ModelAttribute WorkOrderQuery q) {return Result.success(service.listAdmin(q));}
    @GetMapping("/{id}") @RequiresPermission("workorder:read") public Result<WorkOrderVO> detail(@PathVariable Long id) {return Result.success(service.detailAdmin(id));}
    @GetMapping("/{id}/logs") @RequiresPermission("workorder:read") public Result<PageResult<WorkOrderLogVO>> logs(@PathVariable Long id,@Valid @ModelAttribute PageRequest q) {return Result.success(service.logs(null,id,q));}
    @PutMapping("/{id}/assign") @RequiresPermission("workorder:assign") public Result<WorkOrderVO> assign(@PathVariable Long id,@Valid @RequestBody WorkOrderAssignRequest r) {return Result.success(service.assign(SecurityUtils.currentUserId(),id,r));}
    @PutMapping("/{id}/start") @RequiresPermission("workorder:process") public Result<WorkOrderVO> start(@PathVariable Long id,@Valid @RequestBody WorkOrderActionRequest r) {return Result.success(service.start(SecurityUtils.currentUserId(),id,r));}
    @PutMapping("/{id}/complete") @RequiresPermission("workorder:process") public Result<WorkOrderVO> complete(@PathVariable Long id,@Valid @RequestBody WorkOrderActionRequest r) {return Result.success(service.complete(SecurityUtils.currentUserId(),id,r));}
}
