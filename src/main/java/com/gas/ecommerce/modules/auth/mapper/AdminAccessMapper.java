package com.gas.ecommerce.modules.auth.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AdminAccessMapper {
    @Select("""
        SELECT r.role_key FROM role r JOIN employee_role er ON er.role_id = r.id
        WHERE er.employee_id = #{employeeId} AND r.status = 'active' AND r.is_deleted = 0
        """)
    List<String> roles(Long employeeId);

    @Select("""
        SELECT DISTINCT p.permission_key FROM permission p
        JOIN role_permission rp ON rp.permission_id = p.id AND rp.is_deleted = 0
        JOIN role r ON r.id = rp.role_id AND r.is_deleted = 0 AND r.status = 'active'
        JOIN employee_role er ON er.role_id = r.id
        WHERE er.employee_id = #{employeeId} AND p.is_deleted = 0
        """)
    List<String> permissions(Long employeeId);

    @Insert("INSERT INTO employee_role(employee_id, role_id) VALUES(#{employeeId}, #{roleId})")
    void assign(@Param("employeeId") Long employeeId, @Param("roleId") Long roleId);
}
