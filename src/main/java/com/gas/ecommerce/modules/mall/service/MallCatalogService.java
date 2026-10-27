package com.gas.ecommerce.modules.mall.service;

import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.modules.inventory.service.StockReadService;
import com.gas.ecommerce.modules.marketing.service.PublishedPriceService;
import com.gas.ecommerce.modules.product.service.CatalogReadService;
import com.gas.ecommerce.modules.mall.vo.MallViews.*;
import org.springframework.beans.factory.annotation.Value;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class MallCatalogService {
    private final CatalogReadService catalog;
    private final StockReadService inventory;
    private final PublishedPriceService marketing;
    private final ProductDetailCacheService stableCache;
    @Value("${app.mall.detail-cache.enabled:true}") private boolean detailCacheEnabled;
    public MallCatalogService(CatalogReadService catalog,StockReadService inventory,PublishedPriceService marketing,ProductDetailCacheService stableCache) {
        this.catalog=catalog;this.inventory=inventory;this.marketing=marketing;this.stableCache=stableCache;
    }
    public Detail detail(Long id) {
        var cached=detailCacheEnabled?stableCache.get(id):catalog.stableDetail(id);
        var stable=cached.orElseThrow(()->new BusinessException(404001,"商品不存在或暂不可展示"));
        // Re-read volatile service data after stable content so stale pages never claim a sellable SKU.
        Card card=catalog.card(id).orElseThrow(()->new BusinessException(404001,"商品不存在或暂不可展示"));
        return new Detail(card,stable.mainImages(),stable.description(),stable.detailImages(),stable.servicePromises(),
                stable.review(),stable.attributes(),matrix(id,Map.of()));

    }
    public Matrix matrix(Long id,Map<String,String> selected) {
        Card product=catalog.card(id).orElseThrow(()->new BusinessException(404001,"商品不存在或暂不可展示"));
        var rows=catalog.skuRows(id);
        var ids=rows.stream().map(r->(Long)r.get("id")).toList();
        var stock=inventory.skus(ids); var quotes=marketing.quotes(ids);
        List<Sku> skus=new ArrayList<>();
        Map<String,Set<String>> dimensions=new TreeMap<>();
        for(var r:rows) {
            Long skuId=(Long)r.get("id");var specs=catalog.specs(skuId);
            specs.forEach((k,v)->dimensions.computeIfAbsent(k,ignored->new TreeSet<>()).add(v));
            long available=stock.getOrDefault(skuId,0L);
            boolean active="active".equals(r.get("status"));
            boolean selectable=active&&available>0&&"on_sale".equals(product.status());
            String reason=!active?"规格已停用":!"on_sale".equals(product.status())?product.statusMessage():available==0?"该规格已售罄":"";
            skus.add(new Sku(skuId,(String)r.get("code"),(String)r.get("name"),(String)r.get("image"),specs,
                    quotes.getOrDefault(skuId,(BigDecimal)r.get("price")),(BigDecimal)r.get("original"),available,selectable,reason));
        }
        for(var e:selected.entrySet()) if(!dimensions.containsKey(e.getKey())||!dimensions.get(e.getKey()).contains(e.getValue()))
            throw new BusinessException("规格选项不存在");
        List<Dimension> options=new ArrayList<>();
        dimensions.forEach((name,values)->options.add(new Dimension(name,values.stream().map(value->new Option(value,
                skus.stream().noneMatch(s->s.selectable()&&value.equals(s.specs().get(name))&&selected.entrySet().stream()
                        .filter(e->!e.getKey().equals(name)).allMatch(e->e.getValue().equals(s.specs().get(e.getKey())))))).toList())));
        Long match=selected.size()==dimensions.size()&&!dimensions.isEmpty()?skus.stream()
                .filter(s->s.selectable()&&s.specs().equals(selected)).map(Sku::id).findFirst().orElse(null):null;
        if(dimensions.isEmpty()&&skus.size()==1&&skus.getFirst().selectable()) match=skus.getFirst().id();
        return new Matrix(options,skus,match);
    }
}
