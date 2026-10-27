package com.gas.ecommerce.modules.mall.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.ecommerce.modules.mall.vo.MallViews.StableDetail;
import com.gas.ecommerce.modules.product.service.CatalogReadService;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductDetailCacheServiceTests {
    final CatalogReadService catalog=mock(CatalogReadService.class);
    final StringRedisTemplate redis=mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked") final ValueOperations<String,String> values=mock(ValueOperations.class);
    final ObjectMapper json=new ObjectMapper();
    final Map<String,String> shared=new ConcurrentHashMap<>();
    final List<ProductDetailCacheService> instances=new ArrayList<>();
    final StableDetail sample=new StableDetail("产品",null,null,1L,null,List.of(),"介绍",List.of(),List.of(),null,Map.of());

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(call->shared.get(call.getArgument(0)));
        when(values.setIfAbsent(anyString(),anyString(),any(Duration.class)))
                .thenAnswer(call->shared.putIfAbsent(call.getArgument(0),call.getArgument(1))==null);
        // Simulate the Redis Lua operations atomically, including ownership and generation rejection.
        doAnswer(call->{
            RedisScript<?> script=call.getArgument(0);List<String> keys=call.getArgument(1);
            Object[] args=Arrays.copyOfRange(call.getArguments(),2,call.getArguments().length);
            String source=script.getScriptAsString();
            synchronized(shared) {
                if(script.getResultType()==String.class) return shared.computeIfAbsent(keys.getFirst(),k->args[0].toString());
                if(source.contains("KEYS[3]")) {
                    if(!Objects.equals(shared.get(keys.get(1)),args[0])||!Objects.equals(shared.get(keys.get(2)),args[3])) return 0L;
                    shared.put(keys.getFirst(),args[1].toString());return 1L;
                }
                if(source.contains("KEYS[2]")) {shared.put(keys.getFirst(),args[0].toString());shared.remove(keys.get(1));return 1L;}
                if(Objects.equals(shared.get(keys.getFirst()),args[0])) {shared.remove(keys.getFirst());return 1L;}
                return 0L;
            }
        }).when(redis).execute(any(RedisScript.class),anyList(),any(Object[].class));
    }
    ProductDetailCacheService service() {
        var service=new ProductDetailCacheService(catalog,redis,mock(JdbcTemplate.class),json,20,Duration.ofSeconds(10),Duration.ofSeconds(30),
                Duration.ZERO,Duration.ofSeconds(10),Duration.ofMillis(500),Duration.ofSeconds(2),Duration.ofSeconds(5),4096);
        instances.add(service);return service;
    }
    @AfterEach void close() {instances.forEach(ProductDetailCacheService::close);}

    @Test void negativeCacheAndL1SkipRepeatedOriginReads() {
        when(catalog.stableDetail(99L)).thenReturn(Optional.empty());var service=service();
        assertTrue(service.get(99L).isEmpty());assertTrue(service.get(99L).isEmpty());
        verify(catalog,times(1)).stableDetail(99L);
        assertTrue(shared.containsKey(ProductDetailCacheService.key(99L)));
    }
    @Test void l2HitDoesNotReadOriginAndLogicalExpiryIsPreserved() throws Exception {
        var entry=new ProductDetailCacheService.Entry(false,System.currentTimeMillis()+30000,System.currentTimeMillis()+35000,sample);
        shared.put(ProductDetailCacheService.key(1L),json.writeValueAsString(entry));
        var service=service();assertEquals("产品",service.get(1L).orElseThrow().name());
        for(int i=0;i<50;i++) service.get(1L);
        verifyNoInteractions(catalog);assertEquals(50,service.localStats().hitCount());
    }
    @Test void twoInstancesAndConcurrentColdReadersOnlyLoadOnce() throws Exception {
        when(catalog.stableDetail(1L)).thenAnswer(call->{Thread.sleep(60);return Optional.of(sample);});
        var first=service();var second=service();
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Optional<StableDetail>>> calls=new ArrayList<>();
            for(int i=0;i<40;i++) {var instance=i%2==0?first:second;calls.add(workers.submit(()->instance.get(1L)));}
            for(var call:calls) assertEquals("产品",call.get(3,TimeUnit.SECONDS).orElseThrow().name());
        }
        verify(catalog,times(1)).stableDetail(1L);
    }
    @Test void logicalExpiryReturnsStaleAndOnlyOneAsyncRebuild() throws Exception {
        long now=System.currentTimeMillis();shared.put(ProductDetailCacheService.key(1L),json.writeValueAsString(new ProductDetailCacheService.Entry(false,now-10,now+4000,sample)));
        CountDownLatch started=new CountDownLatch(1),finish=new CountDownLatch(1);
        when(catalog.stableDetail(1L)).thenAnswer(call->{started.countDown();finish.await(2,TimeUnit.SECONDS);return Optional.of(sample);});
        var service=service();assertEquals("产品",service.get(1L).orElseThrow().name());assertTrue(started.await(1,TimeUnit.SECONDS));
        for(int i=0;i<30;i++) assertTrue(service.get(1L).isPresent());
        verify(catalog,times(1)).stableDetail(1L);finish.countDown();
    }
    @Test void missingLockWaitTimesOutWithoutDBFallback() {
        shared.put(ProductDetailCacheService.lockKey(1L),"other-node");
        assertThrows(ProductDetailCacheService.Unavailable.class,()->service().get(1L));verifyNoInteractions(catalog);
    }
    @Test void redisOutageFailsClosedForColdKeyAndDoesNotQueryDB() {
        when(values.get(anyString())).thenThrow(new IllegalStateException("offline"));
        assertThrows(ProductDetailCacheService.Unavailable.class,()->service().get(1L));verifyNoInteractions(catalog);
    }
    @Test void failedOriginIsNotCachedAsMissing() {
        when(catalog.stableDetail(1L)).thenThrow(new IllegalStateException("database offline"));
        assertThrows(ProductDetailCacheService.Unavailable.class,()->service().get(1L));
        assertFalse(shared.containsKey(ProductDetailCacheService.key(1L)));
    }
    @Test void invalidationRejectsAnInflightOlderReader() throws Exception {
        CountDownLatch started=new CountDownLatch(1),finish=new CountDownLatch(1);
        when(catalog.stableDetail(1L)).thenAnswer(call->{started.countDown();finish.await(2,TimeUnit.SECONDS);return Optional.of(sample);});
        var reader=service();var writer=service();
        try(var worker=Executors.newVirtualThreadPerTaskExecutor()) {
            var call=worker.submit(()->reader.get(1L));assertTrue(started.await(1,TimeUnit.SECONDS));
            writer.invalidateId(1L);finish.countDown();
            assertThrows(ExecutionException.class,()->call.get(2,TimeUnit.SECONDS));
            assertFalse(shared.containsKey(ProductDetailCacheService.key(1L)));
        }
    }
    @Test void expiredLockOwnerCannotPublishOrDeleteNewOwnersLock() throws Exception {
        when(catalog.stableDetail(1L)).thenAnswer(call->{shared.put(ProductDetailCacheService.lockKey(1L),"new-owner");return Optional.of(sample);});
        assertThrows(ProductDetailCacheService.Unavailable.class,()->service().get(1L));
        assertFalse(shared.containsKey(ProductDetailCacheService.key(1L)));
        assertEquals("new-owner",shared.get(ProductDetailCacheService.lockKey(1L)));
    }
}
