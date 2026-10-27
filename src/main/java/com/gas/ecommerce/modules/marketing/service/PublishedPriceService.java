package com.gas.ecommerce.modules.marketing.service;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PublishedPriceService {
    private final NamedParameterJdbcTemplate db;
    public PublishedPriceService(NamedParameterJdbcTemplate db) { this.db = db; }
    public Map<Long, BigDecimal> quotes(Collection<Long> ids) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (!ids.isEmpty()) db.query("SELECT sku_id,price FROM marketing_sku_quote WHERE sku_id IN (:ids)"
                + " AND starts_at<=CURRENT_TIMESTAMP AND ends_at>CURRENT_TIMESTAMP", Map.of("ids", ids),
                rs -> { result.put(rs.getLong(1), rs.getBigDecimal(2)); });
        return result;
    }
}
