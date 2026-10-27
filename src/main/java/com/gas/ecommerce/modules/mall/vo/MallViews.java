package com.gas.ecommerce.modules.mall.vo;

import java.math.BigDecimal;
import java.util.*;

public final class MallViews {
    private MallViews() { }
    public record Sku(Long id, String code, String name, String imageUrl, Map<String,String> specs,
            BigDecimal price, BigDecimal originalPrice, long availableStock, boolean selectable, String reason) { }
    public record Option(String value, boolean disabled) { }
    public record Dimension(String name, List<Option> options) { }
    public record Matrix(List<Dimension> dimensions, List<Sku> skus, Long selectedSkuId) { }
    public record Review(long count, long positiveCount, BigDecimal averageRating) { }
    public record Card(Long id, Long categoryId, Long brandId, String name, String subtitle, String coverUrl,
            BigDecimal price, BigDecimal originalPrice, long sales, long availableStock,
            String status, String statusMessage, boolean purchasable) { }
    public record Detail(Card product, List<String> mainImages, String description, List<String> detailImages,
            List<String> servicePromises, Review review, Map<String,List<String>> attributes, Matrix matrix) { }
    public record StableDetail(String name,String subtitle,String coverUrl,Long categoryId,Long brandId,
            List<String> mainImages,String description,List<String> detailImages,List<String> servicePromises,
            Review review,Map<String,List<String>> attributes) { }
}
