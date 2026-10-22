package com.gas.ecommerce.modules.product.controller;
import com.gas.ecommerce.common.result.PageResult;
import com.gas.ecommerce.common.result.Result;
import com.gas.ecommerce.modules.product.dto.ProductQueryRequest;
import com.gas.ecommerce.modules.product.service.ProductBrowseService;
import com.gas.ecommerce.modules.product.vo.ProductVO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductBrowseService browse;
    public ProductController(ProductBrowseService browse) { this.browse = browse; }

    @GetMapping
    public Result<PageResult<ProductVO>> list(@Valid @ModelAttribute ProductQueryRequest query) {
        return Result.success(browse.products(query));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<ProductVO>> detail(@PathVariable Long id) {
        return browse.product(id).map(p -> ResponseEntity.ok(Result.success(p)))
                .orElseGet(() -> ResponseEntity.status(404).body(Result.of(404001, "商品不存在", null)));
    }
}
