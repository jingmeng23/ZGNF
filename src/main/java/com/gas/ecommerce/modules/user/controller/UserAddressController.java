package com.gas.ecommerce.modules.user.controller;
import com.gas.ecommerce.common.result.Result;
import com.gas.ecommerce.common.security.SecurityUtils;
import com.gas.ecommerce.modules.user.dto.AddressRequest;
import com.gas.ecommerce.modules.user.service.UserAddressService;
import com.gas.ecommerce.modules.user.vo.UserAddressVO;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me/addresses")
public class UserAddressController {
    private final UserAddressService addresses;
    public UserAddressController(UserAddressService addresses) { this.addresses = addresses; }

    @GetMapping
    public Result<List<UserAddressVO>> list() { return Result.success(addresses.listMine(SecurityUtils.currentUserId())); }

    @PostMapping
    public Result<UserAddressVO> create(@Valid @RequestBody AddressRequest request, @RequestParam(defaultValue="false") boolean allowDuplicate) {
        return Result.success(addresses.create(SecurityUtils.currentUserId(), allowDuplicate ? request.withDuplicateConsent() : request));
    }

    @PutMapping("/{id}")
    public Result<UserAddressVO> update(@PathVariable Long id, @Valid @RequestBody AddressRequest request, @RequestParam(defaultValue="false") boolean allowDuplicate) {
        return Result.success(addresses.update(SecurityUtils.currentUserId(), id, allowDuplicate ? request.withDuplicateConsent() : request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        addresses.delete(SecurityUtils.currentUserId(), id); return Result.success();
    }

    @PutMapping("/{id}/default")
    public Result<UserAddressVO> setDefault(@PathVariable Long id) {
        return Result.success(addresses.setDefault(SecurityUtils.currentUserId(), id));
    }
}
