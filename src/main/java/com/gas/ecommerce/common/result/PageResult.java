package com.gas.ecommerce.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
/**
 * 统一响应和分页数据类型，约定接口返回的数据结构。
 */

public record PageResult<T>(List<T> list, long total, long page, long size, long pages) {
    public static <T> PageResult<T> from(IPage<T> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }
}
