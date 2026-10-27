package com.gas.ecommerce.modules.user.controller;
import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.common.result.Result;
import com.gas.ecommerce.common.security.SecurityUtils;
import com.gas.ecommerce.modules.user.service.*;
import com.gas.ecommerce.modules.user.mapper.UserMapper;
import com.gas.ecommerce.modules.user.vo.AddressDraftVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/users/me/addresses")
public class AddressAssistanceController {
    private final AddressTextParser parser;private final AddressRegionService regions;private final AddressGeocodingService geocoder;private final UserMapper users;
    public AddressAssistanceController(AddressTextParser parser,AddressRegionService regions,AddressGeocodingService geocoder,UserMapper users) {this.parser=parser;this.regions=regions;this.geocoder=geocoder;this.users=users;}
    public record ParseRequest(@NotBlank @Size(max=2000) String text) { }
    public record ReverseRequest(@NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,boolean consent) { }
    private void active() {var u=users.selectById(SecurityUtils.currentUserId());if(u==null || !"active".equals(u.getStatus())) throw new BusinessException("用户不存在或已停用");}
    @PostMapping("/parse") public Result<AddressDraftVO> parse(@Valid @RequestBody ParseRequest request) {active();return Result.success(parser.parse(request.text()));}
    @GetMapping("/regions") public Result<java.util.List<AddressRegionService.Region>> regions(@RequestParam(required=false) String parentCode) {active();return Result.success(regions.children(parentCode));}
    @PostMapping("/reverse-geocode") public Result<AddressDraftVO> reverse(@Valid @RequestBody ReverseRequest request) {active();return Result.success(geocoder.reverse(request.latitude(),request.longitude(),request.consent()));}
}
