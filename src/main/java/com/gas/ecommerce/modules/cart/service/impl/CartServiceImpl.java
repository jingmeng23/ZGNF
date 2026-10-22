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

/**
 * 购物车服务实现。
 *
 * <p>职责边界：负责购物车的增删改查、幂等加购、库存与上架状态的可用性判定，
 * 以及组装前端渲染所需的完整视图（CartVO）。</p>
 *
 * <p>并发模型：<b>所有写操作先对 user 行执行 SELECT ... FOR UPDATE</b>（lockActiveUser），
 * 以用户为粒度串行化同一个购物车的修改，从而保证「查询是否存在 → 新增或累加」这一读改写序列的原子性。
 * 因此本类不再使用乐观锁版本号，也不需要在 cart_item 上加唯一索引兜底（加了更安全，属双重保险）。</p>
 *
 * <p>库存语义：购物车只做「可用库存校验与展示」，<b>不锁定库存</b>。真实库存扣减发生在下单环节，
 * 因此购物车显示的「有货」仅为瞬时快照，下单时仍需二次校验。</p>
 */
@Service
public class CartServiceImpl extends ServiceImpl<CartItemMapper, CartItem> implements CartService {

    private final UserMapper users;          // 用户表访问：用于锁行 + 校验用户是否启用
    private final ProductMapper products;    // 商品表访问：用于校验上架状态、取名称/价格/封面
    private final InventoryMapper inventory; // 库存表访问：用于计算可用库存 = quantity - lockedQuantity
    private final ProductBrowseService browse; // 类目浏览服务：用于判断商品类目是否对用户可见

    public CartServiceImpl(UserMapper users, ProductMapper products, InventoryMapper inventory,
            ProductBrowseService browse) {
        this.users = users;
        this.products = products;
        this.inventory = inventory;
        this.browse = browse;
    }

    /**
     * 查询购物车。
     * 只读接口，不做行锁（仅校验用户存在且启用），避免查询拖垮性能。
     */
    @Override
    public CartVO getCart(Long userId) {
        ensureActiveUser(userId);   // 普通查询，不加锁
        return buildCart(userId);
    }

    /**
     * 添加商品到购物车（幂等：已存在则累加数量）。
     *
     * 执行顺序说明：
     * 1. 锁用户行（串行化同一购物车的并发修改）
     * 2. 校验数量合法性
     * 3. 校验商品可购买（存在 + 上架中 + 类目可见）—— 纯读操作，本可在锁外，为保持事务简洁放在此处
     * 4. 判断是否已存在该商品：存在则累加，不存在则尝试恢复逻辑删除的旧记录，都没有则新增
     * 5. 重新组装购物车视图返回
     */
    @Override
    @Transactional
    public CartVO add(Long userId, CartAddRequest request) {
        lockActiveUser(userId);                     // SELECT ... FOR UPDATE 用户行，阻塞同一用户的其他写请求
        validateQuantity(request.quantity());       // 入参数量合法性：1~999
        Product product = requirePurchasable(request.productId()); // 商品不可买则直接抛异常，事务回滚

        // 查找当前用户购物车中是否已有该商品（逻辑删除的记录不会被查到）
        CartItem item = getOne(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId).eq(CartItem::getProductId, product.getId()));
        int newQuantity = request.quantity() + (item == null ? 0 : item.getQuantity()); // 累加后的数量
        validateQuantity(newQuantity);              // 累加后仍不能超过上限
        ensureStock(product.getId(), newQuantity);  // 可用库存不足则拒绝（不占库存）

        if (item != null) {
            // 已存在：直接覆盖为新数量，并强制设为选中（用户主动加购视为要买）
            item.setQuantity(newQuantity);
            item.setSelected(true);
            updateById(item);
        } else {
            // 不存在：先查是否有被逻辑删除的同商品记录（用户曾经删过它）
            CartItem deleted = baseMapper.selectDeleted(userId, product.getId());
            if (deleted != null) {
                // 有则复活（以本次请求的数量为准），避免重复插入、保留历史位置
                if (baseMapper.restore(deleted.getId(), request.quantity()) != 1)
                    throw new BusinessException("购物车商品恢复失败");
            } else {
                // 完全没有记录：新增一行
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

    /**
     * 修改单件商品数量。
     * 先通过 requireOwnedItem 校验 itemId 确实属于当前用户（防水平越权），再校验库存。
     */
    @Override
    @Transactional
    public CartVO updateQuantity(Long userId, Long itemId, Integer quantity) {
        lockActiveUser(userId);
        validateQuantity(quantity);
        CartItem item = requireOwnedItem(userId, itemId);          // 不属于该用户则抛“购物车商品不存在”
        Product product = requirePurchasable(item.getProductId()); // 商品下架后不允许再加数量
        ensureStock(product.getId(), quantity);
        item.setQuantity(quantity);
        updateById(item);
        return buildCart(userId);
    }

    /** 切换单件商品的选中状态。不改数量、不校验库存（取消勾选允许缺货商品保持勾选态，由结算页二次拦截）。 */
    @Override
    @Transactional
    public CartVO setSelected(Long userId, Long itemId, boolean selected) {
        lockActiveUser(userId);
        CartItem item = requireOwnedItem(userId, itemId);
        item.setSelected(selected);
        updateById(item);
        return buildCart(userId);
    }

    /**
     * 全选 / 全不选。
     * 使用 update(wrapper) 一条 SQL 批量更新，避免逐条 load → 逐条 update。
     * 注意：此处手动设置了 updateTime，与其他方法依赖 MP 自动填充的策略不一致，需统一。
     */
    @Override
    @Transactional
    public CartVO setAllSelected(Long userId, boolean selected) {
        lockActiveUser(userId);
        update(null, Wrappers.<CartItem>lambdaUpdate().eq(CartItem::getUserId, userId)
                .set(CartItem::getSelected, selected).set(CartItem::getUpdateTime, LocalDateTime.now()));
        return buildCart(userId);
    }

    /** 删除单件商品（若实体配置了 @TableLogic，则走逻辑删除，数据仍可用于恢复/统计）。 */
    @Override
    @Transactional
    public void deleteItem(Long userId, Long itemId) {
        lockActiveUser(userId);
        removeById(requireOwnedItem(userId, itemId).getId());
    }

    /** 批量删除已选中商品（常用于“结算后清空已购”）。 */
    @Override
    @Transactional
    public void deleteSelected(Long userId) {
        lockActiveUser(userId);
        remove(Wrappers.<CartItem>lambdaQuery().eq(CartItem::getUserId, userId)
                .eq(CartItem::getSelected, true));
    }

    /** 清空购物车（通常在下单成功后调用）。 */
    @Override
    @Transactional
    public void clear(Long userId) {
        lockActiveUser(userId);
        remove(Wrappers.<CartItem>lambdaQuery().eq(CartItem::getUserId, userId));
    }

    // ─────────────────────────────────────────
    // 核心组装方法：把 cart_item + product + inventory + 类目 拼成前端可直接渲染的 VO
    // ─────────────────────────────────────────
    private CartVO buildCart(Long userId) {
        // 1. 拉取该用户全部购物车明细，按更新时间与 ID 倒序（最近操作的排前面）
        List<CartItem> items = list(Wrappers.<CartItem>lambdaQuery().eq(CartItem::getUserId, userId)
                .orderByDesc(CartItem::getUpdateTime).orderByDesc(CartItem::getId));
        if (items.isEmpty()) return new CartVO(List.of(), 0, 0, BigDecimal.ZERO);

        List<Long> productIds = items.stream().map(CartItem::getProductId).distinct().toList();

        // 2. 批量查商品（避免循环内查库造成 N+1）
        Map<Long, Product> productById = products.selectList(Wrappers.<Product>lambdaQuery()
                        .in(Product::getId, productIds)).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        // 3. 批量查库存，按商品汇总可用库存 = quantity - lockedQuantity（多仓库合并）
        Map<Long, Long> stockByProduct = new HashMap<>();
        Set<Long> visibleCategories = browse.categories().stream()
                .map(c -> c.id()).collect(Collectors.toSet()); // 对用户可见的类目集合（如下架/隐藏类目不在此列）
        for (Inventory stock : inventory.selectList(Wrappers.<Inventory>lambdaQuery()
                .in(Inventory::getProductId, productIds))) {
            long availableStock = Math.max(0L, (long) stock.getQuantity() - stock.getLockedQuantity());
            stockByProduct.merge(stock.getProductId(), availableStock, Long::sum);
        }

        // 4. 逐条组装 VO，同时判定可用性与累计金额
        List<CartItemVO> views = new ArrayList<>();
        int totalQuantity = 0;          // 购物车总件数（含未选中、含缺货）
        int selectedQuantity = 0;       // 已选且可用的件数（用于结算栏“已选 X 件”）
        BigDecimal selectedAmount = BigDecimal.ZERO; // 已选且可用的应付金额
        for (CartItem item : items) {
            Product product = productById.get(item.getProductId());
            long availableStock = stockByProduct.getOrDefault(item.getProductId(), 0L);

            // 四条条件全部满足才算「可购买」：商品存在、上架中、类目可见、库存够
            boolean available = product != null && "on_sale".equals(product.getStatus())
                    && visibleCategories.contains(product.getCategoryId()) && availableStock >= item.getQuantity();

            BigDecimal unitPrice = product == null ? BigDecimal.ZERO : product.getPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));

            views.add(new CartItemVO(item.getId(), item.getProductId(), product == null ? null : product.getName(),
                    product == null ? null : product.getCoverUrl(), item.getQuantity(), unitPrice,
                    availableStock, item.getSelected(), available, subtotal));

            totalQuantity += item.getQuantity();
            // 只有「可用且被勾选」的商品才计入结算金额，防止用户为下架/缺货商品付款
            if (available && Boolean.TRUE.equals(item.getSelected())) {
                selectedQuantity += item.getQuantity();
                selectedAmount = selectedAmount.add(subtotal);
            }
        }
        return new CartVO(List.copyOf(views), totalQuantity, selectedQuantity, selectedAmount);
    }

    // ─────────────────────────────────────────
    // 校验与工具方法
    // ─────────────────────────────────────────

    /**
     * 校验商品是否可购买：必须存在、状态为 on_sale、且所属类目对用户可见。
     * 任一条件不满足即抛异常，事务回滚，保证不会把不可买的商品写入购物车。
     */
    private Product requirePurchasable(Long productId) {
        Product product = products.selectById(productId);
        if (product == null || !"on_sale".equals(product.getStatus())

                || browse.categories().stream().noneMatch(c -> c.id().equals(product.getCategoryId())))
            throw new BusinessException("商品不存在或已下架");
        return product;
    }

    /**
     * 校验指定商品的可用库存是否满足目标数量。
     * 注意：这里只做「检查」，不做「预留」。并发场景下该检查结果会在下单时被再次校验。
     */
    private void ensureStock(Long productId, int quantity) {
        long available = inventory.selectList(Wrappers.<Inventory>lambdaQuery()
                        .eq(Inventory::getProductId, productId)).stream()
                .mapToLong(row -> Math.max(0L, (long) row.getQuantity() - row.getLockedQuantity())).sum();
        if (quantity > available) throw new BusinessException("商品可用库存不足");
    }

    /** 数量合法性校验：非空、≥1、≤999。统一入口，避免各处魔法数字不一致。 */
    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 1 || quantity > 999)
            throw new BusinessException("单件商品的购物车数量须为 1 至 999");
    }

    /**
     * 校验购物车明细归属：itemId 必须属于 userId。
     * 这是防水平越权的最后一道防线（Controller 层的 userId 来自 token，理论上可信，但此处仍做双保险）。
     * 报错信息统一为“购物车商品不存在”，避免向调用方泄露“该记录属于他人”的信息。
     */
    private CartItem requireOwnedItem(Long userId, Long itemId) {
        CartItem item = getOne(Wrappers.<CartItem>lambdaQuery().eq(CartItem::getId, itemId)
                .eq(CartItem::getUserId, userId));
        if (item == null) throw new BusinessException("购物车商品不存在");
        return item;
    }

    /** 加锁版用户校验：SELECT ... FOR UPDATE，既校验用户存在且启用，又串行化同一用户的购物车写操作。 */
    private void lockActiveUser(Long userId) {
        User user = users.lockActiveUser(userId);
        if (user == null || !"active".equals(user.getStatus())) throw new BusinessException("用户不存在或已停用");
    }

    /** 只读版用户校验：不加锁，用于 getCart 等查询接口。 */
    private void ensureActiveUser(Long userId) {
        User user = users.selectById(userId);
        if (user == null || !"active".equals(user.getStatus())) throw new BusinessException("用户不存在或已停用");
    }
}