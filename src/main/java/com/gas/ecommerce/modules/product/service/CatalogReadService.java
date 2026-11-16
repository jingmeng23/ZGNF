package com.gas.ecommerce.modules.product.service;

import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.common.result.PageResult;
import com.gas.ecommerce.modules.mall.dto.MallQuery;
import com.gas.ecommerce.modules.mall.vo.MallViews.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** Product-center read contract. Prices and balances come from owner-provided read views. */
@Service
public class CatalogReadService {
    public record ProductDetailChanged(Long productId) { }
    private final JdbcTemplate db;
    private final NamedParameterJdbcTemplate named;
    private final ProductBrowseService categories;
    public CatalogReadService(JdbcTemplate db, NamedParameterJdbcTemplate named, ProductBrowseService categories) {
        this.db=db; this.named=named; this.categories=categories;
    }
    private static final String FROM = " FROM product p JOIN product_published_price q ON q.product_id=p.id"
            + " JOIN product_available_stock a ON a.product_id=p.id"
            + " LEFT JOIN brand b ON b.id=p.brand_id AND b.is_deleted=0";
    private static final String SELECT = "SELECT p.*,q.price display_price,a.available,b.name brand_name,"
            + "(SELECT COUNT(*) FROM product_sku s WHERE s.product_id=p.id) sku_count";
    public static final Set<String> PUBLIC = Set.of("on_sale","off_sale","sold_out","presale","scheduled");
    public static String status(String state, long stock) {
        return "on_sale".equals(state) && stock==0 ? "sold_out" : state;
    }
    public static String message(String state) {
        return switch(state) { case "on_sale" -> "已上架"; case "off_sale" -> "商品已下架";
            case "sold_out" -> "商品已售罄"; case "presale" -> "预售商品，开售时间以公告为准";
            case "scheduled" -> "商品待上架"; default -> "商品暂不可购买"; };
    }
    private final RowMapper<Card> mapper = (r,n) -> {
        long stock=r.getLong("available"); String state=status(r.getString("status"),stock);
        return new Card(r.getLong("id"),r.getLong("category_id"),r.getObject("brand_id",Long.class),
                r.getString("name"),r.getString("subtitle"),r.getString("cover_url"),r.getBigDecimal("display_price"),
                null,r.getLong("sales"),stock,state,message(state),"on_sale".equals(state)&&stock>0,r.getString("brand_name"),r.getLong("sku_count"));
    };

    public PageResult<Card> list(MallQuery query) { return list(query,null); }
    public PageResult<Card> list(MallQuery query, List<Long> searchIds) {
        Map<String,Object> params=new HashMap<>();
        var visible=categories.categories();
        Set<Long> ids=new HashSet<>();
        if(query.getCategoryId()==null) visible.forEach(c->ids.add(c.id()));
        else {
            if(visible.stream().anyMatch(c->c.id().equals(query.getCategoryId()))) ids.add(query.getCategoryId());
            boolean changed=true;
            while(changed) { changed=false; for(var c:visible) if(ids.contains(c.parentId())) changed|=ids.add(c.id()); }
        }
        String order=order(query);
        if(ids.isEmpty() || searchIds!=null && searchIds.isEmpty()) return page(List.of(),0,query);
        params.put("categories",ids);
        StringBuilder where=new StringBuilder(" WHERE p.is_deleted=0 AND p.status IN ('on_sale','sold_out','presale','scheduled') AND p.category_id IN (:categories)");
        if(searchIds!=null) { where.append(" AND p.id IN (:searchIds)");params.put("searchIds",searchIds); }
        else if(query.getKeyword()!=null&&!query.getKeyword().isBlank()) {
            where.append(" AND (LOWER(p.name) LIKE :keyword ESCAPE '!' OR LOWER(p.subtitle) LIKE :keyword ESCAPE '!')");
            params.put("keyword","%"+escape(query.getKeyword().trim().toLowerCase(Locale.ROOT))+"%");
        }
        if(query.getBrandId()!=null) {where.append(" AND p.brand_id=:brand");params.put("brand",query.getBrandId());}
        if(query.getMinPrice()!=null) {where.append(" AND q.price>=:min");params.put("min",query.getMinPrice());}
        if(query.getMaxPrice()!=null) {where.append(" AND q.price<=:max");params.put("max",query.getMaxPrice());}
        if(query.isInStock()) where.append(" AND a.available>0 AND p.status='on_sale'");
        int i=0;
        if(query.getAttributes()!=null) for(var attr:query.getAttributes().entrySet()) {
            where.append(" AND EXISTS(SELECT 1 FROM product_attribute x WHERE x.product_id=p.id AND x.attribute_name=:an").append(i)
                    .append(" AND x.attribute_value=:av").append(i).append(")");
            params.put("an"+i,attr.getKey());params.put("av"+i,attr.getValue());i++;
        }
        long total=named.queryForObject("SELECT COUNT(*)"+FROM+where,params,Long.class);
        params.put("limit",query.getSize());params.put("offset",((long)query.getPage()-1)*query.getSize());
        // ES relevance is retained by a bound CASE expression; filters and totals remain authoritative.
        if(searchIds!=null&&"comprehensive".equals(query.getSort())) {
            StringBuilder relevance=new StringBuilder("CASE p.id ");
            for(int j=0;j<searchIds.size();j++) {relevance.append("WHEN :rank").append(j).append(" THEN ").append(j).append(' ');params.put("rank"+j,searchIds.get(j));}
            order=relevance.append("ELSE ").append(searchIds.size()).append(" END ASC,p.id DESC").toString();
        }
        return page(named.query(SELECT+FROM+where+" ORDER BY "+order+" LIMIT :limit OFFSET :offset",params,mapper),total,query);
    }
    public String order(MallQuery q) {
        String direction="asc".equals(q.getOrder())?" ASC":" DESC";
        return switch(q.getSort()) {case "comprehensive" -> "p.sales DESC,p.id DESC";
            case "sales" -> "p.sales"+direction+",p.id DESC";
            case "price" -> "q.price"+direction+",p.id DESC";
            case "newest","createTime" -> "p.create_time"+direction+",p.id DESC";
            default -> throw new BusinessException("不支持的排序字段");};
    }
    private <T> PageResult<T> page(List<T> rows,long total,MallQuery q) {
        return new PageResult<>(rows,total,q.getPage(),q.getSize(),(total+q.getSize()-1)/q.getSize());
    }
    public Optional<Card> card(Long id) {
        var rows=db.query(SELECT+FROM+" WHERE p.id=? AND p.is_deleted=0",mapper,id);
        if(rows.isEmpty()) return Optional.empty();
        var card=rows.getFirst();
        if(!PUBLIC.contains(card.status()) || categories.categories().stream().noneMatch(c->c.id().equals(card.categoryId()))) return Optional.empty();
        return Optional.of(card);
    }
    public Map<Long, Card> cards(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        Map<Long, Card> result = new HashMap<>();
        named.query(SELECT + FROM + " WHERE p.id IN (:ids) AND p.is_deleted=0", Map.of("ids", ids),
                rs -> {
                    Card card = mapper.mapRow(rs, rs.getRow());
                    if (card != null && PUBLIC.contains(card.status())) result.put(card.id(), card);
                });
        Set<Long> categoryIds = categories.categories().stream().map(c -> c.id()).collect(java.util.stream.Collectors.toSet());
        result.values().removeIf(card -> !categoryIds.contains(card.categoryId()));
        return result;
    }
    public List<Map<String,Object>> skuRows(Long id) {
        return db.query("SELECT * FROM product_sku WHERE product_id=? ORDER BY id",(r,n)->{
            Map<String,Object> row=new HashMap<>();row.put("id",r.getLong("id"));row.put("code",r.getString("code"));
            row.put("name",r.getString("name"));row.put("image",r.getString("image_url"));row.put("price",r.getBigDecimal("price"));
            row.put("original",r.getBigDecimal("original_price"));row.put("status",r.getString("status"));return row;
        },id);
    }
    public Map<String,String> specs(Long sku) {
        Map<String,String> specs=new LinkedHashMap<>();
        db.query("SELECT dimension,spec_value FROM product_sku_spec WHERE sku_id=? ORDER BY dimension",r->{specs.put(r.getString(1),r.getString(2));},sku);
        return specs;
    }
    public Map<String,List<String>> attributes(Long id) {
        Map<String,List<String>> attrs=new LinkedHashMap<>();
        db.query("SELECT attribute_name,attribute_value FROM product_attribute WHERE product_id=? ORDER BY attribute_name,attribute_value",
                r->{attrs.computeIfAbsent(r.getString(1),k->new ArrayList<>()).add(r.getString(2));},id);
        return attrs;
    }
    public List<String> strings(String sql,Long id) { return db.query(sql,(r,n)->r.getString(1),id); }
    public Review review(Long id) {
        var rows=db.query("SELECT * FROM product_review_summary WHERE product_id=?",(r,n)->new Review(r.getLong("review_count"),r.getLong("positive_count"),r.getBigDecimal("average_rating")),id);
        return rows.isEmpty()?new Review(0,0,BigDecimal.ZERO):rows.getFirst();
    }
    public List<Map<String,Object>> brands() {
        return db.query("SELECT id,name FROM brand WHERE is_deleted=0 AND status='active' ORDER BY name,id",(r,n)->Map.of("id",r.getLong(1),"name",r.getString(2)));
    }
    public Map<String,List<String>> attributeOptions() {
        Map<String,List<String>> result=new LinkedHashMap<>();
        db.query("SELECT DISTINCT x.attribute_name,x.attribute_value FROM product_attribute x JOIN product p ON p.id=x.product_id"
                + " WHERE p.is_deleted=0 AND p.status IN ('on_sale','sold_out','presale','scheduled') ORDER BY x.attribute_name,x.attribute_value",
                r->{result.computeIfAbsent(r.getString(1),k->new ArrayList<>()).add(r.getString(2));});
        return result;
    }
    public List<Map<String,Object>> documents(long after,int size) {
        return db.query("SELECT id,name,subtitle,status FROM product WHERE is_deleted=0 AND id>? ORDER BY id LIMIT ?",(r,n)->{
            Map<String,Object> d=new LinkedHashMap<>();d.put("id",r.getLong(1));d.put("title",r.getString(2));d.put("name",r.getString(2));d.put("subtitle",Objects.toString(r.getString(3),""));d.put("status",r.getString(4));return d;
        },after,size);
    }
    public static String escape(String s) { return s.replace("!","!!").replace("%","!%").replace("_","!_"); }

    @org.springframework.transaction.annotation.Transactional(readOnly=true,timeout=3)
    public Optional<StableDetail> stableDetail(Long id) {
        var rows=db.query("SELECT id,name,subtitle,cover_url,category_id,brand_id,description,detail_images FROM product WHERE id=? AND is_deleted=0",
                (r,n)->new Object[]{r.getLong("id"),r.getString("name"),r.getString("subtitle"),r.getString("cover_url"),
                        r.getLong("category_id"),r.getObject("brand_id",Long.class),r.getString("description"),r.getString("detail_images")},id);
        if(rows.isEmpty()) return Optional.empty();
        Object[] row=rows.getFirst();
        List<String> images=new ArrayList<>();
        if(row[3]!=null&&!((String)row[3]).isBlank()) images.add((String)row[3]);
        images.addAll(strings("SELECT url FROM product_media WHERE product_id=? ORDER BY sort,id",id));
        String detail=(String)row[7];
        return Optional.of(new StableDetail((String)row[1],(String)row[2],(String)row[3],(Long)row[4],(Long)row[5],
                images.stream().distinct().toList(),(String)row[6],detail==null?List.of():detail.lines().filter(s->!s.isBlank()).toList(),
                strings("SELECT promise FROM product_promise WHERE product_id=? ORDER BY promise",id),review(id),attributes(id)));
    }
}
