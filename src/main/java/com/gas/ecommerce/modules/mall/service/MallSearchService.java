package com.gas.ecommerce.modules.mall.service;

import com.fasterxml.jackson.databind.*;
import com.gas.ecommerce.common.result.PageResult;
import com.gas.ecommerce.modules.mall.dto.MallQuery;
import com.gas.ecommerce.modules.mall.vo.MallViews.Card;
import com.gas.ecommerce.modules.product.service.CatalogReadService;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MallSearchService {
    public record Facet(String key,long count) { }
    public record SearchResult(PageResult<Card> results,List<Card> recommendations,boolean degraded,String message,Map<String,List<Facet>> facets,Map<String,String> highlights) { }
    private final CatalogReadService catalog;
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final String endpoint,index,apiKey;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    public MallSearchService(CatalogReadService catalog,JdbcTemplate db,ObjectMapper json,
            @Value("${app.mall.elasticsearch.url:}") String endpoint,
            @Value("${app.mall.elasticsearch.index:mall-products}") String index,
            @Value("${app.mall.elasticsearch.api-key:}") String apiKey) {
        if(!index.matches("[a-z][a-z0-9_-]*")) throw new IllegalArgumentException("Invalid ES index");
        this.catalog=catalog;this.db=db;this.json=json;this.endpoint=endpoint.replaceAll("/+$","");this.index=index;this.apiKey=apiKey;
    }
    public SearchResult search(MallQuery q) {
        catalog.order(q);
        boolean degraded=false;PageResult<Card> page;Map<String,List<Facet>> facets=new LinkedHashMap<>();Map<String,String> highlights=new LinkedHashMap<>();
        if(q.getKeyword()==null||q.getKeyword().isBlank()) page=catalog.list(q);
        else {
            try {
                if(endpoint.isBlank()) throw new IllegalStateException("ES not configured");
                // Retrieve all matching IDs up to the ES window, then apply owner-service live filters and pagination.
                int from=(q.getPage()-1)*q.getSize();
                var response=call("POST","/"+index+"/_search",json.writeValueAsString(SearchDslBuilder.search(json,q,from,q.getSize())));
                if(response.path("hits").path("total").path("value").asLong()>10000) throw new IllegalStateException("ES result window exceeded");
                List<Long> ids=new ArrayList<>();
                for(var hit:response.path("hits").path("hits")) ids.add(Long.valueOf(hit.path("_id").asText()));
                page=catalog.list(q,ids);
                for(var bucket:response.path("aggregations").path("brands").path("buckets")) facets.computeIfAbsent("brand",k->new ArrayList<>()).add(new Facet(bucket.path("key").asText(),bucket.path("doc_count").asLong()));
                for(var bucket:response.path("aggregations").path("attributes").path("values").path("buckets")) facets.computeIfAbsent("attributes",k->new ArrayList<>()).add(new Facet(bucket.path("key").asText(),bucket.path("doc_count").asLong()));
                for(var hit:response.path("hits").path("hits")) if(hit.path("highlight").path("title").isArray()) highlights.put(hit.path("_id").asText(),hit.path("highlight").path("title").get(0).asText());
            } catch(Exception e) {degraded=true;page=catalog.list(q);}
        }
        List<Card> recommendations=List.of();
        if(page.total()==0) {MallQuery fallback=new MallQuery();fallback.setSize(6);fallback.setSort("sales");recommendations=catalog.list(fallback).list();}
        return new SearchResult(page,recommendations,degraded,page.total()==0?"未找到符合条件的商品，看看其他热门商品":degraded?"搜索服务暂不可用，已使用基础搜索":"",facets,highlights);
    }
    public List<String> hotwords() {
        return db.query("SELECT word FROM search_hotword WHERE is_deleted=0 AND status='active' ORDER BY search_count DESC,id DESC LIMIT 10",(r,n)->r.getString(1));
    }
    public List<String> suggest(String keyword) {
        if(keyword==null||keyword.isBlank()) return hotwords();
        try {
            if(endpoint.isBlank()) throw new IllegalStateException();
            var response=call("POST","/"+index+"/_search",json.writeValueAsString(Map.of("size",10,"_source",List.of("name"),
                    "query",Map.of("bool",Map.of("must",Map.of("match_phrase_prefix",Map.of("name",keyword.trim())),
                            "filter",Map.of("terms",Map.of("status",List.of("on_sale","sold_out","presale","scheduled"))))))));
            Set<String> names=new LinkedHashSet<>();
            for(var hit:response.path("hits").path("hits")) names.add(hit.path("_source").path("name").asText());
            return List.copyOf(names);
        } catch(Exception e) {
            return db.query("SELECT word FROM search_hotword WHERE is_deleted=0 AND status='active' AND word LIKE ? ESCAPE '!' ORDER BY search_count DESC,id DESC LIMIT 10",
                    (r,n)->r.getString(1),"%"+CatalogReadService.escape(keyword.trim())+"%");
        }
    }
    /** Build a versioned index and atomically switch alias, so failures retain the previous index. */
    public synchronized Map<String,Object> reindex() {
        if(endpoint.isBlank()) throw new com.gas.ecommerce.common.exception.BusinessException("请先配置 Elasticsearch");
        String target=index+"-"+UUID.randomUUID().toString().replace("-","");
        List<String> previous=new ArrayList<>();
        try {
            call("GET","/_alias/"+index,"").fieldNames().forEachRemaining(previous::add);
            call("PUT","/"+target,json.writeValueAsString(SearchDslBuilder.mapping(json)));
            long after=0,count=0;
            while(true) {
                var docs=catalog.documents(after,500);if(docs.isEmpty()) break;
                StringBuilder bulk=new StringBuilder();
                for(var doc:docs) {
                    after=((Number)doc.get("id")).longValue();
                    bulk.append(json.writeValueAsString(Map.of("index",Map.of("_id",String.valueOf(after))))).append('\n');
                    bulk.append(json.writeValueAsString(doc)).append('\n');count++;
                }
                if(call("POST","/"+target+"/_bulk",bulk.toString()).path("errors").asBoolean()) throw new IllegalStateException("Bulk indexing failed");
            }
            call("POST","/"+target+"/_refresh","{}");
            List<Object> actions=new ArrayList<>();
            for(String old:previous) actions.add(Map.of("remove",Map.of("index",old,"alias",index)));
            actions.add(Map.of("add",Map.of("index",target,"alias",index)));
            call("POST","/_aliases",json.writeValueAsString(Map.of("actions",actions)));
            // Delete only the old versioned indices that belonged to our alias.
            for(String old:previous) if(old.startsWith(index+"-")) {
                try {call("DELETE","/"+old,"");}
                catch(Exception ignored) {org.slf4j.LoggerFactory.getLogger(getClass()).warn("Old mall search index retained: {}",old);}
            }
            return Map.of("indexed",count,"index",target);
        } catch(Exception e) {
            try {call("DELETE","/"+target,"");} catch(Exception ignored) { }
            throw new com.gas.ecommerce.common.exception.BusinessException("ES 索引同步失败，原索引保持可用");
        }
    }
    private JsonNode call(String method,String path,String body) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(endpoint+path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type",path.endsWith("_bulk")?"application/x-ndjson":"application/json");
        if(!apiKey.isBlank()) builder.header("Authorization","ApiKey "+apiKey);
        var response=http.send(builder.method(method,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()==404&&path.startsWith("/_alias/")) return json.createObjectNode();
        if(response.statusCode()<200||response.statusCode()>=300) throw new IllegalStateException("ES HTTP "+response.statusCode());
        return json.readTree(response.body());
    }
}
