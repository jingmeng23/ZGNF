package com.gas.ecommerce.modules.user.controller;
import com.gas.ecommerce.common.result.Result;
import com.gas.ecommerce.modules.user.service.AddressRegionService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
/** Protected by /api/v1/admin/** security rules. Imports never rewrite address/order snapshots. */
@RestController @RequestMapping("/api/v1/admin/address-regions")
public class AddressRegionAdminController {
    private final AddressRegionService regions;
    public AddressRegionAdminController(AddressRegionService regions){this.regions=regions;}
    @PutMapping public Result<Void> importRegions(@RequestBody List<AddressRegionService.Region> rows) {regions.importRegions(rows);return Result.success();}
}
