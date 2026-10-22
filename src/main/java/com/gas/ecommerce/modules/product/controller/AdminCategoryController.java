package com.gas.ecommerce.modules.product.controller;

import com.gas.ecommerce.common.annotation.RequiresPermission;
import com.gas.ecommerce.common.result.Result;
import com.gas.ecommerce.modules.product.dto.CategoryRequest;
import com.gas.ecommerce.modules.product.service.CatalogManagementService;
import com.gas.ecommerce.modules.product.vo.CategoryVO;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/categories")
public class AdminCategoryController {
    private final CatalogManagementService catalog;
    public AdminCategoryController(CatalogManagementService catalog) { this.catalog = catalog; }

    @GetMapping @RequiresPermission("category:read")
    public Result<List<CategoryVO>> list() { return Result.success(catalog.categories()); }
    @GetMapping("/{id}") @RequiresPermission("category:read")
    public Result<CategoryVO> detail(@PathVariable Long id) { return Result.success(catalog.category(id)); }
    @PostMapping @RequiresPermission("category:write")
    public Result<CategoryVO> create(@Valid @RequestBody CategoryRequest request) { return Result.success(catalog.createCategory(request)); }
    @PutMapping("/{id}") @RequiresPermission("category:write")
    public Result<CategoryVO> update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return Result.success(catalog.updateCategory(id, request));
    }
    @DeleteMapping("/{id}") @RequiresPermission("category:write")
    public Result<Void> delete(@PathVariable Long id) { catalog.deleteCategory(id); return Result.success(); }
}
