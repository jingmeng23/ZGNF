package com.gas.ecommerce.modules.system.controller;

import com.gas.ecommerce.common.annotation.RequiresPermission;
import com.gas.ecommerce.common.result.*;
import com.gas.ecommerce.modules.system.dto.*;
import com.gas.ecommerce.modules.system.entity.Coupon;
import com.gas.ecommerce.modules.coupon.service.CouponService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/coupons")
public class AdminCouponController {
    private final CouponService service;
    public AdminCouponController(CouponService service){this.service=service;}
    @GetMapping @RequiresPermission("operation:read") public Result<PageResult<Coupon>> list(@Valid @ModelAttribute CouponQuery q){return Result.success(PageResult.from(service.coupons(q)));}
    @GetMapping("/{id}") @RequiresPermission("operation:read") public Result<Coupon> detail(@PathVariable Long id){return Result.success(service.detail(id));}
    @PostMapping @RequiresPermission("operation:write") public Result<Coupon> create(@Valid @RequestBody CouponRequest r){return Result.success(service.saveCoupon(null,r));}
    @PutMapping("/{id}") @RequiresPermission("operation:write") public Result<Coupon> update(@PathVariable Long id,@Valid @RequestBody CouponRequest r){return Result.success(service.saveCoupon(id,r));}
    @DeleteMapping("/{id}") @RequiresPermission("operation:write") public Result<Void> delete(@PathVariable Long id){service.deleteCoupon(id);return Result.success();}
}
