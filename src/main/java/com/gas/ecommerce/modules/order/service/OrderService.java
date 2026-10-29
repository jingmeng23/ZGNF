package com.gas.ecommerce.modules.order.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.gas.ecommerce.common.result.PageResult;
import com.gas.ecommerce.modules.order.dto.*;
import com.gas.ecommerce.modules.order.entity.Order;
import com.gas.ecommerce.modules.order.vo.OrderVO;

public interface OrderService extends IService<Order> {
    com.gas.ecommerce.modules.order.vo.OrderPreviewVO preview(Long userId, OrderPreviewRequest request);
    OrderVO create(Long userId, OrderCreateRequest request);
    PageResult<OrderVO> listMine(Long userId, OrderQueryRequest query);
    com.gas.ecommerce.modules.order.vo.OrderCursorPage scrollMine(Long userId,OrderCursorRequest query);
    OrderVO detail(Long userId, Long id);
    OrderVO cancel(Long userId, Long id);
    OrderVO confirmReceipt(Long userId, Long id);
    PageResult<OrderVO> listAdmin(OrderQueryRequest query);
    OrderVO detailAdmin(Long id);
    OrderVO process(Long id);
    OrderVO cancelAdmin(Long id);
    void cancelExpired(String orderNo);
}
