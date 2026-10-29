package com.gas.ecommerce.modules.inventory.controller;

import com.gas.ecommerce.common.annotation.RequiresPermission;
import com.gas.ecommerce.common.result.*;
import com.gas.ecommerce.common.security.SecurityUtils;
import com.gas.ecommerce.modules.inventory.dto.*;
import com.gas.ecommerce.modules.inventory.vo.*;
import com.gas.ecommerce.modules.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/inventory")
public class AdminInventoryController {
    private final InventoryService service;
    public AdminInventoryController(InventoryService service) {this.service=service;}
    @GetMapping("/options") @RequiresPermission("inventory:read")
    public Result<InventoryOptionsVO> options() {return Result.success(service.options());}
    @GetMapping @RequiresPermission("inventory:read")
    public Result<PageResult<InventoryVO>> list(@Valid @ModelAttribute InventoryQuery q) {return Result.success(service.list(q));}
    @GetMapping("/{id}") @RequiresPermission("inventory:read")
    public Result<InventoryVO> detail(@PathVariable Long id) {return Result.success(service.detail(id));}
    @GetMapping("/logs") @RequiresPermission("inventory:read")
    public Result<PageResult<InventoryLogVO>> logs(@Valid @ModelAttribute InventoryLogQuery q) {return Result.success(service.logs(q));}
    @PostMapping @RequiresPermission("inventory:write")
    public Result<InventoryLogVO> create(@Valid @RequestBody InventoryCreateRequest r) {return Result.success(service.create(SecurityUtils.currentUserId(),r));}
    @PostMapping("/{id}/inbound") @RequiresPermission("inventory:write")
    public Result<InventoryLogVO> inbound(@PathVariable Long id,@Valid @RequestBody InventoryChangeRequest r) {return Result.success(service.inbound(SecurityUtils.currentUserId(),id,r));}
    @PostMapping("/{id}/outbound") @RequiresPermission("inventory:write")
    public Result<InventoryLogVO> outbound(@PathVariable Long id,@Valid @RequestBody InventoryChangeRequest r) {return Result.success(service.outbound(SecurityUtils.currentUserId(),id,r));}
    @PutMapping("/{id}/adjust") @RequiresPermission("inventory:write")
    public Result<InventoryLogVO> adjust(@PathVariable Long id,@Valid @RequestBody InventoryAdjustRequest r) {return Result.success(service.adjust(SecurityUtils.currentUserId(),id,r));}
}
