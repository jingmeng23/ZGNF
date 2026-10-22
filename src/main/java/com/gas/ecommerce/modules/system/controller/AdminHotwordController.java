package com.gas.ecommerce.modules.system.controller;

import com.gas.ecommerce.common.annotation.RequiresPermission;
import com.gas.ecommerce.common.result.Result;
import com.gas.ecommerce.modules.system.dto.HotwordRequest;
import com.gas.ecommerce.modules.system.service.OperationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/hotwords")
public class AdminHotwordController {
    private final OperationService service;
    public AdminHotwordController(OperationService service){this.service=service;}
    @GetMapping @RequiresPermission("operation:read") public Result<?> list(){return Result.success(service.hotwords());}
    @PostMapping @RequiresPermission("operation:write") public Result<Void> create(@Valid @RequestBody HotwordRequest r){service.saveHotword(null,r.word(),r.searchCount(),r.status());return Result.success();}
    @PutMapping("/{id}") @RequiresPermission("operation:write") public Result<Void> update(@PathVariable Long id,@Valid @RequestBody HotwordRequest r){service.saveHotword(id,r.word(),r.searchCount(),r.status());return Result.success();}
    @DeleteMapping("/{id}") @RequiresPermission("operation:write") public Result<Void> delete(@PathVariable Long id){service.deleteHotword(id);return Result.success();}
}
