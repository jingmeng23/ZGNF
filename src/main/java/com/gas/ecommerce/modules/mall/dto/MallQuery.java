package com.gas.ecommerce.modules.mall.dto;

import com.gas.ecommerce.common.result.PageRequest;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper = true)
public class MallQuery extends PageRequest {
    @Size(max=100) private String keyword;
    @Positive private Long categoryId;
    @Positive private Long brandId;
    @DecimalMin("0") private BigDecimal minPrice;
    @DecimalMin("0") private BigDecimal maxPrice;
    private boolean inStock;
    @Size(max=20) private Map<@Size(max=64) String, @Size(max=100) String> attributes = new java.util.LinkedHashMap<>();
    public MallQuery() { setSort("comprehensive"); }
    @Override @Min(1) @Max(1000) public int getPage() { return super.getPage(); }
    @Override @NotNull @Pattern(regexp="comprehensive|sales|price|newest|createTime")
    public String getSort() {return super.getSort();}
    @AssertTrue(message="最低价格不能大于最高价格")
    public boolean isPriceRangeValid() { return minPrice == null || maxPrice == null || minPrice.compareTo(maxPrice)<=0; }
}
