package com.gas.ecommerce.modules.aftersale.dto;

import com.gas.ecommerce.common.result.PageRequest;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AftersaleQueryRequest extends PageRequest {
    @Pattern(regexp = "pending|approved|rejected|completed")
    private String status;
    @Size(max = 40) private String aftersaleNo;
    @Size(max = 40) private String orderNo;
}
