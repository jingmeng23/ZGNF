package com.gas.ecommerce.modules.user.service.impl;
// MyBatis-Plus 3.5.17 将 Spring Service 接口包从 extension.service 调整到了 spring.service。
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.gas.ecommerce.modules.user.entity.UserAddress;
import com.gas.ecommerce.modules.user.mapper.UserAddressMapper;
import com.gas.ecommerce.modules.user.service.UserAddressService;
import com.gas.ecommerce.modules.user.dto.AddressRequest;
import com.gas.ecommerce.modules.user.entity.User;
import com.gas.ecommerce.modules.user.vo.UserAddressVO;
import com.gas.ecommerce.modules.user.mapper.UserMapper;
import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.common.utils.EncryptUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
/**
 * 业务服务实现类；当前主要继承 MyBatis-Plus 通用 CRUD，复杂业务流程仍需补充。
 */
@org.springframework.stereotype.Service
public class UserAddressServiceImpl extends ServiceImpl<UserAddressMapper, UserAddress> implements UserAddressService {
    private final UserMapper users;
    private final String encryptionKey;
    private final com.gas.ecommerce.modules.user.service.AddressRegionService regions;

    public UserAddressServiceImpl(UserMapper users,
            @Value("${app.encryption-key}") String encryptionKey,com.gas.ecommerce.modules.user.service.AddressRegionService regions) {
        this.users = users;
        this.encryptionKey = encryptionKey; this.regions=regions;
    }

    @Override public List<UserAddressVO> listMine(Long userId) {
        ensureActiveUser(userId);
        return list(Wrappers.<UserAddress>lambdaQuery().eq(UserAddress::getUserId, userId)
                        .orderByDesc(UserAddress::getIsDefault).orderByDesc(UserAddress::getUpdateTime)
                        .orderByDesc(UserAddress::getId))
                .stream().map(this::toView).toList();
    }

    @Override @Transactional
    public UserAddressVO create(Long userId, AddressRequest request) {
        lockUser(userId);
        checkDuplicate(userId,null,request);
        boolean makeDefault = Boolean.TRUE.equals(request.isDefault()) || countFor(userId) == 0;
        if (makeDefault) clearDefault(userId);
        UserAddress address = new UserAddress();
        address.setUserId(userId);
        apply(address, request);
        address.setIsDefault(makeDefault);
        address.setIsDeleted(0);
        save(address);
        return toView(address);
    }

    @Override @Transactional
    public UserAddressVO update(Long userId, Long addressId, AddressRequest request) {
        lockUser(userId);
        UserAddress address = requireOwned(userId, addressId);
        checkDuplicate(userId,addressId,request);
        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());
        apply(address, request);
        if (Boolean.TRUE.equals(request.isDefault())) {
            clearDefault(userId);
            address.setIsDefault(true);
        } else if (Boolean.FALSE.equals(request.isDefault())) {
            address.setIsDefault(false);
        }
        updateById(address);
        clearNullableFields(address);
        if (wasDefault && Boolean.FALSE.equals(request.isDefault())) promoteDefault(userId, addressId);
        return toView(address);
    }

    @Override @Transactional
    public void delete(Long userId, Long addressId) {
        lockUser(userId);
        UserAddress address = requireOwned(userId, addressId);
        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());
        removeById(addressId);
        if (wasDefault) promoteDefault(userId, null);
    }

    @Override @Transactional
    public UserAddressVO setDefault(Long userId, Long addressId) {
        lockUser(userId);
        UserAddress address = requireOwned(userId, addressId);
        clearDefault(userId);
        address.setIsDefault(true);
        address.setUpdateTime(LocalDateTime.now());
        updateById(address);
        return toView(address);
    }

    private void apply(UserAddress address, AddressRequest request) {
        if (request.receiver().trim().length()<2 || request.receiver().trim().length()>20 || request.detail().trim().length()<5)
            throw new BusinessException("收件人须为2至20字，详细地址不少于5字");
        address.setReceiver(request.receiver().trim());
        address.setPhone(EncryptUtils.encryptAes(request.phone(), encryptionKey));
        address.setPhoneLast4(request.phone().substring(7));
        address.setProvince(request.province().trim());
        address.setCity(request.city().trim());
        address.setDistrict(request.district().trim());
        address.setDetail(request.detail().trim());
        address.setLabel(request.label()==null || request.label().isBlank()?null:request.label().trim());
        address.setProvinceCode(request.provinceCode());address.setCityCode(request.cityCode());address.setDistrictCode(request.districtCode());
        address.setAdcode(request.adcode()!=null ? request.adcode() : request.districtCode());
        boolean samePoint = address.getLatitude()!=null && address.getLongitude()!=null && request.latitude()!=null && request.longitude()!=null
                && address.getLatitude().compareTo(request.latitude())==0 && address.getLongitude().compareTo(request.longitude())==0;
        if (!samePoint) address.setCoordinateSystem(request.fullAddress() != null ? "GCJ-02" : "WGS84");
        address.setLatitude(request.latitude());address.setLongitude(request.longitude());
        if(request.provinceCode()!=null) regions.requireValid(address);
    }

    private UserAddressVO toView(UserAddress address) {
        var region=regions.validate(address);
        return new UserAddressVO(address.getId(), address.getReceiver(),
                EncryptUtils.decryptAes(address.getPhone(), encryptionKey), address.getProvince(),
                address.getCity(), address.getDistrict(), address.getDetail(), Boolean.TRUE.equals(address.getIsDefault()),
                address.getLabel(),address.getProvinceCode(),address.getCityCode(),address.getDistrictCode(),address.getLatitude(),address.getLongitude(),address.getCoordinateSystem()==null?"WGS84":address.getCoordinateSystem(),
                !region.valid(),region.message(),region.suggestedDistrictCode(),address.getAdcode());
    }

    private void clearNullableFields(UserAddress a) {
        update(null,Wrappers.<UserAddress>lambdaUpdate().eq(UserAddress::getId,a.getId()).eq(UserAddress::getUserId,a.getUserId())
                .set(UserAddress::getLabel,a.getLabel()).set(UserAddress::getProvinceCode,a.getProvinceCode()).set(UserAddress::getCityCode,a.getCityCode())
                .set(UserAddress::getDistrictCode,a.getDistrictCode()).set(UserAddress::getLatitude,a.getLatitude()).set(UserAddress::getLongitude,a.getLongitude()).set(UserAddress::getAdcode,a.getAdcode()));
    }
    private static String normalized(String text) {
        return java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFKC).replaceAll("[\\s\\p{Punct}\u3000\uFF0C\u3002]", "").toLowerCase(java.util.Locale.ROOT);
    }
    private void checkDuplicate(Long userId,Long excluded,AddressRequest request) {
        if(Boolean.TRUE.equals(request.allowDuplicate())) return;
        String key=normalized(request.province()+request.city()+request.district()+request.detail());
        boolean exists=list(Wrappers.<UserAddress>lambdaQuery().eq(UserAddress::getUserId,userId).ne(excluded!=null,UserAddress::getId,excluded))
                .stream().anyMatch(a->key.equals(normalized(a.getProvince()+a.getCity()+a.getDistrict()+a.getDetail())));
        if(exists) throw new com.gas.ecommerce.modules.user.service.AddressDuplicateException();
    }

    private UserAddress requireOwned(Long userId, Long addressId) {
        UserAddress address = getOne(Wrappers.<UserAddress>lambdaQuery()
                .eq(UserAddress::getId, addressId).eq(UserAddress::getUserId, userId));
        if (address == null) throw new BusinessException("收货地址不存在");
        return address;
    }

    private long countFor(Long userId) {
        return count(Wrappers.<UserAddress>lambdaQuery().eq(UserAddress::getUserId, userId));
    }

    private void clearDefault(Long userId) {
        update(null, Wrappers.<UserAddress>lambdaUpdate().eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getIsDefault, true).set(UserAddress::getIsDefault, false)
                .set(UserAddress::getUpdateTime, LocalDateTime.now()));
    }

    private void promoteDefault(Long userId, Long excludedId) {
        UserAddress next = getOne(Wrappers.<UserAddress>lambdaQuery().eq(UserAddress::getUserId, userId)
                .ne(excludedId != null, UserAddress::getId, excludedId).orderByAsc(UserAddress::getId).last("LIMIT 1"));
        if (next != null) {
            next.setIsDefault(true);
            next.setUpdateTime(LocalDateTime.now());
            updateById(next);
        }
    }

    private User lockUser(Long userId) {
        User user = users.lockActiveUser(userId);
        if (user == null || !"active".equals(user.getStatus())) throw new BusinessException("用户不存在或已停用");
        return user;
    }

    private void ensureActiveUser(Long userId) {
        User user = users.selectById(userId);
        if (user == null || !"active".equals(user.getStatus())) throw new BusinessException("用户不存在或已停用");
    }
}
