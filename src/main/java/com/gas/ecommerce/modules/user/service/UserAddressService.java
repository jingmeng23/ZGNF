package com.gas.ecommerce.modules.user.service;
// MyBatis-Plus 3.5.17 将 Spring Service 接口包从 extension.service 调整到了 spring.service。
import com.baomidou.mybatisplus.spring.service.IService;
import com.gas.ecommerce.modules.user.entity.UserAddress;
/**
 * 业务服务接口，供 Controller 调用；继承 IService 后包含通用 CRUD 契约。
 */
public interface UserAddressService extends IService<UserAddress> {
    java.util.List<com.gas.ecommerce.modules.user.vo.UserAddressVO> listMine(Long userId);
    com.gas.ecommerce.modules.user.vo.UserAddressVO create(Long userId, com.gas.ecommerce.modules.user.dto.AddressRequest request);
    com.gas.ecommerce.modules.user.vo.UserAddressVO update(Long userId, Long addressId, com.gas.ecommerce.modules.user.dto.AddressRequest request);
    void delete(Long userId, Long addressId);
    com.gas.ecommerce.modules.user.vo.UserAddressVO setDefault(Long userId, Long addressId);
}
