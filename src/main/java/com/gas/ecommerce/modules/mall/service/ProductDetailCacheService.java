package com.gas.ecommerce.modules.mall.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.*;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.gas.ecommerce.modules.mall.vo.MallViews.StableDetail;
import com.gas.ecommerce.modules.product.service.CatalogReadService;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

/** Stable content only: live prices, inventory and publication checks are never cached here. */
@Service
public class ProductDetailCacheService {
    public static final class Unavailable extends RuntimeException {
        public Unavailable() {super("商品详情服务繁忙，请稍后重试");}
    }
    public record Entry(boolean missing,long freshUntil,long hardUntil,StableDetail value) { }
    private record Local(Entry entry,int bytes) { }
    private static final long MEMORY_BUDGET=64L*1024*1024;
    private static final String GEN_SCRIPT="local v=redis.call('get',KEYS[1]); if not v then redis.call('set',KEYS[1],ARGV[1],'PX',ARGV[2]);v=ARGV[1] end;return v";
    private static final String WRITE_SCRIPT="if redis.call('get',KEYS[2]) ~= ARGV[1] or redis.call('get',KEYS[3]) ~= ARGV[4] then return 0 end;redis.call('set',KEYS[1],ARGV[2],'PX',ARGV[3]);return 1";
    private static final String RELEASE_SCRIPT="if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) end;return 0";
    private static final String INVALIDATE_SCRIPT="redis.call('set',KEYS[1],ARGV[1],'PX',ARGV[2]);redis.call('del',KEYS[2]);return 1";
    private final CatalogReadService catalog;
    private final StringRedisTemplate redis;
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final Cache<Long,Local> l1;
    private final Cache<Long,AtomicInteger> accesses=Caffeine.newBuilder().maximumSize(10000).expireAfterWrite(Duration.ofSeconds(10)).build();
    private final ReentrantLock[] stripes=new ReentrantLock[256];
    private final AtomicLongArray localEpochs=new AtomicLongArray(256);
    private final Set<Long> refreshing=ConcurrentHashMap.newKeySet();
    private final Semaphore originSlots=new Semaphore(8);
    private final ThreadPoolExecutor refresh=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),
            Thread.ofPlatform().daemon().name("detail-refresh-",0).factory(),new ThreadPoolExecutor.AbortPolicy());
    private final AtomicInteger redisFailures=new AtomicInteger(),originFailures=new AtomicInteger();
    private final AtomicLong redisOpenUntil=new AtomicLong(),originOpenUntil=new AtomicLong();
    private final long ttl,jitter,lease,wait,negative,grace,maxBytes;

    public ProductDetailCacheService(CatalogReadService catalog,StringRedisTemplate redis,JdbcTemplate db,ObjectMapper json,
            @Value("${app.mall.detail-cache.l1-max-size:5000}") long maxSize,
            @Value("${app.mall.detail-cache.l1-ttl:PT20S}") Duration l1Ttl,
            @Value("${app.mall.detail-cache.redis-ttl:PT10M}") Duration redisTtl,
            @Value("${app.mall.detail-cache.jitter:PT2M}") Duration jitter,
            @Value("${app.mall.detail-cache.lock-lease:PT10S}") Duration lockLease,
            @Value("${app.mall.detail-cache.wait-timeout:PT2S}") Duration wait,
            @Value("${app.mall.detail-cache.negative-ttl:PT20S}") Duration negativeTtl,
            @Value("${app.mall.detail-cache.stale-grace:PT30S}") Duration staleGrace,
            @Value("${app.mall.detail-cache.max-detail-bytes:524288}") long maxBytes) {
        if(maxSize<1||maxBytes<1024||bad(l1Ttl)||bad(redisTtl)||jitter.isNegative()||bad(lockLease)||bad(wait)||bad(negativeTtl)||bad(staleGrace)
                ||lockLease.compareTo(Duration.ofHours(1))>=0||wait.compareTo(lockLease)>=0)
            throw new IllegalArgumentException("Invalid product detail cache settings");
        this.catalog=catalog;this.redis=redis;this.db=db;this.json=json;this.ttl=redisTtl.toMillis();this.jitter=jitter.toMillis();
        this.lease=lockLease.toMillis();this.wait=wait.toMillis();this.negative=negativeTtl.toMillis();this.grace=staleGrace.toMillis();this.maxBytes=maxBytes;
        int minimumWeight=(int)Math.min(Integer.MAX_VALUE,Math.max(1,(MEMORY_BUDGET+maxSize-1)/maxSize));
        this.l1=Caffeine.newBuilder().maximumWeight(MEMORY_BUDGET)
                .weigher((Long id,Local entry)->Math.max(minimumWeight,entry.bytes()))
                .expireAfterWrite(l1Ttl).recordStats().build();
        Arrays.setAll(stripes,i->new ReentrantLock());
    }
    private static boolean bad(Duration value) {return value.toMillis()<1;}

    public Optional<StableDetail> get(Long id) {
        if(id==null||id<=0) return Optional.empty();
        if(accesses.get(id,k->new AtomicInteger()).incrementAndGet()==1000)
            LoggerFactory.getLogger(getClass()).warn("Hot detail detected: productId={}, threshold=1000/10s per instance",id);
        Local local=l1.getIfPresent(id);
        Entry entry=local==null?null:local.entry();
        if(entry==null||entry.hardUntil()<=now()) entry=readShared(id);
        if(entry!=null&&entry.hardUntil()>now()) {
            if(entry.freshUntil()<=now()) {
                // Missing values never have a stale grace period.
                if(entry.missing()) return cold(id);
                scheduleRefresh(id);
            }
            return optional(entry);
        }
        return cold(id);
    }

    private Entry readShared(Long id) {
        long epoch=localEpochs.get(stripeIndex(id));
        String raw=redisCall(()->redis.opsForValue().get(key(id)));
        if(raw==null) return null;
        try {
            Entry entry=json.readValue(raw,Entry.class);
            if(!valid(entry)||entry.hardUntil()<=now()) return null;
            putLocal(id,entry,raw,epoch);return entry;
        } catch(Exception e) {
            // Corrupt cache data is a miss, not a cached 404 or an origin failure.
            LoggerFactory.getLogger(getClass()).warn("Invalid detail cache entry: {}",id);return null;
        }
    }

    private Optional<StableDetail> cold(Long id) {
        ReentrantLock localGate=stripe(id);
        boolean acquired;
        try {acquired=localGate.tryLock(wait,TimeUnit.MILLISECONDS);}
        catch(InterruptedException e) {Thread.currentThread().interrupt();throw new Unavailable();}
        if(!acquired) throw new Unavailable();
        try {
            Local local=l1.getIfPresent(id);
            if(local!=null&&local.entry().hardUntil()>now()) return optional(local.entry());
            Entry entry=readShared(id);
            if(entry!=null&&entry.hardUntil()>now()) return optional(entry);
            String token=UUID.randomUUID().toString();
            if(takeLock(id,token)) {
                try {
                    // Double-check after acquiring the distributed lease.
                    entry=readShared(id);
                    if(entry!=null&&entry.freshUntil()>now()) return optional(entry);
                    return loadAndPublish(id,token);
                } finally {release(id,token);}
            }
            // A competing JVM owns the rebuild: wait boundedly; never fall through to DB.
            long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(wait);
            do {
                try {Thread.sleep(ThreadLocalRandom.current().nextLong(15,36));}
                catch(InterruptedException e) {Thread.currentThread().interrupt();throw new Unavailable();}
                entry=readShared(id);
                if(entry!=null&&entry.hardUntil()>now()) return optional(entry);
            } while(System.nanoTime()<deadline);
            throw new Unavailable();
        } finally {localGate.unlock();}
    }

    private void scheduleRefresh(Long id) {
        if(!refreshing.add(id)) return;
        // Gate BEFORE Redis: requests to one hot local key do not all perform SET NX.
        ReentrantLock gate=stripe(id);
        if(!gate.tryLock()) {refreshing.remove(id);return;}
        boolean submitted=false;
        try {
            String token=UUID.randomUUID().toString();
            if(!takeLock(id,token)) return;
            try {
                refresh.execute(()->{
                    try {loadAndPublish(id,token);}
                    catch(Exception e) {LoggerFactory.getLogger(getClass()).warn("Detail refresh failed for {}: {}",id,e.toString());}
                    finally {release(id,token);refreshing.remove(id);}
                });
                submitted=true;
            } catch(RejectedExecutionException e) {release(id,token);}
        } catch(Unavailable ignored) {
            // Existing nonnegative static content is bounded by its embedded hardUntil.
        } finally {gate.unlock();if(!submitted) refreshing.remove(id);}
    }

    private Optional<StableDetail> loadAndPublish(Long id,String token) {
        long epoch=localEpochs.get(stripeIndex(id));
        String generation=redisCall(()->redis.execute(new DefaultRedisScript<>(GEN_SCRIPT,String.class),
                List.of(fenceKey(id)),UUID.randomUUID().toString(),"3600000"));
        if(generation==null) throw new Unavailable();
        if(!token.equals(redisCall(()->redis.opsForValue().get(lockKey(id))))) throw new Unavailable();
        if(originOpenUntil.get()>now()||!originSlots.tryAcquire()) throw new Unavailable();
        Optional<StableDetail> value;
        try {
            value=catalog.stableDetail(id);originFailures.set(0);
        } catch(Exception e) {
            if(originFailures.incrementAndGet()>=5) originOpenUntil.set(now()+10000);
            throw new Unavailable();
        } finally {originSlots.release();}
        long fresh=value.isPresent()?ttl+ThreadLocalRandom.current().nextLong(jitter+1):negative+ThreadLocalRandom.current().nextLong(5001);
        Entry entry=new Entry(value.isEmpty(),now()+fresh,now()+fresh+(value.isPresent()?grace:0),value.orElse(null));
        try {
            String raw=json.writeValueAsString(entry);
            if(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>maxBytes) return value;
            Long written=redisCall(()->redis.execute(new DefaultRedisScript<>(WRITE_SCRIPT,Long.class),
                    List.of(key(id),fenceKey(id),lockKey(id)),generation,raw,String.valueOf(Math.max(1,entry.hardUntil()-now())),token));
            // Lease or generation changed? Discard old results, including the response to the triggering reader.
            if(!Long.valueOf(1).equals(written)) throw new Unavailable();
            putLocal(id,entry,raw,epoch);return value;
        } catch(Unavailable e) {throw e;}
        catch(Exception e) {throw new Unavailable();}
    }

    /** Synchronous AFTER_COMMIT listener, not an atomic DB+Redis transaction or durable message. */
    @TransactionalEventListener
    public void invalidate(CatalogReadService.ProductDetailChanged event) {
        invalidateId(event.productId());
    }
    void invalidateId(Long id) {
        // Same stripe serializes local fills and invalidation; remote L1 remains bounded by 20s TTL.
        ReentrantLock gate=stripe(id);gate.lock();
        try {
            localEpochs.incrementAndGet(stripeIndex(id));
            l1.invalidate(id);
            redisCall(()->redis.execute(new DefaultRedisScript<>(INVALIDATE_SCRIPT,Long.class),
                    List.of(fenceKey(id),key(id)),UUID.randomUUID().toString(),"3600000"));
            db.update("DELETE FROM mall_detail_invalidation_retry WHERE product_id=?", id);
        } catch(Unavailable e) {
            db.update("INSERT INTO mall_detail_invalidation_retry(product_id,requested_at) VALUES(?,CURRENT_TIMESTAMP) "
                    + "ON DUPLICATE KEY UPDATE requested_at=VALUES(requested_at)", id);
            LoggerFactory.getLogger(getClass()).warn("Detail invalidation queued for retry: {}",id);
        }
        finally {gate.unlock();}
    }
    @Scheduled(fixedDelayString="${app.mall.detail-cache.invalidation-retry-ms:5000}")
    public void retryInvalidations() {
        List<Long> ids = db.query("SELECT product_id FROM mall_detail_invalidation_retry ORDER BY requested_at LIMIT 100",
                (r,n)->r.getLong(1));
        for (Long id : ids) invalidateId(id);
    }
    private void putLocal(Long id,Entry entry,String raw,long epoch) {
        ReentrantLock gate=stripe(id);gate.lock();
        try {
            if(localEpochs.get(stripeIndex(id))==epoch)
                l1.put(id,new Local(entry,(int)Math.min(Integer.MAX_VALUE,256L+raw.length()*2L)));
        } finally {gate.unlock();}
    }
    private boolean takeLock(Long id,String token) {
        return Boolean.TRUE.equals(redisCall(()->redis.opsForValue().setIfAbsent(lockKey(id),token,Duration.ofMillis(lease))));
    }
    private void release(Long id,String token) {
        try {redisCall(()->redis.execute(new DefaultRedisScript<>(RELEASE_SCRIPT,Long.class),List.of(lockKey(id)),token));}
        catch(Unavailable ignored) { /* finite lease is the last-resort cleanup */ }
    }
    private <T> T redisCall(Supplier<T> action) {
        if(redisOpenUntil.get()>now()) throw new Unavailable();
        try {T value=action.get();redisFailures.set(0);return value;}
        catch(Exception e) {if(redisFailures.incrementAndGet()>=3) redisOpenUntil.set(now()+5000);throw new Unavailable();}
    }
    private static boolean valid(Entry entry) {return entry!=null&&(entry.missing()?entry.value()==null:entry.value()!=null)&&entry.hardUntil()>=entry.freshUntil();}
    private static Optional<StableDetail> optional(Entry entry) {return entry.missing()?Optional.empty():Optional.of(entry.value());}
    private int stripeIndex(Long id) {return Math.floorMod(id.hashCode(),stripes.length);}
    private ReentrantLock stripe(Long id) {return stripes[stripeIndex(id)];}
    private static long now() {return System.currentTimeMillis();}
    // Redis Cluster hash tags keep all per-product Lua keys in the same slot.
    static String key(Long id) {return "mall:detail:v2:{"+id+"}:value";}
    static String lockKey(Long id) {return "mall:detail:v2:{"+id+"}:lock";}
    static String fenceKey(Long id) {return "mall:detail:v2:{"+id+"}:generation";}
    public CacheStats localStats() {return l1.stats();}
    @PreDestroy public void close() {refresh.shutdownNow();}
}
