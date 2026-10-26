package com.gas.ecommerce.modules.cart.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.gas.ecommerce.modules.cart.dto.CartAddRequest;
import com.gas.ecommerce.modules.cart.dto.CartQuery;
import com.gas.ecommerce.modules.cart.entity.CartItem;
import com.gas.ecommerce.modules.cart.vo.CartVO;
import java.util.List;

public interface CartService extends IService<CartItem> {
    CartVO getCart(Long userId);
    CartVO getCart(Long userId, CartQuery query);
    CartVO add(Long userId, CartAddRequest request);
    CartVO updateQuantity(Long userId, Long itemId, Integer quantity);
    CartVO setSelected(Long userId, Long itemId, boolean selected);
    CartVO setAllSelected(Long userId, boolean selected);
    void deleteItem(Long userId, Long itemId);
    void deleteSelected(Long userId);
    void deleteItems(Long userId, List<Long> ids);
    void clear(Long userId);
}
