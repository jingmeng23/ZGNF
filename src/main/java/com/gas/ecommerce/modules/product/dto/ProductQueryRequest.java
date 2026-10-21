package com.gas.ecommerce.modules.product.dto;
public record ProductQueryRequest(Integer page, Integer size, Long categoryId, String keyword, String sort, String order) { }
