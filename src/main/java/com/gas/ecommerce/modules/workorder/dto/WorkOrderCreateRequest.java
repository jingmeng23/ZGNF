package com.gas.ecommerce.modules.workorder.dto;
public record WorkOrderCreateRequest(String type, String title, String description, Long storeId) { }
