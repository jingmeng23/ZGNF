package com.gas.ecommerce.modules.workorder.dto;
import java.time.LocalDateTime;
/**
 * 数据传输对象（DTO），承接接口传入或传出的数据，不直接负责业务处理。
 */
public record WorkOrderCreateRequest(
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(regexp="installation|repair|inspection") String type,
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=200) String title,
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=5000) String description,
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long addressId,
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Future LocalDateTime appointmentTime,
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(regexp="[A-Za-z0-9_-]{1,64}") String requestId) { }
