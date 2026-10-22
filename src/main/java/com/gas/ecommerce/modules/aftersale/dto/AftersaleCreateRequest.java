package com.gas.ecommerce.modules.aftersale.dto;
/**
 * 数据传输对象（DTO），承接接口传入或传出的数据，不直接负责业务处理。
 */
public record AftersaleCreateRequest(Long orderId, String reason) { }
