package com.gas.ecommerce.modules.product.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.ecommerce.modules.product.vo.CategoryTreeVO;
import com.gas.ecommerce.modules.product.vo.CategoryVO;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

/** C-end category projection; category ownership stays in the product catalog. */
@Service
public class CategoryTreeService {
    static final String CACHE_KEY = "mall:categories:tree:v1";
    private static final Logger log = LoggerFactory.getLogger(CategoryTreeService.class);
    private final ProductBrowseService catalog;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Duration ttl;

    public CategoryTreeService(ProductBrowseService catalog, StringRedisTemplate redis, ObjectMapper json,
            @Value("${app.mall.category-tree-cache-ttl:PT5M}") Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("Category cache TTL must be positive");
        this.catalog = catalog; this.redis = redis; this.json = json; this.ttl = ttl;
    }

    public List<CategoryTreeVO> tree() {
        try {
            String cached = redis.opsForValue().get(CACHE_KEY);
            if (cached != null) return json.readValue(cached, new TypeReference<List<CategoryTreeVO>>() { });
        } catch (Exception e) {
            log.warn("Category tree cache unavailable or invalid; reading product catalog: {}", e.toString());
        }
        List<CategoryTreeVO> tree = build(catalog.categories());
        try {
            redis.opsForValue().set(CACHE_KEY, json.writeValueAsString(tree), ttl);
        } catch (Exception e) {
            log.warn("Could not cache category tree: {}", e.toString());
        }
        return tree;
    }

    static List<CategoryTreeVO> build(List<CategoryVO> categories) {
        Map<Long, List<CategoryTreeVO>> children = new HashMap<>();
        Map<Long, CategoryTreeVO> nodes = new HashMap<>();
        categories.stream().filter(c -> "active".equals(c.status()))
                .sorted(Comparator.comparing(CategoryVO::sort, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(CategoryVO::id))
                .forEach(c -> nodes.put(c.id(), new CategoryTreeVO(c.id(), c.parentId(), c.name(), c.sort(),
                        c.status(), children.computeIfAbsent(c.id(), ignored -> new ArrayList<>()))));
        // Only traverse root-connected nodes: orphans and cycles are never exposed.
        List<CategoryTreeVO> roots = new ArrayList<>();
        categories.stream().map(c -> nodes.get(c.id())).filter(Objects::nonNull)
                .sorted(Comparator.comparing(CategoryTreeVO::sort, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(CategoryTreeVO::id))
                .forEach(n -> {
                    if (Long.valueOf(0).equals(n.parentId())) roots.add(n);
                    else if (nodes.containsKey(n.parentId())) children.get(n.parentId()).add(n);
                });
        return roots;
    }

    public record CatalogChanged() { }

    @TransactionalEventListener
    public void invalidate(CatalogChanged event) {
        try { redis.delete(CACHE_KEY); }
        catch (Exception e) { log.warn("Could not invalidate category tree; TTL bounds staleness: {}", e.toString()); }
    }
}
