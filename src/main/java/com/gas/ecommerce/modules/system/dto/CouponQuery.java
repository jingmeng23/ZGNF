package com.gas.ecommerce.modules.system.dto;

import com.gas.ecommerce.common.result.PageRequest;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper=true)
public class CouponQuery extends PageRequest {
    private String keyword;
    @Pattern(regexp="active|inactive|expired") private String status;
}
