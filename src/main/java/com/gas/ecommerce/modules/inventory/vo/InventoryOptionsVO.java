package com.gas.ecommerce.modules.inventory.vo;

import java.util.List;

/** Inventory selectors expose only identifiers and names under inventory:read. */
public record InventoryOptionsVO(List<Option> products) {
    public record Option(Long id, String name) { }
}
