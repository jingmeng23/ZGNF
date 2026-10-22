package com.gas.ecommerce.modules.system.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gas.ecommerce.modules.system.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;
/**
 * 数据库访问接口，继承 BaseMapper 后可使用通用增删改查。
 */
@Mapper public interface OperationLogMapper extends BaseMapper<OperationLog> { }
