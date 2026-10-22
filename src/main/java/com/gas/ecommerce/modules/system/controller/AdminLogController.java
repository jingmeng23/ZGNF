package com.gas.ecommerce.modules.system.controller;

import com.gas.ecommerce.common.annotation.RequiresPermission;
import com.gas.ecommerce.common.result.*;
import com.gas.ecommerce.modules.system.service.OperationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/logs")
public class AdminLogController {
    private final OperationService service;
    public AdminLogController(OperationService service){this.service=service;}
    @GetMapping @RequiresPermission("operation:read") public Result<PageResult<com.gas.ecommerce.modules.system.entity.OperationLog>> list(@Valid @ModelAttribute PageRequest q){return Result.success(PageResult.from(service.operationLogs(q)));}
}
