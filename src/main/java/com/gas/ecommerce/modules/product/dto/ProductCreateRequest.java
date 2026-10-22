package com.gas.ecommerce.modules.product.dto;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
/**
 * 数据传输对象（DTO），承接接口传入或传出的数据，不直接负责业务处理。
 */
public record ProductCreateRequest(
        @NotNull @Positive Long categoryId,
        @Positive Long brandId,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 500) String subtitle,
        @Size(max = 512) String coverUrl,
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal originalPrice) { }
