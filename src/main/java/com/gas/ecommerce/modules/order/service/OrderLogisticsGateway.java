package com.gas.ecommerce.modules.order.service;
import com.gas.ecommerce.modules.order.vo.OrderLogisticsVO;
/** Optional downstream port; implement bounded network timeouts. Null means hide logistics. */
public interface OrderLogisticsGateway {
    OrderLogisticsVO find(Long userId,String orderNo);
}
