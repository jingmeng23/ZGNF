package com.gas.ecommerce.modules.inventory.dto;
/**
 * 数据传输对象（DTO），承接接口传入或传出的数据，不直接负责业务处理。
 */
public record InventoryAdjustRequest(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(0) Integer quantity,
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(regexp="[A-Za-z0-9_-]{1,64}") String requestId,
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=255) String remark) { }
