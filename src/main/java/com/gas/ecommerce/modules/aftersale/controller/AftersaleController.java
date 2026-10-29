package com.gas.ecommerce.modules.aftersale.controller;

import com.gas.ecommerce.common.result.*;
import com.gas.ecommerce.common.security.SecurityUtils;
import com.gas.ecommerce.modules.aftersale.dto.*;
import com.gas.ecommerce.modules.aftersale.service.AftersaleService;
import com.gas.ecommerce.modules.aftersale.vo.AftersaleVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/aftersales")
public class AftersaleController {
    private final AftersaleService aftersales;
    public AftersaleController(AftersaleService aftersales) { this.aftersales = aftersales; }

    @PostMapping
    public Result<AftersaleVO> create(@Valid @RequestBody AftersaleCreateRequest request) {
        return Result.success(aftersales.create(SecurityUtils.currentUserId(), request));
    }
    @GetMapping
    public Result<PageResult<AftersaleVO>> list(@Valid @ModelAttribute AftersaleQueryRequest query) {
        return Result.success(aftersales.listMine(SecurityUtils.currentUserId(), query));
    }
    @GetMapping("/{id}")
    public Result<AftersaleVO> detail(@PathVariable Long id) {
        return Result.success(aftersales.detailMine(SecurityUtils.currentUserId(), id));
    }
    @PutMapping("/{id}/cancel")
    public Result<AftersaleVO> cancel(@PathVariable Long id) {
        return Result.success(aftersales.cancelMine(SecurityUtils.currentUserId(), id));
    }
    @GetMapping("/{id}/operations")
    public Result<java.util.List<com.gas.ecommerce.modules.aftersale.vo.AftersaleOperationVO>> operations(@PathVariable Long id) {
        return Result.success(aftersales.operationsMine(SecurityUtils.currentUserId(), id));
    }
}
