package com.gas.ecommerce.modules.user.service;
import com.gas.ecommerce.modules.user.entity.UserAddress;
import com.gas.ecommerce.modules.delivery.service.DeliveryQuoteService;
import com.gas.ecommerce.common.exception.BusinessException;
import java.util.*;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AddressAssistanceTests {
    AddressRegionService regions;AddressTextParser parser;
    @BeforeEach void setup() {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:regions_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var db=new JdbcTemplate(ds);db.execute("CREATE TABLE address_region(code VARCHAR(12) PRIMARY KEY,parent_code VARCHAR(12),name VARCHAR(64),region_level INT,active BOOLEAN,replacement_code VARCHAR(12),version VARCHAR(64))");
        regions=new AddressRegionService(db);
        regions.importRegions(List.of(new AddressRegionService.Region("510000",null,"四川省",1,true,null,"v1"),new AddressRegionService.Region("510300","510000","自贡市",2,true,null,"v1"),new AddressRegionService.Region("510302","510300","自流井区",3,true,null,"v1")));
        parser=new AddressTextParser(regions);
    }
    @Test void streetsCanBeImportedAndLoadedOnlyUnderTheirDistrict() {
        regions.importRegions(List.of(new AddressRegionService.Region("510302001","510302","五星街街道",4,true,null,"v1")));
        assertEquals("510302001", regions.children("510302").getFirst().code());
        assertEquals(4, regions.children("510302").getFirst().level());
        assertTrue(regions.children("510302001").isEmpty());
        assertThrows(BusinessException.class, () -> regions.importRegions(List.of(new AddressRegionService.Region("510302002","510300","错误街道",4,true,null,"v1"))));
        assertTrue(regions.validate(address()).valid());
    }
    @Test void parsedFieldsAreAlwaysSuggestionsAndNeverSaved() {
        var draft=parser.parse("张三，13800138000，四川省自贡市自流井区汇东路1号");
        assertEquals("张三",draft.receiver());assertEquals("13800138000",draft.phone());assertEquals("510302",draft.districtCode());assertEquals("汇东路1号",draft.detail());assertTrue(draft.needsConfirmation());
    }
    @Test void ambiguousPhonesAndRegionsAreNotGuessed() {
        var draft=parser.parse("张三 13800138000 13900139000 未知地区1号");
        assertNull(draft.phone());assertNull(draft.districtCode());assertFalse(draft.warnings().isEmpty());assertTrue(draft.needsConfirmation());
    }
    @Test void obsoleteCodesRequireExplicitCompletionAndOfferReplacement() {
        var address=address();assertTrue(regions.validate(address).valid());
        regions.importRegions(List.of(new AddressRegionService.Region("510399","510300","新城区",3,true,null,"v2"),new AddressRegionService.Region("510302","510300","自流井区",3,false,"510399","v2")));
        var result=regions.validate(address);assertFalse(result.valid());assertEquals("510399",result.suggestedDistrictCode());assertEquals("自流井区",address.getDistrict());assertThrows(BusinessException.class,()->regions.requireValid(address));
    }
    @Test void wrongParentAndRenamedRegionAreRejected() {
        var address=address();address.setCityCode("510000");assertFalse(regions.validate(address).valid());
        address=address();address.setDistrict("旧名称");assertFalse(regions.validate(address).valid());
    }
    @Test void checkoutRequiresCodesAndDelegatesFreightToDelivery() {
        var store=mock(UserAddressService.class);var delivery=mock(DeliveryQuoteService.class);var checkout=new AddressCheckoutService(store,regions,delivery,true);
        var address=address();address.setUserId(1L);when(store.getById(2L)).thenReturn(address);
        when(delivery.quote("四川省","自贡市","自流井区")).thenReturn(java.math.BigDecimal.TEN);
        assertEquals(java.math.BigDecimal.TEN,checkout.quote(1L,2L).freightAmount());
        address.setDistrictCode(null);assertThrows(BusinessException.class,()->checkout.quote(1L,2L));verify(delivery,times(1)).quote(anyString(),anyString(),anyString());
    }
    @Test void geocodingRequiresConsentAndConfiguredProvider() {
        var geocoder=new AddressGeocodingService("",parser);
        assertThrows(BusinessException.class,()->geocoder.reverse(java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,false));
        assertThrows(BusinessException.class,()->geocoder.reverse(java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,true));
    }
    UserAddress address() {var a=new UserAddress();a.setProvince("四川省");a.setCity("自贡市");a.setDistrict("自流井区");a.setProvinceCode("510000");a.setCityCode("510300");a.setDistrictCode("510302");return a;}
}
