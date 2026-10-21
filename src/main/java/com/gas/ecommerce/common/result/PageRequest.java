package com.gas.ecommerce.common.result;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PageRequest {
    @Min(1) private int page = 1;
    @Min(1) @Max(100) private int size = 20;
    private String sort = "createTime";
    @Pattern(regexp = "asc|desc") private String order = "desc";
    /** Map a client sort key through an explicit allowlist before using it in SQL. */
    public String resolveSort(java.util.Map<String, String> allowedColumns) {
        String column = allowedColumns.get(sort);
        if (column == null) throw new com.gas.ecommerce.common.exception.BusinessException("不支持的排序字段");
        return column;
    }
}
