package com.gas.ecommerce.modules.cart.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.gas.ecommerce.modules.cart.dto.CartAddRequest;
import com.gas.ecommerce.modules.cart.entity.CartItem;
import com.gas.ecommerce.modules.cart.vo.CartVO;

public interface CartService extends IService<CartItem> {
    CartVO getCart(Long userId);
    CartVO add(Long userId, CartAddRequest request);
    CartVO updateQuantity(Long userId, Long itemId, Integer quantity);
    CartVO setSelected(Long userId, Long itemId, boolean selected);
    CartVO setAllSelected(Long userId, boolean selected);
    void deleteItem(Long userId, Long itemId);
    void deleteSelected(Long userId);
    void clear(Long userId);
}
