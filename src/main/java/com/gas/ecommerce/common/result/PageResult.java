package com.gas.ecommerce.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;

public record PageResult<T>(List<T> list, long total, long page, long size, long pages) {
    public static <T> PageResult<T> from(IPage<T> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }
}
