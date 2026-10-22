package com.gas.ecommerce.modules.aftersale.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gas.ecommerce.modules.aftersale.entity.Aftersale;
import org.apache.ibatis.annotations.Mapper;
/**
 * 数据库访问接口，继承 BaseMapper 后可使用通用增删改查。
 */
@Mapper public interface AftersaleMapper extends BaseMapper<Aftersale> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM aftersale WHERE id=#{id} AND is_deleted=0 FOR UPDATE")
    Aftersale lockById(@org.apache.ibatis.annotations.Param("id") Long id);
}
