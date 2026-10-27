package com.gas.ecommerce.modules.user.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.modules.user.vo.AddressDraftVO;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
/** Optional Nominatim-compatible geocoder. Coordinates are WGS84; response is an untrusted suggestion. */
@Service
public class AddressGeocodingService {
    private final RestClient client;
    private final AddressTextParser parser;
    public AddressGeocodingService(@Value("${app.addresses.geocoding-url:}") String base,AddressTextParser parser) {
        this.parser=parser;
        var factory=new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        client=base.isBlank()?null:RestClient.builder().baseUrl(base).requestFactory(factory).defaultHeader("User-Agent","ZGNF-Shop-Address/1.0").build();
    }
    public AddressDraftVO reverse(BigDecimal lat,BigDecimal lon,boolean consent) {
        if(!consent) throw new BusinessException("请先同意将选点坐标发送给地图地址服务");
        if(client==null) throw new BusinessException("地图地址识别暂不可用，请手动补全；选点坐标仍可保留");
        try {
            JsonNode response=client.get().uri(b->b.path("/reverse").queryParam("format","jsonv2").queryParam("lat",lat).queryParam("lon",lon).queryParam("addressdetails",1).queryParam("accept-language","zh-CN").build()).retrieve().body(JsonNode.class);
            if(response==null || !response.path("display_name").isTextual()) throw new IllegalStateException();
            // Prefer structured components: display_name often lists street before province.
            var a=response.path("address");
            String province=first(a,"state","province"),city=first(a,"city","municipality","prefecture"),district=first(a,"city_district","county","district");
            String street=first(a,"road","pedestrian","residential"),house=first(a,"house_number"),building=first(a,"building","amenity");
            if(city.isEmpty() && java.util.Set.of("北京市","上海市","天津市","重庆市").contains(province)) city=province;
            String text=province+city+district+street+house+building;
            return parser.parse(province.isEmpty()?response.path("display_name").asText():text);
        } catch(RuntimeException ex) {throw new BusinessException("地图地址识别失败，请手动补全；选点坐标仍可保留");}
    }
    private static String first(JsonNode address,String... fields) {
        for(String field:fields) {String value=address.path(field).asText("");if(!value.isBlank()) return value;}return "";
    }
}
