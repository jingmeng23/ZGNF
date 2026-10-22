package com.gas.ecommerce.modules.workorder.vo;
/**
 * 视图对象（VO），整理后返回给客户端展示的数据。
 */
public record WorkOrderVO(Long id, String workOrderNo, String title, String type, String status) { }
