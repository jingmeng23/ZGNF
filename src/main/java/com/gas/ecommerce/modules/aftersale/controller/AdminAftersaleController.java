package com.gas.ecommerce.modules.aftersale.controller;

import com.gas.ecommerce.common.annotation.RequiresPermission;
import com.gas.ecommerce.common.result.*;
import com.gas.ecommerce.modules.aftersale.dto.*;
import com.gas.ecommerce.modules.aftersale.service.AftersaleService;
import com.gas.ecommerce.modules.aftersale.vo.AftersaleVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/aftersales")
public class AdminAftersaleController {
    private final AftersaleService aftersales;
    public AdminAftersaleController(AftersaleService aftersales) { this.aftersales = aftersales; }

    @GetMapping @RequiresPermission("aftersale:read")
    public Result<PageResult<AftersaleVO>> list(@Valid @ModelAttribute AftersaleQueryRequest query) {
        return Result.success(aftersales.listAdmin(query));
    }
    @GetMapping("/{id}") @RequiresPermission("aftersale:read")
    public Result<AftersaleVO> detail(@PathVariable Long id) {
        return Result.success(aftersales.detailAdmin(id));
    }
    @PutMapping("/{id}/review") @RequiresPermission("aftersale:write")
    public Result<AftersaleVO> review(@PathVariable Long id, @Valid @RequestBody AftersaleReviewRequest request) {
        Long adminId = com.gas.ecommerce.common.security.SecurityUtils.currentUserId();
        return Result.success(aftersales.review(id, adminId, request));
    }
    @PutMapping("/{id}/complete") @RequiresPermission("aftersale:write")
    public Result<AftersaleVO> complete(@PathVariable Long id, @Valid @RequestBody AftersaleCompleteRequest request) {
        return Result.success(aftersales.complete(id, com.gas.ecommerce.common.security.SecurityUtils.currentUserId(), request));
    }
    @GetMapping("/{id}/operations") @RequiresPermission("aftersale:read")
    public Result<java.util.List<com.gas.ecommerce.modules.aftersale.vo.AftersaleOperationVO>> operations(@PathVariable Long id) {
        return Result.success(aftersales.operationsAdmin(id));
    }
}
