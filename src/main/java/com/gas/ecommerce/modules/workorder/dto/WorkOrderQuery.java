package com.gas.ecommerce.modules.workorder.dto;
import com.gas.ecommerce.common.result.PageRequest;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true)
public class WorkOrderQuery extends PageRequest {
    @Pattern(regexp="pending|accepted|processing|completed|cancelled") private String status;
    @Pattern(regexp="installation|repair|inspection") private String type;
    @Size(max=200) private String keyword;
    @Positive private Long storeId;
    @Positive private Long employeeId;
}
