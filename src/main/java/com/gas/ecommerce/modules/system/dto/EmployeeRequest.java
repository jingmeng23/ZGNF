package com.gas.ecommerce.modules.system.dto;

import jakarta.validation.constraints.*;

/** 密码通过创建/重置接口单独传递，资料更新不会修改密码或角色。 */
public record EmployeeRequest(
        @NotBlank @Pattern(regexp="[a-zA-Z0-9_.-]{3,64}") String username,
        @NotBlank @Size(max=100) String name,
        @NotBlank @Pattern(regexp="1[3-9][0-9]{9}") String phone,
        @Positive Long storeId,
        @Positive Long departmentId,
        @Positive Long positionId,
        @NotNull @Pattern(regexp="active|inactive") String status) { }
