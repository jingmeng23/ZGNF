package com.gas.ecommerce.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
/**
 * 数据传输对象（DTO），承接接口传入或传出的数据，不直接负责业务处理。
 */

public record SmsCodeRequest(@NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确") String phone) { }
