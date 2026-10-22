package com.gas.ecommerce.modules.user.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gas.ecommerce.modules.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
/**
 * 数据库访问接口，继承 BaseMapper 后可使用通用增删改查。
 */
@Mapper public interface UserMapper extends BaseMapper<User> {
    @org.apache.ibatis.annotations.Select("SELECT id, status FROM `user` WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    User lockActiveUser(@org.apache.ibatis.annotations.Param("id") Long id);
}
