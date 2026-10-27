package com.gas.ecommerce.modules.mall.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.ecommerce.modules.product.service.CatalogReadService;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Reads only the whitelisted public configuration, never exposes system configuration. */
@Service
public class MallHomeService {
    public record Item(String title,String imageUrl,String path,Long productId) { }
    public record Floor(String id,String type,String title,int sort,boolean enabled,Instant startsAt,Instant endsAt,List<Item> items) { }
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final CatalogReadService catalog;
    public MallHomeService(JdbcTemplate db,ObjectMapper json,CatalogReadService catalog) {this.db=db;this.json=json;this.catalog=catalog;}
    public List<Floor> floors() {
        var configs=db.query("SELECT config_value FROM system_config WHERE config_key='mall.home.floors' AND is_deleted=0",(r,n)->r.getString(1));
        if(configs.isEmpty()) return List.of();
        try {
            List<Floor> floors=json.readValue(configs.getFirst(),new TypeReference<List<Floor>>(){});
            Instant now=Instant.now();
            return floors.stream().filter(f->f.enabled()&&Set.of("banner","activity","recommend").contains(f.type()))
                    .filter(f->(f.startsAt()==null||!now.isBefore(f.startsAt()))&&(f.endsAt()==null||now.isBefore(f.endsAt())))
                    .sorted(Comparator.comparingInt(Floor::sort).thenComparing(Floor::id))
                    .map(f->new Floor(f.id(),f.type(),f.title(),f.sort(),true,f.startsAt(),f.endsAt(),
                            f.items()==null?List.of():f.items().stream().filter(i->safe(i.path())&&safeImage(i.imageUrl()))
                                    .filter(i->i.productId()==null||catalog.card(i.productId()).filter(c->!"off_sale".equals(c.status())).isPresent()).toList())).toList();
        } catch(Exception e) {throw new com.gas.ecommerce.common.exception.BusinessException("首页运营配置无效");}
    }
    private boolean safe(String path) {return path==null||path.startsWith("/")&&!path.startsWith("//")&&!path.contains("\\");}
    private boolean safeImage(String path) {return path==null||path.startsWith("https://")||path.startsWith("/")&&!path.startsWith("//");}
}
