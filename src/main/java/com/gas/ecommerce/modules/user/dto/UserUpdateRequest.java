package com.gas.ecommerce.modules.user.dto;
/**
 * 数据传输对象（DTO），承接接口传入或传出的数据，不直接负责业务处理。
 */
public record UserUpdateRequest(String nickname, String avatar) { }
