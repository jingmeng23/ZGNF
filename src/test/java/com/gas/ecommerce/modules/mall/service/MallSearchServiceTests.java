package com.gas.ecommerce.modules.mall.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.ecommerce.common.result.PageResult;
import com.gas.ecommerce.modules.mall.dto.MallQuery;
import com.gas.ecommerce.modules.product.service.CatalogReadService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MallSearchServiceTests {
    HttpServer server;
    CatalogReadService catalog=mock(CatalogReadService.class);
    List<String> requests=new CopyOnWriteArrayList<>();
    List<String> bodies=new CopyOnWriteArrayList<>();
    String hits="{\"hits\":{\"total\":{\"value\":1},\"hits\":[{\"_id\":\"42\",\"_source\":{\"name\":\"智能灶\"}}]}}";
    int searchStatus=200;
    MallSearchService search;
    @BeforeEach void setup() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            String path=exchange.getRequestURI().getPath();requests.add(exchange.getRequestMethod()+" "+path);
            bodies.add(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            assertEquals("ApiKey test-key",exchange.getRequestHeaders().getFirst("Authorization"));
            int status=path.endsWith("_search")?searchStatus:200;
            String response=path.endsWith("_search")?hits:path.startsWith("/_alias/")?"{}":"{\"errors\":false}";
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);
            exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        search=new MallSearchService(catalog,mock(JdbcTemplate.class),new ObjectMapper(),"http://127.0.0.1:"+server.getAddress().getPort(),"mall-products","test-key");
    }
    @AfterEach void stop() {server.stop(0);}
    @Test void usesEsRecallAndAuthoritativeLiveFiltering() {
        MallQuery q=new MallQuery();q.setKeyword("智能");q.setMinPrice(new java.math.BigDecimal("10"));q.setInStock(true);
        var page=new PageResult<com.gas.ecommerce.modules.mall.vo.MallViews.Card>(List.of(),1,1,20,1);
        when(catalog.list(q,List.of(42L))).thenReturn(page);
        var result=search.search(q);
        assertFalse(result.degraded());assertEquals(1,result.results().total());
        verify(catalog).list(q,List.of(42L));assertTrue(bodies.getFirst().contains("multi_match"));
        assertEquals(List.of("智能灶"),search.suggest("智"));
    }
    @Test void esHttpFailureFallsBackWithoutDroppingFilters() {
        searchStatus=503;MallQuery q=new MallQuery();q.setKeyword("灶");q.setBrandId(9L);
        when(catalog.list(q)).thenReturn(new PageResult<>(List.of(),1,1,20,1));
        assertTrue(search.search(q).degraded());verify(catalog).list(q);
    }
    @Test void windowOverflowFallsBackRatherThanReturningTruncatedTotals() {
        hits="{\"hits\":{\"total\":{\"value\":10001},\"hits\":[]}}";
        MallQuery q=new MallQuery();q.setKeyword("灶");when(catalog.list(q)).thenReturn(new PageResult<>(List.of(),1,1,20,1));
        assertTrue(search.search(q).degraded());
    }
    @Test void reindexBuildsBulkRefreshesThenSwitchesAlias() {
        when(catalog.documents(0,500)).thenReturn(List.of(Map.of("id",42L,"name","灶","subtitle","","status","on_sale")));
        when(catalog.documents(42,500)).thenReturn(List.of());
        assertEquals(1L,search.reindex().get("indexed"));
        assertTrue(requests.stream().anyMatch(s->s.endsWith("/_bulk")));
        assertEquals("POST /_aliases",requests.getLast());
        assertTrue(bodies.stream().anyMatch(s->s.endsWith("\n")&&s.contains("\"_id\":\"42\"")));
    }
}
