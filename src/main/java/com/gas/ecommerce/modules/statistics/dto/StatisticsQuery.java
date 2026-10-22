package com.gas.ecommerce.modules.statistics.dto;

import com.gas.ecommerce.common.result.PageRequest;
import java.time.LocalDate;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

@Data @EqualsAndHashCode(callSuper=true)
public class StatisticsQuery extends PageRequest {
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate startDate;
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate endDate;
    @Min(1) @Max(100) private int limit=10;
}
