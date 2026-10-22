package com.gas.ecommerce.modules.workorder.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gas.ecommerce.modules.workorder.entity.WorkOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
/**
 * 数据库访问接口，继承 BaseMapper 后可使用通用增删改查。
 */
@Mapper public interface WorkOrderMapper extends BaseMapper<WorkOrder> {
    @Select("SELECT * FROM work_order WHERE id=#{id} AND is_deleted=0 FOR UPDATE")
    WorkOrder lockById(@Param("id") Long id);
}
