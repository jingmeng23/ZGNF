package com.gas.ecommerce.modules.mall.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.ecommerce.modules.mall.dto.MallQuery;
import java.util.*;

/** Elasticsearch request shape kept independent from transport/client implementation. */
public final class SearchDslBuilder {
    private SearchDslBuilder() { }
    public static ObjectNode mapping(ObjectMapper json) {
        ObjectNode p=json.createObjectNode();
        p.putObject("id").put("type","long");
        p.putObject("title").put("type","text").put("analyzer","ik_max_word").put("search_analyzer","ik_smart").putObject("fields").putObject("keyword").put("type","keyword").put("ignore_above",256);
        p.putObject("brand").put("type","keyword"); p.putObject("brand_id").put("type","long"); p.putObject("category_id").put("type","long");
        p.putObject("category_path").put("type","keyword"); p.putObject("price").put("type","scaled_float").put("scaling_factor",100);
        p.putObject("sales").put("type","long"); p.putObject("status").put("type","keyword");
        p.putObject("created_at").put("type","date"); p.putObject("updated_at").put("type","date");
        p.putObject("attributes").put("type","flattened");
        ObjectNode pairs=p.putObject("attribute_pairs").put("type","nested");
        pairs.putObject("properties").putObject("name").put("type","keyword");
        pairs.with("properties").putObject("value").put("type","keyword");
        p.putObject("suggest").put("type","completion");
        return json.createObjectNode().set("mappings",json.createObjectNode().set("properties",p));
    }
    public static ObjectNode search(ObjectMapper json,MallQuery q,int from,int size) {
        if(from<0||size<1||size>100||from>10000-size) throw new IllegalArgumentException("搜索分页超出 from+size=10000 限制");
        ObjectNode root=json.createObjectNode();root.put("from",from).put("size",size).put("track_total_hits",true);
        ObjectNode bool=json.createObjectNode();
        if(q.getKeyword()==null||q.getKeyword().isBlank()) bool.set("must",json.createArrayNode().add(json.createObjectNode().set("match_all",json.createObjectNode())));
        else bool.set("must",json.createArrayNode().add(json.createObjectNode().set("multi_match",json.createObjectNode().put("query",q.getKeyword().trim()).put("type","best_fields").putArray("fields").add("title^4").add("brand^2").add("category_path").add("attributes.*"))));
        var filters=json.createArrayNode().add(json.createObjectNode().set("terms",json.createObjectNode().putArray("status").add("on_sale").add("sold_out").add("presale").add("scheduled")));
        if(q.getBrandId()!=null) filters.add(json.createObjectNode().set("term",json.createObjectNode().put("brand_id",q.getBrandId())));
        if(q.getCategoryId()!=null) filters.add(json.createObjectNode().set("term",json.createObjectNode().put("category_id",q.getCategoryId())));
        if(q.getMinPrice()!=null||q.getMaxPrice()!=null) {ObjectNode range=json.createObjectNode();if(q.getMinPrice()!=null)range.put("gte",q.getMinPrice());if(q.getMaxPrice()!=null)range.put("lte",q.getMaxPrice());filters.add(json.createObjectNode().set("range",json.createObjectNode().set("price",range)));}
        if(q.isInStock()) filters.add(json.createObjectNode().set("range",json.createObjectNode().set("available_stock",json.createObjectNode().put("gt",0))));
        bool.set("filter",filters);root.set("query",json.createObjectNode().set("bool",bool));
        root.set("highlight",json.createObjectNode().putArray("fields").addObject().put("title",json.createObjectNode().put("pre_tags","<em>").put("post_tags","</em>")));
        String sort=switch(q.getSort()){case "sales"->"sales";case "price"->"price";case "newest","createTime"->"created_at";default->"_score";};
        root.set("sort",json.createArrayNode().add(json.createObjectNode().set(sort,json.createObjectNode().put("order","asc".equals(q.getOrder())?"asc":"desc").put("unmapped_type","keyword"))).add(json.createObjectNode().set("id",json.createObjectNode().put("order","desc"))));
        ObjectNode aggs=root.putObject("aggs");
        aggs.putObject("brands").set("terms",json.createObjectNode().put("field","brand").put("size",100));
        ObjectNode nested=aggs.putObject("attributes").set("nested",json.createObjectNode().put("path","attribute_pairs"));
        nested.putObject("aggs").putObject("values").set("terms",json.createObjectNode().put("field","attribute_pairs.value").put("size",200));
        return root;
    }
}
