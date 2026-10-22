package com.gas.ecommerce.modules.system.controller;

import com.gas.ecommerce.common.annotation.RequiresPermission;
import com.gas.ecommerce.common.result.Result;
import com.gas.ecommerce.modules.system.dto.ConfigRequest;
import com.gas.ecommerce.modules.system.service.OperationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/config")
public class AdminConfigController {
    private final OperationService service;
    public AdminConfigController(OperationService service){this.service=service;}
    @GetMapping @RequiresPermission("operation:read") public Result<?> list(){return Result.success(service.configs());}
    @PutMapping @RequiresPermission("operation:write") public Result<Void> put(@Valid @RequestBody ConfigRequest r){service.putConfig(r.configKey(),r.configValue(),r.description());return Result.success();}
}
