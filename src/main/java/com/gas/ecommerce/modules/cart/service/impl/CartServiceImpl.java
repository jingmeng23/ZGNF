package com.gas.ecommerce.modules.cart.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.modules.cart.dto.CartAddRequest;
import com.gas.ecommerce.modules.cart.entity.CartItem;
import com.gas.ecommerce.modules.cart.mapper.CartItemMapper;
import com.gas.ecommerce.modules.cart.service.CartService;
import com.gas.ecommerce.modules.cart.vo.CartItemVO;
import com.gas.ecommerce.modules.cart.vo.CartVO;
import com.gas.ecommerce.modules.inventory.entity.Inventory;
import com.gas.ecommerce.modules.inventory.mapper.InventoryMapper;
import com.gas.ecommerce.modules.product.entity.Product;
import com.gas.ecommerce.modules.product.mapper.ProductMapper;
import com.gas.ecommerce.modules.product.service.ProductBrowseService;
import com.gas.ecommerce.modules.user.entity.User;
import com.gas.ecommerce.modules.user.mapper.UserMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartServiceImpl extends ServiceImpl<CartItemMapper, CartItem> implements CartService {
    private final UserMapper users;
    private final ProductMapper products;
    private final InventoryMapper inventory;
    private final ProductBrowseService browse;

    public CartServiceImpl(UserMapper users, ProductMapper products, InventoryMapper inventory,
            ProductBrowseService browse) {
        this.users = users;
        this.products = products;
        this.inventory = inventory;
        this.browse = browse;
    }

    @Override
    public CartVO getCart(Long userId) {
        ensureActiveUser(userId);
        return buildCart(userId);
    }

    @Override
    @Transactional
    public CartVO add(Long userId, CartAddRequest request) {
        lockActiveUser(userId);
        validateQuantity(request.quantity());
        Product product = requirePurchasable(request.productId());
        CartItem item = getOne(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId).eq(CartItem::getProductId, product.getId()));
        int newQuantity = request.quantity() + (item == null ? 0 : item.getQuantity());
        validateQuantity(newQuantity);
        ensureStock(product.getId(), newQuantity);

        if (item != null) {
            item.setQuantity(newQuantity);
            item.setSelected(true);
            updateById(item);
        } else {
            CartItem deleted = baseMapper.selectDeleted(userId, product.getId());
            if (deleted != null) {
                if (baseMapper.restore(deleted.getId(), request.quantity()) != 1)
                    throw new BusinessException("购物车商品恢复失败");
            } else {
                item = new CartItem();
                item.setUserId(userId);
                item.setProductId(product.getId());
                item.setQuantity(request.quantity());
                item.setSelected(true);
                save(item);
            }
        }
        return buildCart(userId);
    }

    @Override
    @Transactional
    public CartVO updateQuantity(Long userId, Long itemId, Integer quantity) {
        lockActiveUser(userId);
        validateQuantity(quantity);
        CartItem item = requireOwnedItem(userId, itemId);
        Product product = requirePurchasable(item.getProductId());
        ensureStock(product.getId(), quantity);
        item.setQuantity(quantity);
        updateById(item);
        return buildCart(userId);
    }

    @Override
    @Transactional
    public CartVO setSelected(Long userId, Long itemId, boolean selected) {
        lockActiveUser(userId);
        CartItem item = requireOwnedItem(userId, itemId);
        item.setSelected(selected);
        updateById(item);
        return buildCart(userId);
    }

    @Override
    @Transactional
    public CartVO setAllSelected(Long userId, boolean selected) {
        lockActiveUser(userId);
        update(null, Wrappers.<CartItem>lambdaUpdate().eq(CartItem::getUserId, userId)
                .set(CartItem::getSelected, selected).set(CartItem::getUpdateTime, LocalDateTime.now()));
        return buildCart(userId);
    }

    @Override
    @Transactional
    public void deleteItem(Long userId, Long itemId) {
        lockActiveUser(userId);
        removeById(requireOwnedItem(userId, itemId).getId());
    }

    @Override
    @Transactional
    public void deleteSelected(Long userId) {
        lockActiveUser(userId);
        remove(Wrappers.<CartItem>lambdaQuery().eq(CartItem::getUserId, userId)
                .eq(CartItem::getSelected, true));
    }

    @Override
    @Transactional
    public void clear(Long userId) {
        lockActiveUser(userId);
        remove(Wrappers.<CartItem>lambdaQuery().eq(CartItem::getUserId, userId));
    }

    private CartVO buildCart(Long userId) {
        List<CartItem> items = list(Wrappers.<CartItem>lambdaQuery().eq(CartItem::getUserId, userId)
                .orderByDesc(CartItem::getUpdateTime).orderByDesc(CartItem::getId));
        if (items.isEmpty()) return new CartVO(List.of(), 0, 0, BigDecimal.ZERO);

        List<Long> productIds = items.stream().map(CartItem::getProductId).distinct().toList();
        Map<Long, Product> productById = products.selectList(Wrappers.<Product>lambdaQuery()
                        .in(Product::getId, productIds)).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, Long> stockByProduct = new HashMap<>();
        Set<Long> visibleCategories = browse.categories().stream()
                .map(c -> c.id()).collect(Collectors.toSet());
        for (Inventory stock : inventory.selectList(Wrappers.<Inventory>lambdaQuery()
                .in(Inventory::getProductId, productIds))) {
            long availableStock = Math.max(0L, (long) stock.getQuantity() - stock.getLockedQuantity());
            stockByProduct.merge(stock.getProductId(), availableStock, Long::sum);
        }

        List<CartItemVO> views = new ArrayList<>();
        int totalQuantity = 0;
        int selectedQuantity = 0;
        BigDecimal selectedAmount = BigDecimal.ZERO;
        for (CartItem item : items) {
            Product product = productById.get(item.getProductId());
            long availableStock = stockByProduct.getOrDefault(item.getProductId(), 0L);
            boolean available = product != null && "on_sale".equals(product.getStatus())
                    && visibleCategories.contains(product.getCategoryId()) && availableStock >= item.getQuantity();
            BigDecimal unitPrice = product == null ? BigDecimal.ZERO : product.getPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
            views.add(new CartItemVO(item.getId(), item.getProductId(), product == null ? null : product.getName(),
                    product == null ? null : product.getCoverUrl(), item.getQuantity(), unitPrice,
                    availableStock, item.getSelected(), available, subtotal));
            totalQuantity += item.getQuantity();
            if (available && Boolean.TRUE.equals(item.getSelected())) {
                selectedQuantity += item.getQuantity();
                selectedAmount = selectedAmount.add(subtotal);
            }
        }
        return new CartVO(List.copyOf(views), totalQuantity, selectedQuantity, selectedAmount);
    }

    private Product requirePurchasable(Long productId) {
        Product product = products.selectById(productId);
        if (product == null || !"on_sale".equals(product.getStatus())
                || browse.categories().stream().noneMatch(c -> c.id().equals(product.getCategoryId())))
            throw new BusinessException("商品不存在或已下架");
        return product;
    }

    private void ensureStock(Long productId, int quantity) {
        long available = inventory.selectList(Wrappers.<Inventory>lambdaQuery()
                        .eq(Inventory::getProductId, productId)).stream()
                .mapToLong(row -> Math.max(0L, (long) row.getQuantity() - row.getLockedQuantity())).sum();
        if (quantity > available) throw new BusinessException("商品可用库存不足");
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 1 || quantity > 999)
            throw new BusinessException("单件商品的购物车数量须为 1 至 999");
    }

    private CartItem requireOwnedItem(Long userId, Long itemId) {
        CartItem item = getOne(Wrappers.<CartItem>lambdaQuery().eq(CartItem::getId, itemId)
                .eq(CartItem::getUserId, userId));
        if (item == null) throw new BusinessException("购物车商品不存在");
        return item;
    }

    private void lockActiveUser(Long userId) {
        User user = users.lockActiveUser(userId);
        if (user == null || !"active".equals(user.getStatus())) throw new BusinessException("用户不存在或已停用");
    }

    private void ensureActiveUser(Long userId) {
        User user = users.selectById(userId);
        if (user == null || !"active".equals(user.getStatus())) throw new BusinessException("用户不存在或已停用");
    }
}
