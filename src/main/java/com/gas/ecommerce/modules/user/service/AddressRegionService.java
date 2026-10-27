package com.gas.ecommerce.modules.user.service;
import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.modules.user.entity.UserAddress;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddressRegionService {
    public record Region(String code,String parentCode,String name,int level,boolean active,String replacementCode,String version) { }
    public record Validation(boolean valid,String message,String suggestedDistrictCode) { }
    private final JdbcTemplate db;
    public AddressRegionService(JdbcTemplate db) { this.db=db; }
    public List<Region> all() {
        return db.query("SELECT code,parent_code,name,region_level,active,replacement_code,version FROM address_region ORDER BY code",
                (r,n)->new Region(r.getString(1),r.getString(2),r.getString(3),r.getInt(4),r.getBoolean(5),r.getString(6),r.getString(7)));
    }
    public List<Region> children(String parent) {return all().stream().filter(r->r.active() && Objects.equals(parent,r.parentCode())).toList();}
    public Validation validate(UserAddress address) {
        var regions=all().stream().collect(java.util.stream.Collectors.toMap(Region::code,r->r));
        var p=regions.get(address.getProvinceCode());var c=regions.get(address.getCityCode());var d=regions.get(address.getDistrictCode());
        if(p==null || c==null || d==null) return new Validation(false,"请补全当前有效的省市区编码",null);
        if(!p.active() || !c.active() || !d.active()) return new Validation(false,"行政区划已调整，请确认并补全收货地区",d.replacementCode());
        if(p.level()!=1 || c.level()!=2 || d.level()!=3 || !p.code().equals(c.parentCode()) || !c.code().equals(d.parentCode()))
            return new Validation(false,"省市区编码层级不匹配",null);
        if(!p.name().equals(address.getProvince()) || !c.name().equals(address.getCity()) || !d.name().equals(address.getDistrict()))
            return new Validation(false,"行政区名称已更新，请重新选择并确认",d.code());
        return new Validation(true,null,null);
    }
    public void requireValid(UserAddress address) {var result=validate(address);if(!result.valid()) throw new BusinessException(result.message());}
    @Transactional
    public void importRegions(List<Region> incoming) {
        if(incoming==null || incoming.isEmpty() || incoming.size()>10000) throw new BusinessException("区划导入条数须为1至10000");
        var map=new HashMap<String,Region>();for(var r:all()) map.put(r.code(),r);
        var seen=new HashSet<String>();
        for(var r:incoming) {
            if(r==null) throw new BusinessException("区划条目不能为空");
            if(r.code()==null || !r.code().matches("[0-9]{6,12}") || r.name()==null || r.name().isBlank() || r.name().length()>64
                    || r.version()==null || r.version().isBlank() || r.version().length()>64 || r.level()<1 || r.level()>3 || !seen.add(r.code())) throw new BusinessException("区划数据格式错误或编码重复");
            map.put(r.code(),r);
        }
        for(var r:incoming) {
            var parent=map.get(r.parentCode());
            if((r.level()==1 && r.parentCode()!=null) || (r.level()>1 && (parent==null || parent.level()!=r.level()-1))) throw new BusinessException("区划父级不存在或层级错误");
            var replacement=map.get(r.replacementCode());
            if(r.replacementCode()!=null && (replacement==null || !replacement.active() || replacement.level()!=r.level() || r.code().equals(r.replacementCode()))) throw new BusinessException("替代区划编码无效");
            db.update("INSERT INTO address_region(code,parent_code,name,region_level,active,replacement_code,version) VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE parent_code=VALUES(parent_code),name=VALUES(name),region_level=VALUES(region_level),active=VALUES(active),replacement_code=VALUES(replacement_code),version=VALUES(version)",r.code(),r.parentCode(),r.name(),r.level(),r.active(),r.replacementCode(),r.version());
        }
    }
}
