package com.gas.ecommerce.modules.cart.service;

import com.baomidou.mybatisplus.spring.service.IService; // MyBatis-Plus 通用 Service
import com.gas.ecommerce.modules.cart.dto.CartAddRequest;   // 入参 DTO：添加商品（skuId、数量等）
import com.gas.ecommerce.modules.cart.entity.CartItem;      // 购物车明细实体，对应 cart_item 表
import com.gas.ecommerce.modules.cart.vo.CartVO;            // 出参 VO：购物车完整视图（含商品列表、已选合计等）

/**
 * 购物车服务接口。
 *
 * <p>职责：定义购物车模块的所有业务能力，具体实现由 CartServiceImpl 承担。</p>
 * <p>安全约定：<b>所有方法的第一个参数均为 userId</b>，实现类必须用 (user_id, item_id) 双条件
 * 执行更新/删除，确保用户只能操作自己的购物车，防止水平越权。</p>
 * <p>注意：本接口继承 MP 的 IService&lt;CartItem&gt;，因此天然拥有 save/removeById/list/
 * lambdaQuery 等基础 CRUD 方法，实现类无需重复声明。</p>
 */
public interface CartService extends IService<CartItem> {

    /**
     * 查询指定用户的完整购物车。
     *
     * @param userId 用户 ID
     * @return 购物车视图（含商品列表、各商品选中状态、已选商品总件数与总金额）；
     *         空购物车时返回空的 CartVO，不要返回 null
     */
    CartVO getCart(Long userId);

    /**
     * 添加商品到购物车。
     * <p>幂等约定：若该 sku 已存在于当前用户购物车中，则累加数量而非新增一行；
     * 需在 (user_id, sku_id) 上加唯一索引配合保证。</p>
     *
     * @param userId  用户 ID
     * @param request 添加请求（skuId、购买数量、可选的规格信息）
     * @return 添加后的完整购物车
     */
    CartVO add(Long userId, CartAddRequest request);

    /**
     * 修改购物车中某件商品的数量。
     *
     * @param userId   用户 ID
     * @param itemId   购物车明细 ID（cart_item.id）
     * @param quantity 新的购买数量，必须 ≥ 1；传 0 或负数应抛业务异常
     * @return 修改后的完整购物车
     */
    CartVO updateQuantity(Long userId, Long itemId, Integer quantity);

    /**
     * 切换单件商品的选中状态（勾选 / 取消勾选）。
     *
     * @param userId   用户 ID
     * @param itemId   购物车明细 ID
     * @param selected true = 选中，false = 取消选中
     * @return 修改后的完整购物车
     */
    CartVO setSelected(Long userId, Long itemId, boolean selected);

    /**
     * 全选 / 全不选当前用户购物车中的所有商品。
     *
     * @param userId   用户 ID
     * @param selected true = 全选，false = 全不选
     * @return 修改后的完整购物车
     */
    CartVO setAllSelected(Long userId, boolean selected);

    /**
     * 删除购物车中的单件商品。
     * <p>建议使用逻辑删除（is_deleted），便于后续做「移入收藏」或数据分析。</p>
     *
     * @param userId 用户 ID
     * @param itemId 购物车明细 ID
     */
    void deleteItem(Long userId, Long itemId);

    /**
     * 批量删除当前用户已选中的商品（用于「结算后清空已购」或「一键删除选中」）。
     *
     * @param userId 用户 ID
     */
    void deleteSelected(Long userId);

    /**
     * 清空当前用户的整个购物车。
     * <p>通常在下单成功后调用。</p>
     *
     * @param userId 用户 ID
     */
    void clear(Long userId);
}