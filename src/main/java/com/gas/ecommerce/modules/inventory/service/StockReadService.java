package com.gas.ecommerce.modules.inventory.service;

import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** Availability belongs to inventory; consumers must not repeat balance arithmetic. */
@Service
public class StockReadService {
    private final NamedParameterJdbcTemplate db;
    public StockReadService(NamedParameterJdbcTemplate db) { this.db = db; }
    public Map<Long, Long> skus(Collection<Long> ids) { return read(ids, true); }
    public Map<Long, Long> products(Collection<Long> ids) { return read(ids, false); }
    private Map<Long, Long> read(Collection<Long> ids, boolean sku) {
        Map<Long, Long> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        String key = sku ? "sku_id" : "product_id";
        String view = sku ? "sku_available_stock" : "product_available_stock";
        db.query("SELECT " + key + ", available FROM " + view + " WHERE " + key + " IN (:ids)", Map.of("ids", ids),
                rs -> { result.put(rs.getLong(1), rs.getLong(2)); });
        return result;
    }
}
