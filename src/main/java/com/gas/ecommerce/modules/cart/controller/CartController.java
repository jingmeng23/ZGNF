package com.gas.ecommerce.modules.cart.controller;


import com.gas.ecommerce.common.result.Result;
import com.gas.ecommerce.common.security.SecurityUtils;
import com.gas.ecommerce.modules.cart.dto.CartAddRequest;
import com.gas.ecommerce.modules.cart.dto.CartQuantityRequest;
import com.gas.ecommerce.modules.cart.dto.CartSelectionRequest;
import com.gas.ecommerce.modules.cart.dto.CartQuery;
import com.gas.ecommerce.modules.cart.dto.CartBatchDeleteRequest;
import com.gas.ecommerce.modules.cart.service.CartService;
import com.gas.ecommerce.modules.cart.vo.CartVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.gas.ecommerce.common.annotation.Idempotent;

/**
 * 购物车接口控制器。
 * 职责：接收 HTTP 请求 → 提取用户身份 → 委托 Service 处理 → 包装统一格式返回。
 * 不写业务逻辑，不碰数据库。
 */
@RestController                                    // 标记为 Web 控制器，所有方法返回值自动序列化为 JSON
@RequestMapping("/api/v1/cart")                    // 所有接口的公共路径前缀
public class CartController {

    // 业务逻辑委托对象（final 保证不可变，线程安全）
    private final CartService cart;

    // 构造器注入：Spring 会自动将 CartService 的实现类注入进来
    public CartController(CartService cart) {
        this.cart = cart;
    }

    // ─────────────────────────────────────────
    // 1. 获取整个购物车
    // GET  /api/v1/cart
    // ─────────────────────────────────────────
    @GetMapping                                     // 映射 GET 请求
    public Result<CartVO> getCart(@Valid @ModelAttribute CartQuery query) {
        // 提取当前用户 ID → 调用 Service 查询购物车 → 包装成成功响应返回
        return Result.success(cart.getCart(SecurityUtils.currentUserId(), query));
    }

    // ─────────────────────────────────────────
    // 2. 添加商品到购物车
    // POST /api/v1/cart/items
    // ─────────────────────────────────────────
    @PostMapping("/items")                          // 映射 POST 请求
    @Idempotent
    public Result<CartVO> add(
            @Valid                                  // 开启自动校验（DTO 里写了 @NotNull 等约束）
            @RequestBody CartAddRequest request) {  // 从请求体 JSON 反序列化为 Java 对象
        return Result.success(cart.add(SecurityUtils.currentUserId(), request));
    }

    // ─────────────────────────────────────────
    // 3. 修改单件商品的数量
    // PUT  /api/v1/cart/items/{id}
    // ─────────────────────────────────────────
    @PutMapping("/items/{id}")                      // 映射 PUT 请求，{id} 为路径变量
    @Idempotent
    public Result<CartVO> updateQuantity(
            @PathVariable Long id,                  // 提取 URL 中的 {id}（如 /items/101 中的 101）
            @Valid @RequestBody CartQuantityRequest request) {
        return Result.success(
                cart.updateQuantity(SecurityUtils.currentUserId(), id, request.quantity()));
    }

    // ─────────────────────────────────────────
    // 4. 切换单件商品的选中状态（勾选/取消勾选）
    // PUT  /api/v1/cart/items/{id}/selection
    // ─────────────────────────────────────────
    @PutMapping("/items/{id}/selection")
    public Result<CartVO> setSelected(
            @PathVariable Long id,
            @Valid @RequestBody CartSelectionRequest request) {
        return Result.success(
                cart.setSelected(SecurityUtils.currentUserId(), id, request.selected()));
    }

    // ─────────────────────────────────────────
    // 5. 全选 / 全不选（操作整个购物车）
    // PUT  /api/v1/cart/selection
    // ─────────────────────────────────────────
    @PutMapping("/selection")                       // 不带 {id}，说明是批量操作
    public Result<CartVO> setAllSelected(
            @Valid @RequestBody CartSelectionRequest request) {
        return Result.success(
                cart.setAllSelected(SecurityUtils.currentUserId(), request.selected()));
    }

    // ─────────────────────────────────────────
    // 6. 删除单件商品
    // DELETE /api/v1/cart/items/{id}
    // ─────────────────────────────────────────
    @DeleteMapping("/items/{id}")                   // 映射 DELETE 请求
    public Result<Void> deleteItem(@PathVariable Long id) {
        cart.deleteItem(SecurityUtils.currentUserId(), id);
        return Result.success();                    // Void 表示 data 为 null，只返回成功状态
    }

    // ─────────────────────────────────────────
    // 7. 批量删除已选中的商品
    // DELETE /api/v1/cart/selected
    // ─────────────────────────────────────────
    @DeleteMapping("/selected")
    public Result<Void> deleteSelected() {
        cart.deleteSelected(SecurityUtils.currentUserId());
        return Result.success();
    }

    @DeleteMapping("/items")
    public Result<Void> deleteItems(@Valid @RequestBody CartBatchDeleteRequest request) {
        cart.deleteItems(SecurityUtils.currentUserId(), request.ids());
        return Result.success();
    }

    // ─────────────────────────────────────────
    // 8. 清空整个购物车
    // DELETE /api/v1/cart
    // ─────────────────────────────────────────
    @DeleteMapping                                  // 无子路径，直接作用于购物车资源本身
    public Result<Void> clear() {
        cart.clear(SecurityUtils.currentUserId());
        return Result.success();
    }
}
