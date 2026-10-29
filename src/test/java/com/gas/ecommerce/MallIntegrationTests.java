package com.gas.ecommerce;

import com.gas.ecommerce.common.security.JwtTokenProvider;
import com.gas.ecommerce.modules.mall.service.MallPersonalService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.gas.ecommerce.modules.mall.service.ProductDetailCacheService;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="app.mall.history-capacity=2")
@ActiveProfiles("test") @AutoConfigureMockMvc
@Sql(statements="DROP ALL OBJECTS")
@Sql(scripts={"/db/migration/V1.0__init_schema.sql","/db/migration/V1.12__product_details.sql","/db/migration/V1.13__mall_catalog.sql","/db/migration/V1.14__mall_cache_invalidation_retry.sql","/db/migration/V1.22__available_sku_display_price.sql"})
class MallIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired JwtTokenProvider tokens;
    @Autowired MallPersonalService personal;
    @MockitoBean StringRedisTemplate redis;
    @MockitoBean ProductDetailCacheService stableCache;
    @org.junit.jupiter.api.BeforeEach void configureDetailCache() {
        org.mockito.Mockito.when(stableCache.get(org.mockito.ArgumentMatchers.anyLong())).thenAnswer(invocation -> {
            Long id=invocation.getArgument(0);return catalogRead.stableDetail(id);
        });
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.ZSetOperations<String,String> zset = org.mockito.Mockito.mock(org.springframework.data.redis.core.ZSetOperations.class);
        org.mockito.Mockito.when(redis.opsForZSet()).thenReturn(zset);
        org.mockito.Mockito.when(zset.range(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyLong())).thenReturn(java.util.Set.of());
        org.mockito.Mockito.when(zset.reverseRange(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyLong())).thenReturn(null);
        org.mockito.Mockito.when(redis.hasKey(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
    }
    @Autowired com.gas.ecommerce.modules.product.service.CatalogReadService catalogRead;
    String auth(long user) {return "Bearer "+tokens.generateAccessToken(user,"user");}
    @BeforeEach void seed() {
        db.update("INSERT INTO `user`(id,nickname) VALUES(1,'a'),(2,'b')");
        db.update("INSERT INTO category(id,parent_id,name) VALUES(1,0,'家电'),(2,1,'灶具')");
        db.update("INSERT INTO brand(id,name) VALUES(1,'品牌甲'),(2,'品牌乙')");
        db.update("INSERT INTO product(id,category_id,brand_id,name,price,status,sales,description) VALUES"
                + "(1,2,1,'智能燃气灶',100,'on_sale',50,'商品介绍'),(2,2,2,'热水器',200,'off_sale',10,'详情'),"
                + "(3,1,1,'报警器',30,'on_sale',100,'详情'),(4,1,1,'预售灶具',90,'presale',0,'详情'),"
                + "(5,1,1,'新品',80,'scheduled',0,'详情'),(6,1,1,'草稿',80,'draft',0,'详情')");
        db.update("INSERT INTO product_sku(id,product_id,code,name,price,status) VALUES(11,1,'a','红色小号',100,'active'),(12,1,'b','蓝色大号',120,'active'),(13,1,'c','红色大号',110,'inactive')");
        db.update("INSERT INTO product_sku_spec VALUES(11,'颜色','红'),(11,'尺寸','小'),(12,'颜色','蓝'),(12,'尺寸','大'),(13,'颜色','红'),(13,'尺寸','大')");
        db.update("INSERT INTO sku_inventory VALUES(11,0,5,1),(12,0,0,0),(13,0,10,0)");
        db.update("INSERT INTO inventory(product_id,quantity) VALUES(3,5)");
        db.update("INSERT INTO marketing_sku_quote VALUES(11,75,DATEADD('DAY',-1,CURRENT_TIMESTAMP),DATEADD('DAY',1,CURRENT_TIMESTAMP))");
        db.update("INSERT INTO marketing_sku_quote VALUES(12,66,DATEADD('DAY',-1,CURRENT_TIMESTAMP),DATEADD('DAY',1,CURRENT_TIMESTAMP))");
        db.update("INSERT INTO product_attribute VALUES(1,'气源','天然气'),(3,'气源','通用')");
        db.update("INSERT INTO product_media(product_id,url,sort) VALUES(1,'/main.jpg',1)");
        db.update("INSERT INTO product_promise VALUES(1,'官方质保')");
        db.update("INSERT INTO product_review_summary VALUES(1,10,9,4.8)");
        db.update("INSERT INTO search_hotword(word,search_count) VALUES('燃气灶',100)");
    }
    @Test void filtersBeforePaginationUsingPublishedPriceAndInventory() throws Exception {
        mvc.perform(get("/api/v1/mall/products").param("categoryId","1").param("brandId","1").param("minPrice","70").param("maxPrice","80").param("inStock","true").param("attributes[气源]","天然气").param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].price").value(75)).andExpect(jsonPath("$.data.list[0].availableStock").value(4));
        mvc.perform(get("/api/v1/mall/products").param("sort","sales").param("size","1"))
                .andExpect(jsonPath("$.data.list[0].name").value("报警器")).andExpect(jsonPath("$.data.total").value(4));
        mvc.perform(get("/api/v1/mall/products").param("minPrice","100").param("maxPrice","10")).andExpect(status().isBadRequest());
    }
    @Test void displayPriceTracksStockAndFallsBackWhenSoldOutOrDisabled() throws Exception {
        mvc.perform(get("/api/v1/mall/products/1"))
                .andExpect(jsonPath("$.data.product.price").value(75));
        db.update("UPDATE sku_inventory SET quantity=3 WHERE sku_id=12");
        mvc.perform(get("/api/v1/mall/products/1"))
                .andExpect(jsonPath("$.data.product.price").value(66));
        db.update("UPDATE sku_inventory SET quantity=0 WHERE sku_id IN (11,12)");
        mvc.perform(get("/api/v1/mall/products/1"))
                .andExpect(jsonPath("$.data.product.price").value(66))
                .andExpect(jsonPath("$.data.product.status").value("sold_out"));
        mvc.perform(get("/api/v1/mall/products").param("inStock","true").param("minPrice","60").param("maxPrice","80"))
                .andExpect(jsonPath("$.data.total").value(0));
        db.update("UPDATE product_sku SET status='inactive' WHERE product_id=1");
        mvc.perform(get("/api/v1/mall/products/1"))
                .andExpect(jsonPath("$.data.product.price").value(100));
    }
    @Test void displayPriceUsesLiveQuotesAndIgnoresExpiredQuotes() throws Exception {
        db.update("UPDATE marketing_sku_quote SET price=72 WHERE sku_id=11");
        mvc.perform(get("/api/v1/mall/products").param("minPrice","72").param("maxPrice","72"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].price").value(72));
        db.update("UPDATE marketing_sku_quote SET ends_at=DATEADD('DAY',-1,CURRENT_TIMESTAMP) WHERE sku_id=11");
        mvc.perform(get("/api/v1/mall/products/1"))
                .andExpect(jsonPath("$.data.product.price").value(100));
    }
    @Test void detailContainsContentAndExplicitStates() throws Exception {
        mvc.perform(get("/api/v1/mall/products/1")).andExpect(jsonPath("$.data.mainImages[0]").value("/main.jpg"))
                .andExpect(jsonPath("$.data.servicePromises[0]").value("官方质保"))
                .andExpect(jsonPath("$.data.review.count").value(10)).andExpect(jsonPath("$.data.description").value("商品介绍"));
        mvc.perform(get("/api/v1/mall/products/2")).andExpect(jsonPath("$.data.product.status").value("off_sale"))
                .andExpect(jsonPath("$.data.product.statusMessage").value("商品已下架")).andExpect(jsonPath("$.data.product.purchasable").value(false));
        mvc.perform(get("/api/v1/mall/products/4")).andExpect(jsonPath("$.data.product.status").value("presale"));
        mvc.perform(get("/api/v1/mall/products/5")).andExpect(jsonPath("$.data.product.status").value("scheduled"));
        mvc.perform(get("/api/v1/mall/products/6")).andExpect(jsonPath("$.code").value(404001));
        db.update("UPDATE inventory SET quantity=0 WHERE product_id=3");
        mvc.perform(get("/api/v1/mall/products/3")).andExpect(jsonPath("$.data.product.status").value("sold_out"));
    }
    @Test void matrixDisablesMissingAndSoldOutCombinationsAndReadsLiveQuote() throws Exception {
        mvc.perform(get("/api/v1/mall/products/1/skus").param("selection","{\"颜色\":\"红\"}"))
                .andExpect(jsonPath("$.data.dimensions[0].options[0].value").value("大"))
                .andExpect(jsonPath("$.data.dimensions[0].options[0].disabled").value(true))
                .andExpect(jsonPath("$.data.skus[0].price").value(75)).andExpect(jsonPath("$.data.skus[1].selectable").value(false));
        db.update("UPDATE marketing_sku_quote SET price=66 WHERE sku_id=11");
        mvc.perform(get("/api/v1/mall/products/1/skus").param("selection","{\"颜色\":\"红\",\"尺寸\":\"小\"}"))
                .andExpect(jsonPath("$.data.selectedSkuId").value("11")).andExpect(jsonPath("$.data.skus[0].price").value(66));
    }
    @Test void favoritesAreIdempotentAndIsolated() throws Exception {
        mvc.perform(put("/api/v1/mall/me/favorites/1")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/mall/me/favorites/1").header("Authorization",auth(1))).andExpect(status().isOk());
        mvc.perform(put("/api/v1/mall/me/favorites/1").header("Authorization",auth(1))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/mall/me/favorites").header("Authorization",auth(1))).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/v1/mall/me/favorites").header("Authorization",auth(2))).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(delete("/api/v1/mall/me/favorites/1").header("Authorization",auth(2))).andExpect(status().isOk());
        assertTrue(personal.isFavorite(1L,1L));
        mvc.perform(put("/api/v1/mall/me/favorites/1").header("Authorization",auth(2))).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/mall/me/favorites/1").header("Authorization",auth(2))).andExpect(status().isOk());
        assertTrue(personal.isFavorite(1L,1L));
        mvc.perform(delete("/api/v1/mall/me/favorites/1").header("Authorization",auth(1))).andExpect(status().isOk());
        assertFalse(personal.isFavorite(1L,1L));
    }
    @Test void favoriteListMarksUnavailableProductsAndReturnsLiveStatus() throws Exception {
        personal.favorite(1L,1L);
        personal.favorite(1L,3L);
        db.update("UPDATE product SET status='draft' WHERE id=1");
        mvc.perform(get("/api/v1/mall/me/favorites").header("Authorization",auth(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list[0].unavailable").value(false))
                .andExpect(jsonPath("$.data.list[1].unavailable").value(true));
    }
    @Test void historyDeduplicatesMovesToFrontAndTrims() throws Exception {
        personal.visit(1L,1L);personal.visit(1L,2L);personal.visit(1L,1L);personal.visit(1L,3L);
        mvc.perform(get("/api/v1/mall/me/history").header("Authorization",auth(1)))
                .andExpect(jsonPath("$.data.total").value(2)).andExpect(jsonPath("$.data.list[0].productId").value("3"))
                .andExpect(jsonPath("$.data.list[1].productId").value("1"));
        mvc.perform(get("/api/v1/mall/me/history").header("Authorization",auth(2))).andExpect(jsonPath("$.data.total").value(0));
    }
    @Test void historyPreservesCacheOrderAndPaginatesWithoutExposingOtherUsers() throws Exception {
        personal.visit(1L,1L); personal.visit(1L,3L); personal.visit(2L,2L);
        var zset = redis.opsForZSet();
        org.mockito.Mockito.when(zset.reverseRange("mall:history:user:1",0,1))
                .thenReturn(new java.util.LinkedHashSet<>(java.util.List.of("1","3","2")));
        // A cache hit must preserve Redis's order, independent of SQL row order.
        mvc.perform(get("/api/v1/mall/me/history").param("size","2").header("Authorization",auth(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list.length()").value(2))
                .andExpect(jsonPath("$.data.list[0].productId").value("1"))
                .andExpect(jsonPath("$.data.list[1].productId").value("3"));
        mvc.perform(get("/api/v1/mall/me/history").param("page","2").param("size","1").header("Authorization",auth(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.list[0].productId").value("1"));
        mvc.perform(get("/api/v1/mall/me/history").param("page","3").param("size","1").header("Authorization",auth(1)))
                .andExpect(jsonPath("$.data.list.length()").value(0));
    }
    @Test void historyCacheUpdatesAndRebuildUseTheSameDatabaseTimestampScale() throws Exception {
        personal.visit(1L,1L);
        var timestamp = db.queryForObject("SELECT viewed_at FROM mall_history WHERE user_id=1 AND product_id=1", java.sql.Timestamp.class);
        var instant = timestamp.toInstant();
        double score = instant.getEpochSecond() * 1_000_000.0 + instant.getNano() / 1_000;
        var zset = redis.opsForZSet();
        org.mockito.Mockito.verify(zset).add("mall:history:user:1","1",score);
        org.mockito.Mockito.clearInvocations(zset);
        mvc.perform(get("/api/v1/mall/me/history").header("Authorization",auth(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        org.mockito.Mockito.verify(zset).add("mall:history:user:1","1",score);
    }
    @Test void historyUsesDatabaseWhenRedisIsUnavailable() throws Exception {
        org.mockito.Mockito.when(redis.opsForZSet()).thenThrow(new RedisConnectionFailureException("redis down"));
        assertDoesNotThrow(() -> personal.visit(1L,1L));
        assertDoesNotThrow(() -> personal.visit(1L,3L));
        mvc.perform(get("/api/v1/mall/me/history").header("Authorization",auth(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list[0].productId").value("3"));
    }
    @Test void searchFallsBackAndSeparatesRecommendationsFromZeroResults() throws Exception {
        mvc.perform(get("/api/v1/mall/search").param("keyword","智能"))
                .andExpect(jsonPath("$.data.degraded").value(true)).andExpect(jsonPath("$.data.results.total").value(1));
        mvc.perform(get("/api/v1/mall/search").param("keyword","不存在"))
                .andExpect(jsonPath("$.data.results.total").value(0)).andExpect(jsonPath("$.data.recommendations[0].name").value("报警器"));
        mvc.perform(get("/api/v1/mall/search/hotwords")).andExpect(jsonPath("$.data[0]").value("燃气灶"));
    }
    @Test void homeReadsOnlyPublishedWhitelistedConfiguration() throws Exception {
        db.update("INSERT INTO system_config(config_key,config_value) VALUES('secret','hidden')");
        String config="[{\"id\":\"banner\",\"type\":\"banner\",\"title\":\"活动\",\"sort\":1,\"enabled\":true,\"items\":[{\"title\":\"好物\",\"imageUrl\":\"/banner.jpg\",\"path\":\"/products/1\",\"productId\":1},{\"title\":\"下架\",\"productId\":2}]}]";
        db.update("INSERT INTO system_config(config_key,config_value) VALUES('mall.home.floors',?)",config);
        mvc.perform(get("/api/v1/mall/home")).andExpect(jsonPath("$.data[0].items.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("活动"));
    }
}
