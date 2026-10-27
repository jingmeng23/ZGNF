package com.gas.ecommerce.modules.order.vo;
import java.util.List;
public record OrderCursorPage(List<OrderVO> list,boolean hasMore,Long nextCursor) { }
