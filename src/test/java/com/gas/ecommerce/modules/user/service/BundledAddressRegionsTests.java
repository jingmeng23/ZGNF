package com.gas.ecommerce.modules.user.service;

import com.gas.ecommerce.modules.user.entity.UserAddress;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BundledAddressRegionsTests {
    @Test void datasetHasUniqueCodesValidParentsAndGongjingStreet() {
        var rows=BundledAddressRegions.BY_CODE;
        for(var row:rows.values()) if(row.level()>1) {
            var parent=rows.get(row.parentCode());
            assertNotNull(parent,row.code()); assertEquals(row.level()-1,parent.level());
        }
        assertEquals("贡井街道",rows.get("510303002").name());
        assertEquals("510303",rows.get("510303002").parentCode());
        assertEquals("北京市",rows.get("110100").name());
        assertEquals(90,rows.values().stream().filter(r->r.level()==4 && r.code().startsWith("5103")).count());
    }
    @Test void bundledSelectionValidatesWithoutImportAndDatabaseOverridesStillApply() {
        var ds=new JdbcDataSource(); ds.setURL("jdbc:h2:mem:bundled_"+UUID.randomUUID()+";MODE=MySQL");
        var db=new JdbcTemplate(ds);
        try(var connection=ds.getConnection()) {
            db.execute("CREATE TABLE address_region(code VARCHAR(12) PRIMARY KEY,parent_code VARCHAR(12),name VARCHAR(64),region_level INT,active BOOLEAN,replacement_code VARCHAR(12),version VARCHAR(64))");
            var regions=new AddressRegionService(db);
            assertEquals(6,regions.children("510300").size());
            assertTrue(regions.children("510303").stream().anyMatch(r->r.code().equals("510303002")));
            var address=new UserAddress();
            address.setProvinceCode("510000");address.setCityCode("510300");address.setDistrictCode("510303");
            address.setProvince("四川省");address.setCity("自贡市");address.setDistrict("贡井区");
            assertTrue(regions.validate(address).valid());
            regions.importRegions(List.of(new AddressRegionService.Region("510303","510300","贡井区",3,false,null,"override")));
            assertFalse(regions.validate(address).valid());
            assertFalse(regions.children("510300").stream().anyMatch(r->r.code().equals("510303")));
        } catch(java.sql.SQLException e) { throw new RuntimeException(e); }
    }
}
