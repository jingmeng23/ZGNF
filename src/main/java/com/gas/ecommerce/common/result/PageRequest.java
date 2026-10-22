package com.gas.ecommerce.common.result;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
/**
 * 统一响应或分页数据类型，约定接口返回结构。
 */
public class PageRequest {
    @Min(1) private int page = 1;
    @Min(1) @Max(100) private int size = 20;
    private String sort = "createTime";
    @Pattern(regexp = "asc|desc") private String order = "desc";
    /** 将客户端排序字段映射到白名单中的数据库列名，避免把任意输入拼进 SQL。 */
    public String resolveSort(java.util.Map<String, String> allowedColumns) {
        String column = allowedColumns.get(sort);
        if (column == null) throw new com.gas.ecommerce.common.exception.BusinessException("不支持的排序字段");
        return column;
    }
}
