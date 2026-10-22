package com.gas.ecommerce.modules.cart.controller;

import com.gas.ecommerce.common.result.Result;
import com.gas.ecommerce.common.security.SecurityUtils;
import com.gas.ecommerce.modules.cart.dto.CartAddRequest;
import com.gas.ecommerce.modules.cart.dto.CartQuantityRequest;
import com.gas.ecommerce.modules.cart.dto.CartSelectionRequest;
import com.gas.ecommerce.modules.cart.service.CartService;
import com.gas.ecommerce.modules.cart.vo.CartVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {
    private final CartService cart;

    public CartController(CartService cart) {
        this.cart = cart;
    }

    @GetMapping
    public Result<CartVO> getCart() {
        return Result.success(cart.getCart(SecurityUtils.currentUserId()));
    }

    @PostMapping("/items")
    public Result<CartVO> add(@Valid @RequestBody CartAddRequest request) {
        return Result.success(cart.add(SecurityUtils.currentUserId(), request));
    }

    @PutMapping("/items/{id}")
    public Result<CartVO> updateQuantity(@PathVariable Long id, @Valid @RequestBody CartQuantityRequest request) {
        return Result.success(cart.updateQuantity(SecurityUtils.currentUserId(), id, request.quantity()));
    }

    @PutMapping("/items/{id}/selection")
    public Result<CartVO> setSelected(@PathVariable Long id, @Valid @RequestBody CartSelectionRequest request) {
        return Result.success(cart.setSelected(SecurityUtils.currentUserId(), id, request.selected()));
    }

    @PutMapping("/selection")
    public Result<CartVO> setAllSelected(@Valid @RequestBody CartSelectionRequest request) {
        return Result.success(cart.setAllSelected(SecurityUtils.currentUserId(), request.selected()));
    }

    @DeleteMapping("/items/{id}")
    public Result<Void> deleteItem(@PathVariable Long id) {
        cart.deleteItem(SecurityUtils.currentUserId(), id);
        return Result.success();
    }

    @DeleteMapping("/selected")
    public Result<Void> deleteSelected() {
        cart.deleteSelected(SecurityUtils.currentUserId());
        return Result.success();
    }

    @DeleteMapping
    public Result<Void> clear() {
        cart.clear(SecurityUtils.currentUserId());
        return Result.success();
    }
}
