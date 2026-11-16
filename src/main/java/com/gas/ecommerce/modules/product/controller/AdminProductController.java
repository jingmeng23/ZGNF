package com.gas.ecommerce.modules.product.controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.gas.ecommerce.common.annotation.RequiresPermission;
import com.gas.ecommerce.common.result.Result;
import com.gas.ecommerce.common.result.PageResult;
import com.gas.ecommerce.modules.product.dto.*;
import com.gas.ecommerce.modules.product.vo.AdminProductVO;
import com.gas.ecommerce.modules.product.service.CatalogManagementService;
import com.gas.ecommerce.modules.product.service.ProductImageStorage;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
/**
 * HTTP 接口控制器；具体请求方法以类中标注的映射注解为准。
 */
@RestController @RequestMapping("/api/v1/admin/products")
public class AdminProductController {
    private final CatalogManagementService catalog;
    private final ProductImageStorage images;
    public AdminProductController(CatalogManagementService catalog, ProductImageStorage images) {
        this.catalog = catalog;
        this.images = images;
    }

    @GetMapping @RequiresPermission("product:read")
    public Result<PageResult<AdminProductVO>> list(@Valid @ModelAttribute AdminProductQuery query) {
        return Result.success(catalog.products(query));
    }
    @GetMapping("/{id}") @RequiresPermission("product:read")
    public Result<AdminProductVO> detail(@PathVariable Long id) {
        return Result.success(catalog.product(id));
    }
    @GetMapping("/options") @RequiresPermission("product:read")
    public Result<com.gas.ecommerce.modules.product.vo.ProductOptionsVO> options() {
        return Result.success(catalog.productOptions());
    }
    @PostMapping(value = "/images", consumes = "multipart/form-data") @RequiresPermission("product:write")
    public Result<ProductImageStorage.UploadedImage> uploadImage(@RequestPart("file") MultipartFile file)
            throws java.io.IOException {
        return Result.success(images.upload(file));
    }
    @PostMapping @RequiresPermission("product:write")
    public Result<AdminProductVO> create(@Valid @RequestBody ProductCreateRequest request) {
        return Result.success(catalog.create(request));
    }
    @PutMapping("/{id}") @RequiresPermission("product:write")
    public Result<AdminProductVO> update(@PathVariable Long id, @Valid @RequestBody ProductUpdateRequest request) {
        return Result.success(catalog.update(id, request));
    }
    @PutMapping("/{id}/status") @RequiresPermission("product:write")
    public Result<AdminProductVO> status(@PathVariable Long id, @Valid @RequestBody ProductStatusRequest request) {
        return Result.success(catalog.status(id, request.status()));
    }
    @DeleteMapping("/{id}") @RequiresPermission("product:write")
    public Result<Void> delete(@PathVariable Long id) {
        catalog.deleteProduct(id); return Result.success();
    }
}
