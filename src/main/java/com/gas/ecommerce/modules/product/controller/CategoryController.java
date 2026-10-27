package com.gas.ecommerce.modules.product.controller;
import com.gas.ecommerce.common.result.Result;
import com.gas.ecommerce.modules.product.service.ProductBrowseService;
import com.gas.ecommerce.modules.product.vo.CategoryVO;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {
    private final ProductBrowseService browse; private final com.gas.ecommerce.modules.product.service.CategoryTreeService trees;
    public CategoryController(ProductBrowseService browse, com.gas.ecommerce.modules.product.service.CategoryTreeService trees) { this.browse = browse; this.trees = trees; }

    @GetMapping("/tree")
    public Result<List<com.gas.ecommerce.modules.product.vo.CategoryTreeVO>> tree() { return Result.success(trees.tree()); }

    @GetMapping
    public Result<List<CategoryVO>> list() { return Result.success(browse.categories()); }
}
