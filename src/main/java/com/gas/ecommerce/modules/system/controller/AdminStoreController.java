package com.gas.ecommerce.modules.system.controller;

import com.gas.ecommerce.common.annotation.RequiresPermission;
import com.gas.ecommerce.common.result.Result;
import com.gas.ecommerce.modules.system.dto.*;
import com.gas.ecommerce.modules.system.service.OrganizationManagementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/stores")
public class AdminStoreController {
    private final OrganizationManagementService service;
    public AdminStoreController(OrganizationManagementService service) { this.service=service; }
    @GetMapping @RequiresPermission("store:read")
    public Result<?> list(@Valid @ModelAttribute ManagementQuery q) { return Result.success(service.stores(q)); }
    @GetMapping("/{id}") @RequiresPermission("store:read")
    public Result<?> detail(@PathVariable Long id) { return Result.success(service.store(id)); }
    @PostMapping @RequiresPermission("store:write")
    public Result<?> create(@Valid @RequestBody StoreRequest r) { return Result.success(service.saveStore(null,r)); }
    @PutMapping("/{id}") @RequiresPermission("store:write")
    public Result<?> update(@PathVariable Long id,@Valid @RequestBody StoreRequest r) { return Result.success(service.saveStore(id,r)); }
    @DeleteMapping("/{id}") @RequiresPermission("store:write")
    public Result<Void> delete(@PathVariable Long id) { service.deleteStore(id); return Result.success(); }
}
