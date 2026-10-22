package com.gas.ecommerce.modules.system.controller;

import com.gas.ecommerce.common.annotation.RequiresPermission;
import com.gas.ecommerce.common.result.Result;
import com.gas.ecommerce.modules.system.dto.*;
import com.gas.ecommerce.modules.system.service.OrganizationManagementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/roles")
public class AdminRoleController {
    private final OrganizationManagementService service;
    public AdminRoleController(OrganizationManagementService service) { this.service=service; }
    @GetMapping @RequiresPermission("role:read")
    public Result<?> list(@Valid @ModelAttribute ManagementQuery q) { return Result.success(service.roles(q)); }
    @GetMapping("/permissions") @RequiresPermission("role:read")
    public Result<?> permissions() { return Result.success(service.permissions()); }
    @GetMapping("/{id}") @RequiresPermission("role:read")
    public Result<?> detail(@PathVariable Long id) { return Result.success(service.role(id)); }
    @PostMapping @RequiresPermission("role:write")
    public Result<?> create(@Valid @RequestBody RoleRequest r) { return Result.success(service.saveRole(null,r)); }
    @PutMapping("/{id}") @RequiresPermission("role:write")
    public Result<?> update(@PathVariable Long id,@Valid @RequestBody RoleRequest r) { return Result.success(service.saveRole(id,r)); }
    @PutMapping("/{id}/permissions") @RequiresPermission("role:write")
    public Result<?> permissions(@PathVariable Long id,@Valid @RequestBody AssignmentRequest r) { return Result.success(service.assignPermissions(id,r)); }
    @DeleteMapping("/{id}") @RequiresPermission("role:write")
    public Result<Void> delete(@PathVariable Long id) { service.deleteRole(id); return Result.success(); }
}
