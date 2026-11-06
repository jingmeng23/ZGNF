package com.gas.ecommerce.modules.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import java.util.*;

/** Generated from the pinned npm dataset; database records always take precedence. */
final class BundledAddressRegions {
    static final Map<String, AddressRegionService.Region> BY_CODE = load();
    static final Map<String, List<AddressRegionService.Region>> BY_PARENT = index();
    private static Map<String, AddressRegionService.Region> load() {
        try (var input = new ClassPathResource("regions/province-city-china.json").getInputStream()) {
            var data = new ObjectMapper().readTree(input);
            var result = new LinkedHashMap<String, AddressRegionService.Region>();
            for (var row : data.get("rows")) {
                var region = new AddressRegionService.Region(row.get(0).asText(), row.get(1).isNull()?null:row.get(1).asText(),
                        row.get(2).asText(), row.get(3).asInt(), true, null, data.get("version").asText());
                if (result.put(region.code(), region)!=null) throw new IllegalStateException("Duplicate bundled region");
            }
            return Collections.unmodifiableMap(result);
        } catch (java.io.IOException e) { throw new IllegalStateException("Unable to load bundled address regions", e); }
    }
    private static Map<String, List<AddressRegionService.Region>> index() {
        var result = new HashMap<String, List<AddressRegionService.Region>>();
        for (var region : BY_CODE.values()) result.computeIfAbsent(region.parentCode(), key->new ArrayList<>()).add(region);
        return result;
    }
    private BundledAddressRegions() {}
}
