package com.gas.ecommerce.modules.mall.controller;

import com.gas.ecommerce.common.annotation.RequiresPermission;
import com.gas.ecommerce.common.result.*;
import com.gas.ecommerce.common.security.SecurityUtils;
import com.gas.ecommerce.modules.mall.dto.MallQuery;
import com.gas.ecommerce.modules.mall.service.*;
import com.gas.ecommerce.modules.mall.vo.MallViews.*;
import com.gas.ecommerce.modules.product.service.CatalogReadService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated
public class MallController {
    @ExceptionHandler(ProductDetailCacheService.Unavailable.class)
    @ResponseStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
    public Result<?> unavailable(ProductDetailCacheService.Unavailable e,jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Retry-After","1");return Result.of(503001,e.getMessage(),null);
    }
    @ExceptionHandler({jakarta.validation.ConstraintViolationException.class, com.fasterxml.jackson.core.JsonProcessingException.class})
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public Result<?> invalidInput(Exception e) {return Result.of(400001,"请求参数格式错误",null);}
    private final CatalogReadService catalog;
    private final MallCatalogService mall;
    private final MallPersonalService personal;
    private final MallSearchService search;
    private final MallHomeService home;
    private final MallRequestLimiter limiter;
    public MallController(CatalogReadService catalog,MallCatalogService mall,MallPersonalService personal,MallSearchService search,MallHomeService home,MallRequestLimiter limiter) {
        this.catalog=catalog;this.mall=mall;this.personal=personal;this.search=search;this.home=home;this.limiter=limiter;
    }
    private void limit(jakarta.servlet.http.HttpServletRequest request) { limiter.check(request.getRemoteAddr()); }
    @GetMapping("/api/v1/mall/products")
    public Result<PageResult<Card>> products(@Valid @ModelAttribute MallQuery q,jakarta.servlet.http.HttpServletRequest request) { limit(request);return Result.success(catalog.list(q)); }
    @GetMapping("/api/v1/mall/products/{id}")
    public Result<Detail> detail(@PathVariable @Positive Long id,jakarta.servlet.http.HttpServletRequest request) {limit(request);return Result.success(mall.detail(id));}
    @GetMapping("/api/v1/mall/products/{id}/skus")
    public Result<Matrix> matrix(@PathVariable @Positive Long id,@RequestParam(required=false) String selection,jakarta.servlet.http.HttpServletRequest request) throws com.fasterxml.jackson.core.JsonProcessingException {
        limit(request);
        Map<String,String> selected=selection==null?Map.of():new com.fasterxml.jackson.databind.ObjectMapper().readValue(selection,new com.fasterxml.jackson.core.type.TypeReference<Map<String,String>>(){});
        if(selected.size()>20||selected.entrySet().stream().anyMatch(e->e.getKey()==null||e.getValue()==null||e.getKey().length()>64||e.getValue().length()>100))
            throw new com.gas.ecommerce.common.exception.BusinessException("规格选择无效");
        return Result.success(mall.matrix(id,selected));
    }
    @GetMapping("/api/v1/mall/brands") public Result<?> brands(jakarta.servlet.http.HttpServletRequest request) {limit(request);return Result.success(catalog.brands());}
    @GetMapping("/api/v1/mall/attributes") public Result<?> attributes(jakarta.servlet.http.HttpServletRequest request) {limit(request);return Result.success(catalog.attributeOptions());}
    @GetMapping("/api/v1/mall/search") public Result<?> search(@Valid @ModelAttribute MallQuery q,jakarta.servlet.http.HttpServletRequest request) {limit(request);return Result.success(search.search(q));}
    @GetMapping("/api/v1/mall/search/suggest") public Result<?> suggest(@RequestParam(defaultValue="") @Size(max=100) String keyword,jakarta.servlet.http.HttpServletRequest request) {limit(request);return Result.success(search.suggest(keyword));}
    @GetMapping("/api/v1/mall/search/hotwords") public Result<?> hotwords(jakarta.servlet.http.HttpServletRequest request) {limit(request);return Result.success(search.hotwords());}
    @GetMapping("/api/v1/mall/home") public Result<?> home(jakarta.servlet.http.HttpServletRequest request) {limit(request);return Result.success(home.floors());}
    @GetMapping("/api/v1/mall/me/favorites") public Result<?> favorites(@Valid @ModelAttribute PageRequest q) {return Result.success(personal.list(SecurityUtils.currentUserId(),q,false));}
    @GetMapping("/api/v1/mall/me/favorites/{id}") public Result<?> favorite(@PathVariable @Positive Long id) {return Result.success(personal.isFavorite(SecurityUtils.currentUserId(),id));}
    @PutMapping("/api/v1/mall/me/favorites/{id}") public Result<?> save(@PathVariable @Positive Long id) {personal.favorite(SecurityUtils.currentUserId(),id);return Result.success();}
    @DeleteMapping("/api/v1/mall/me/favorites/{id}") public Result<?> remove(@PathVariable @Positive Long id) {personal.unfavorite(SecurityUtils.currentUserId(),id);return Result.success();}
    @GetMapping("/api/v1/mall/me/history") public Result<?> history(@Valid @ModelAttribute PageRequest q) {return Result.success(personal.list(SecurityUtils.currentUserId(),q,true));}
    @PostMapping("/api/v1/mall/me/history/{id}") public Result<?> visit(@PathVariable @Positive Long id) {personal.visit(SecurityUtils.currentUserId(),id);return Result.success();}
    @DeleteMapping("/api/v1/mall/me/history/{id}") public Result<?> removeHistory(@PathVariable @Positive Long id) {personal.deleteHistory(SecurityUtils.currentUserId(),id);return Result.success();}
    @DeleteMapping("/api/v1/mall/me/history") public Result<?> clearHistory() {personal.clearHistory(SecurityUtils.currentUserId());return Result.success();}
    @PostMapping("/api/v1/admin/mall/search/reindex") @RequiresPermission("product:write")
    public Result<?> reindex() {return Result.success(search.reindex());}
}
