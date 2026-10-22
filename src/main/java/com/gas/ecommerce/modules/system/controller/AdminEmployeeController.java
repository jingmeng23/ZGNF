package com.gas.ecommerce.modules.system.controller;

import com.gas.ecommerce.common.annotation.RequiresPermission;
import com.gas.ecommerce.common.result.Result;
import com.gas.ecommerce.modules.system.dto.*;
import com.gas.ecommerce.modules.system.service.OrganizationManagementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/employees")
public class AdminEmployeeController {
    private final OrganizationManagementService service;
    public AdminEmployeeController(OrganizationManagementService service) { this.service=service; }
    @GetMapping @RequiresPermission("employee:read")
    public Result<?> list(@Valid @ModelAttribute ManagementQuery q) { return Result.success(service.employees(q)); }
    @GetMapping("/{id}") @RequiresPermission("employee:read")
    public Result<?> detail(@PathVariable Long id) { return Result.success(service.employee(id)); }
    @PostMapping @RequiresPermission("employee:write")
    public Result<?> create(@Valid @RequestBody EmployeeCreateRequest r) { return Result.success(service.createEmployee(r)); }
    @PutMapping("/{id}") @RequiresPermission("employee:write")
    public Result<?> update(@PathVariable Long id,@Valid @RequestBody EmployeeRequest r) { return Result.success(service.updateEmployee(id,r)); }
    @PutMapping("/{id}/password") @RequiresPermission("role:write")
    public Result<Void> password(@PathVariable Long id,@Valid @RequestBody PasswordRequest r) { service.resetPassword(id,r); return Result.success(); }
    @PutMapping("/{id}/roles") @RequiresPermission("role:write")
    public Result<?> roles(@PathVariable Long id,@Valid @RequestBody AssignmentRequest r) { return Result.success(service.assignRoles(id,r)); }
    @DeleteMapping("/{id}") @RequiresPermission("employee:write")
    public Result<Void> delete(@PathVariable Long id) { service.deleteEmployee(id); return Result.success(); }
}
