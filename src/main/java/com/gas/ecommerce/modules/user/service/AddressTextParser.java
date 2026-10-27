package com.gas.ecommerce.modules.user.service;
import com.gas.ecommerce.modules.user.vo.AddressDraftVO;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
@Service
public class AddressTextParser {
    private final AddressRegionService regions;
    public AddressTextParser(AddressRegionService regions){this.regions=regions;}
    private static final Pattern PHONE=Pattern.compile("(?<![0-9])(?:\\+?86[- ]?)?(1[3-9](?:[ -]?[0-9]){9})(?![0-9])");
    public AddressDraftVO parse(String raw) {
        String text=java.text.Normalizer.normalize(raw,java.text.Normalizer.Form.NFKC).trim();
        var warnings=new ArrayList<String>();var phoneMatcher=PHONE.matcher(text);
        String phone=null;
        if(phoneMatcher.find()) {phone=phoneMatcher.group(1).replaceAll("[ -]","");if(phoneMatcher.find()) {phone=null;warnings.add("识别到多个手机号，请选择收货电话");}}
        String rest=PHONE.matcher(text).replaceAll(" ").replaceAll("(?:手机号码|手机号|联系电话|电话|收货地址|地址)\\s*[:：]?"," ");
        var all=regions.all();var map=all.stream().collect(java.util.stream.Collectors.toMap(AddressRegionService.Region::code,r->r));
        record Match(AddressRegionService.Region p,AddressRegionService.Region c,AddressRegionService.Region d,int score) { }
        var candidates=new ArrayList<Match>();
        for(var d:all) {
            var c=map.get(d.parentCode());var p=c==null?null:map.get(c.parentCode());
            if(d.level()!=3 || !d.active() || c==null || p==null || !c.active() || !p.active() || !rest.contains(d.name())) continue;
            int score=(rest.contains(p.name())?p.name().length():0)+(rest.contains(c.name())?c.name().length():0);
            if(score>0) candidates.add(new Match(p,c,d,score));
        }
        candidates.sort(Comparator.comparingInt(Match::score).reversed());
        Match found=candidates.isEmpty()?null:candidates.getFirst();
        if(candidates.size()>1 && candidates.get(1).score()==found.score()) {found=null;warnings.add("地区存在歧义，请手动选择省市区");}
        String receiver=null,detail=rest;
        var named=Pattern.compile("(?:收货人|收件人|姓名)\\s*[:：]?\\s*([\\p{IsHan}A-Za-z·]{2,20})").matcher(rest);
        if(named.find()) {receiver=named.group(1);detail=detail.replace(named.group()," ");}
        if(found!=null) {
            int start=detail.length();for(String name:List.of(found.p().name(),found.c().name(),found.d().name())) {int pos=detail.indexOf(name);if(pos>=0) start=Math.min(start,pos);}
            if(receiver==null) {
                String prefix=detail.substring(0,start).replaceAll("[\\s,，;；]+"," ").trim();
                if(prefix.matches("[\\p{IsHan}A-Za-z·]{2,20}")) receiver=prefix;
            }
            int districtStart=detail.indexOf(found.d().name());
            detail=detail.substring(districtStart+found.d().name().length()).stripLeading();
        } else warnings.add("未能唯一识别有效行政区划，请补全地区");
        if(receiver==null) warnings.add("请确认收货人姓名");if(phone==null) warnings.add("请确认收货电话");
        detail=detail.replaceAll("^[\\s,，;；]+|[\\s,，;；]+$","");
        return new AddressDraftVO(receiver,phone,found==null?null:found.p().name(),found==null?null:found.c().name(),found==null?null:found.d().name(),detail,
                found==null?null:found.p().code(),found==null?null:found.c().code(),found==null?null:found.d().code(),true,warnings);
    }
}
