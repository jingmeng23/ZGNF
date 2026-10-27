package com.gas.ecommerce.modules.mall.service;

import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.common.result.PageRequest;
import com.gas.ecommerce.common.result.PageResult;
import com.gas.ecommerce.modules.product.service.CatalogReadService;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class MallPersonalService {
    private record HistoryChanged(Long userId, Long productId, boolean clear, boolean remove) { }
    private static final String HISTORY_KEY = "mall:history:user:";
    private final JdbcTemplate db;
    private final CatalogReadService catalog;
    private final StringRedisTemplate redis;
    private final int capacity;

    public MallPersonalService(JdbcTemplate db, CatalogReadService catalog, StringRedisTemplate redis,
            @Value("${app.mall.history-capacity:200}") int capacity) {
        if (capacity < 1 || capacity > 10000) throw new IllegalArgumentException("Invalid history capacity");
        this.db = db;
        this.catalog = catalog;
        this.redis = redis;
        this.capacity = capacity;
    }

    private void lock(Long user) {
        if (user == null || db.query("SELECT id FROM `user` WHERE id=? AND is_deleted=0 AND status='active' FOR UPDATE",
                (r, n) -> r.getLong(1), user).isEmpty()) throw new BusinessException("用户不可用");
    }

    @Transactional
    public void favorite(Long user, Long product) {
        lock(user);
        catalog.card(product).orElseThrow(() -> new BusinessException("商品不存在"));
        try {
            db.update("INSERT INTO mall_favorite(user_id,product_id) VALUES(?,?)", user, product);
        } catch (DuplicateKeyException duplicate) {
            // The composite primary key makes concurrent retries idempotent.
        }
    }

    public boolean isFavorite(Long user, Long product) {
        return db.queryForObject("SELECT COUNT(*) FROM mall_favorite WHERE user_id=? AND product_id=?", Long.class,
                user, product) > 0;
    }

    public void unfavorite(Long user, Long product) {
        db.update("DELETE FROM mall_favorite WHERE user_id=? AND product_id=?", user, product);
    }

    @Transactional
    public void visit(Long user, Long product) {
        lock(user);
        catalog.card(product).orElseThrow(() -> new BusinessException("商品不存在"));
        db.update("DELETE FROM mall_history WHERE user_id=? AND product_id=?", user, product);
        db.update("INSERT INTO mall_history(user_id,product_id) VALUES(?,?)", user, product);
        List<Long> overflow = db.query("SELECT product_id FROM mall_history WHERE user_id=? "
                + "ORDER BY viewed_at DESC,product_id DESC LIMIT 10000 OFFSET ?", (r, n) -> r.getLong(1), user, capacity);
        for (Long id : overflow) db.update("DELETE FROM mall_history WHERE user_id=? AND product_id=?", user, id);
        eventPublisher.publishEvent(new HistoryChanged(user, product, false, false));
    }

    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @org.springframework.beans.factory.annotation.Autowired
    void setEventPublisher(org.springframework.context.ApplicationEventPublisher publisher) { this.eventPublisher = publisher; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void updateHistoryAfterCommit(HistoryChanged event) {
        if (event.clear()) {
            try { redis.delete(historyKey(event.userId())); } catch (RedisConnectionFailureException ignored) { }
        } else if (event.remove()) {
            try { redis.opsForZSet().remove(historyKey(event.userId()), event.productId().toString()); }
            catch (RedisConnectionFailureException ignored) { }
        } else {
            updateHistoryCache(event.userId(), event.productId());
        }
    }

    private String historyKey(Long user) { return HISTORY_KEY + user; }

    private void updateHistoryCache(Long user, Long product) {
        try {
            String key = historyKey(user);
            redis.opsForZSet().add(key, product.toString(), System.currentTimeMillis() * 1000.0 + System.nanoTime() % 1000);
            redis.opsForZSet().removeRange(key, 0, -(capacity + 1L));
            List<Long> activeIds = db.query("SELECT product_id FROM mall_history WHERE user_id=?", (r, n) -> r.getLong(1), user);
            Set<String> active = activeIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.toSet());
            Set<String> cached = redis.opsForZSet().range(key, 0, -1);
            if (cached != null) for (String id : cached) if (!active.contains(id)) redis.opsForZSet().remove(key, id);
            redis.expire(key, java.time.Duration.ofDays(180));
        } catch (RedisConnectionFailureException ignored) {
            // The database remains authoritative and serves requests while Redis is unavailable.
        }
    }

    private List<Long> historyPage(Long user, PageRequest q) {
        String key = historyKey(user);
        try {
            Set<String> ids = redis.opsForZSet().reverseRange(key,
                    (long) (q.getPage() - 1) * q.getSize(), (long) q.getPage() * q.getSize() - 1);
            if (ids != null && !ids.isEmpty()) return ids.stream().map(Long::valueOf).toList();
            // An empty result can also mean an unbuilt cache, so verify the database below.
        } catch (RedisConnectionFailureException ignored) {
            // Fall through to the database query.
        }
        List<Long> ids = db.query("SELECT product_id FROM mall_history WHERE user_id=? "
                + "ORDER BY viewed_at DESC,product_id DESC LIMIT ? OFFSET ?", (r, n) -> r.getLong(1), user,
                q.getSize(), ((long) q.getPage() - 1) * q.getSize());
        if (!ids.isEmpty()) rebuildHistoryCache(user);
        return ids;
    }

    private void rebuildHistoryCache(Long user) {
        try {
            String key = historyKey(user);
            redis.delete(key);
            db.query("SELECT product_id, viewed_at FROM mall_history WHERE user_id=? ORDER BY viewed_at,product_id",
                    (org.springframework.jdbc.core.RowCallbackHandler) r -> redis.opsForZSet().add(key,
                            Long.toString(r.getLong(1)), r.getTimestamp(2).getTime()), user);
            redis.expire(key, java.time.Duration.ofDays(180));
        } catch (RedisConnectionFailureException ignored) {
            // Database reads remain available if cache rebuild cannot reach Redis.
        }
    }

    @Transactional
    public void clearHistory(Long user) {
        db.update("DELETE FROM mall_history WHERE user_id=?", user);
        eventPublisher.publishEvent(new HistoryChanged(user, null, true, false));
    }

    @Transactional
    public void deleteHistory(Long user, Long product) {
        db.update("DELETE FROM mall_history WHERE user_id=? AND product_id=?", user, product);
        eventPublisher.publishEvent(new HistoryChanged(user, product, false, true));
    }

    public PageResult<Map<String, Object>> list(Long user, PageRequest q, boolean history) {
        String table = history ? "mall_history" : "mall_favorite";
        String time = history ? "viewed_at" : "created_at";
        long total = db.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id=?", Long.class, user);
        List<Map<String, Object>> entries;
        List<Long> ids = history ? historyPage(user, q) : null;
        if (history && ids.isEmpty()) entries = List.of();
        else {
            String order = history ? " FIELD(product_id," + String.join(",", ids.stream().map(String::valueOf).toList()) + ")" : time + " DESC,product_id DESC";
            String sql = "SELECT product_id," + time + " FROM " + table + " WHERE user_id=?"
                    + (history ? " AND product_id IN (" + String.join(",", Collections.nCopies(ids.size(), "?")) + ")" : "")
                    + " ORDER BY " + order + (history ? "" : " LIMIT ? OFFSET ?");
            List<Object> args = new ArrayList<>(); args.add(user);
            if (history) args.addAll(ids); else { args.add(q.getSize()); args.add(((long) q.getPage() - 1) * q.getSize()); }
            List<Map<String, Object>> rows = db.query(sql, (r, n) -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("productId", r.getLong(1)); entry.put("time", r.getTimestamp(2).toLocalDateTime());
                return entry;
            }, args.toArray());
            List<Long> productIds = rows.stream().map(row -> (Long) row.get("productId")).toList();
            Map<Long, com.gas.ecommerce.modules.mall.vo.MallViews.Card> cards = catalog.cards(productIds);
            for (Map<String, Object> row : rows) {
                Object productId = row.get("productId");
                var card = cards.get((Long) productId);
                row.put("product", card); row.put("unavailable", card == null);
            }
            entries = rows;
        }
        return new PageResult<>(entries, total, q.getPage(), q.getSize(), (total + q.getSize() - 1) / q.getSize());
    }
}
