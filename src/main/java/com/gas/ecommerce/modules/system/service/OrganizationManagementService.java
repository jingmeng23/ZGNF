package com.gas.ecommerce.modules.system.service;

import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.common.result.PageResult;
import com.gas.ecommerce.common.security.SecurityUtils;
import com.gas.ecommerce.common.utils.EncryptUtils;
import com.gas.ecommerce.modules.system.dto.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 组织资料与授权的事务边界。所有关系变更共用数据库锁，避免检查与写入之间发生竞争。 */
@Service
public class OrganizationManagementService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrganizationManagementService.class);
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final String encryptionKey;
    public OrganizationManagementService(JdbcTemplate db, PasswordEncoder passwords,
            @Value("${app.encryption-key}") String encryptionKey) {
        this.db=db; this.passwords=passwords; this.encryptionKey=encryptionKey;
    }

    public PageResult<Map<String,Object>> employees(ManagementQuery q) { return page("employee",q); }
    public PageResult<Map<String,Object>> roles(ManagementQuery q) { return page("role",q); }

    /** 只返回表单需要的组织名称；无需额外授予角色权限查询权限。 */
    public Map<String,Object> employeeOptions() {
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        Set<String> authorities=new HashSet<>();
        if(auth!=null) auth.getAuthorities().forEach(a->authorities.add(a.getAuthority()));
        boolean bypass=authorities.contains("TEST_LOGIN_BYPASS");
        Map<String,Object> result=new LinkedHashMap<>();
        for(String table:List.of("department","position","role")) {
            String key=switch(table) {case "department"->"departments"; case "position"->"positions"; default->"roles";};
            result.put(key,db.query("SELECT id,name,status FROM "+table+" WHERE is_deleted=0 ORDER BY name,id",this::row));
        }
        result.put("canWrite",bypass||authorities.contains("employee:write"));
        result.put("canManageRoles",bypass||authorities.contains("role:write"));
        result.put("currentEmployeeId",SecurityUtils.currentUserId().toString());
        return result;
    }

    private PageResult<Map<String,Object>> page(String table, ManagementQuery q) {
        String sort=q.resolveSort(Map.of("createTime","create_time","updateTime","update_time","id","id","name","name","status","status"));
        StringBuilder where=new StringBuilder(" WHERE is_deleted=0");
        List<Object> args=new ArrayList<>();
        if(q.getKeyword()!=null && !q.getKeyword().isBlank()) {
            where.append(" AND (name LIKE ?"); args.add("%"+q.getKeyword()+"%");
            if("employee".equals(table)) { where.append(" OR username LIKE ?"); args.add("%"+q.getKeyword()+"%"); }
            if("role".equals(table)) { where.append(" OR role_key LIKE ?"); args.add("%"+q.getKeyword()+"%"); }
            where.append(")");
        }
        if(q.getStatus()!=null) { where.append(" AND status=?"); args.add(q.getStatus()); }
        if("employee".equals(table) && q.getRoleId()!=null) {
            where.append(" AND id IN (SELECT employee_id FROM employee_role WHERE role_id=?)"); args.add(q.getRoleId());
        }
        long total=count("SELECT COUNT(*) FROM "+table+where,args.toArray());
        args.add(q.getSize()); args.add((long)(q.getPage()-1)*q.getSize());
        var rows=db.query("SELECT * FROM "+table+where+" ORDER BY "+sort+" "+("asc".equals(q.getOrder())?"ASC":"DESC")+", id DESC LIMIT ? OFFSET ?",this::row,args.toArray());
        rows.forEach(r->enrich(table,r));
        return new PageResult<>(rows,total,q.getPage(),q.getSize(),(total+q.getSize()-1)/q.getSize());
    }

    public Map<String,Object> employee(Long id) { return detail("employee",id); }
    public Map<String,Object> role(Long id) { return detail("role",id); }
    private Map<String,Object> detail(String table,Long id) {
        var rows=db.query("SELECT * FROM "+table+" WHERE id=? AND is_deleted=0",this::row,id);
        if(rows.isEmpty()) throw new BusinessException("记录不存在");
        var value=rows.getFirst(); enrich(table,value); return value;
    }

    private Map<String,Object> row(ResultSet rs,int n) throws SQLException {
        Map<String,Object> value=new LinkedHashMap<>();
        for(int i=1;i<=rs.getMetaData().getColumnCount();i++) {
            String col=rs.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT);
            if(Set.of("password_hash","phone_last4","is_deleted").contains(col)) continue;
            Object v=rs.getObject(i);
            if(v!=null && (col.equals("id") || col.endsWith("_id"))) v=v.toString();
            if(v instanceof Timestamp t) v=t.toLocalDateTime();
            if(col.equals("phone") && v!=null) {
                try {
                    v=EncryptUtils.decryptAes(v.toString(),encryptionKey);
                } catch (IllegalStateException ex) {
                    // Historical ciphertext may belong to a previous encryption key. Keep
                    // the record accessible without exposing ciphertext or changing storage.
                    String suffix=rs.getString("phone_last4");
                    v=suffix!=null && suffix.matches("[0-9]{4}") ? "*******"+suffix : "";
                    value.put("phoneUnavailable",true);
                    log.warn("Unable to decrypt organization phone for record {}; verify encryption key or update phone",rs.getString("id"));
                }
            }
            String[] parts=col.split("_"); StringBuilder key=new StringBuilder(parts[0]);
            for(int j=1;j<parts.length;j++) key.append(Character.toUpperCase(parts[j].charAt(0))).append(parts[j].substring(1));
            value.put(key.toString(),v);
        }
        return value;
    }

    private void enrich(String table,Map<String,Object> value) {
        Object id=value.get("id");
        if("employee".equals(table)) {
            value.put("roleIds",db.query("SELECT role_id FROM employee_role WHERE employee_id=? ORDER BY role_id",(rs,n)->rs.getString(1),id));
        } else if("role".equals(table)) {
            value.put("permissionIds",db.query("SELECT permission_id FROM role_permission WHERE role_id=? AND is_deleted=0 ORDER BY permission_id",(rs,n)->rs.getString(1),id));
            value.put("members",count("SELECT COUNT(*) FROM employee_role er JOIN employee e ON e.id=er.employee_id AND e.is_deleted=0 WHERE er.role_id=?",id));
            value.put("system", "admin".equals(value.get("roleKey")));
        }
    }

    @Transactional public Map<String,Object> createEmployee(EmployeeCreateRequest r) {
        lock(); validateEmployee(r.employee()); validatePassword(r.password());
        var e=r.employee();
        Long id=insert("INSERT INTO employee(username,name,password_hash,phone,phone_last4,department_id,position_id,status) VALUES(?,?,?,?,?,?,?,?)",
                e.username(),e.name(),passwords.encode(r.password()),EncryptUtils.encryptAes(e.phone(),encryptionKey),last4(e.phone()),e.departmentId(),e.positionId(),e.status());
        return employee(id);
    }

    @Transactional public Map<String,Object> updateEmployee(Long id,EmployeeRequest r) {
        lock(); employee(id); validateEmployee(r);
        updateUnique("UPDATE employee SET username=?,name=?,phone=?,phone_last4=?,department_id=?,position_id=?,status=?,update_time=NOW() WHERE id=?",
                r.username(),r.name(),EncryptUtils.encryptAes(r.phone(),encryptionKey),last4(r.phone()),r.departmentId(),r.positionId(),r.status(),id);
        ensureAdministrator(); ensureWorkOrderStaff(); return employee(id);
    }

    @Transactional public void deleteEmployee(Long id) {
        lock(); employee(id);
        if(id.equals(SecurityUtils.currentUserId())) throw new BusinessException("不能删除当前登录账号");
        softDelete("employee",id); ensureAdministrator(); ensureWorkOrderStaff();
        db.update("DELETE FROM employee_role WHERE employee_id=?",id);
    }

    @Transactional public Map<String,Object> changeEmployeeStatus(Long id,EmployeeStatusRequest r) {
        lock(); employee(id);
        db.update("UPDATE employee SET status=?,update_time=NOW() WHERE id=? AND is_deleted=0",r.status(),id);
        ensureAdministrator(); ensureWorkOrderStaff(); return employee(id);
    }

    @Transactional public void resetPassword(Long id,PasswordRequest r) {
        lock(); employee(id); validatePassword(r.password());
        db.update("UPDATE employee SET password_hash=?,update_time=NOW() WHERE id=?",passwords.encode(r.password()),id);
    }

    @Transactional public Map<String,Object> assignRoles(Long id,AssignmentRequest r) {
        lock(); employee(id); validateIds("role",r.ids());
        db.update("DELETE FROM employee_role WHERE employee_id=?",id);
        for(Long roleId:r.ids()) db.update("INSERT INTO employee_role(employee_id,role_id) VALUES(?,?)",id,roleId);
        ensureAdministrator(); ensureWorkOrderStaff(); return employee(id);
    }

    @Transactional public Map<String,Object> saveRole(Long id,RoleRequest r) {
        lock();
        if(id!=null) {
            var old=role(id);
            if(!old.get("roleKey").equals(r.roleKey())) throw new BusinessException("角色标识创建后不能修改");
            if("admin".equals(r.roleKey()) && !"active".equals(r.status())) throw new BusinessException("内置管理员角色不能停用");
            db.update("UPDATE role SET name=?,status=?,update_time=NOW() WHERE id=?",r.name(),r.status(),id);
        } else {
            if("admin".equals(r.roleKey())) throw new BusinessException("不能创建内置管理员角色");
            id=insert("INSERT INTO role(name,role_key,status) VALUES(?,?,?)",r.name(),r.roleKey(),r.status());
        }
        if(r.permissionIds()!=null) {
            customRole(id); validateIds("permission",r.permissionIds());
            db.update("DELETE FROM role_permission WHERE role_id=?",id);
            for(Long permissionId:r.permissionIds()) db.update("INSERT INTO role_permission(role_id,permission_id) VALUES(?,?)",id,permissionId);
        }
        ensureWorkOrderStaff(); return role(id);
    }

    @Transactional public void deleteRole(Long id) {
        lock(); customRole(id);
        if(count("SELECT COUNT(*) FROM employee_role er JOIN employee e ON e.id=er.employee_id AND e.is_deleted=0 WHERE er.role_id=?",id)>0)
            throw new BusinessException("角色已分配给员工，不能删除");
        softDelete("role",id); db.update("DELETE FROM role_permission WHERE role_id=?",id);
    }

    @Transactional public Map<String,Object> assignPermissions(Long id,AssignmentRequest r) {
        lock(); customRole(id); validateIds("permission",r.ids());
        db.update("DELETE FROM role_permission WHERE role_id=?",id);
        for(Long permissionId:r.ids()) db.update("INSERT INTO role_permission(role_id,permission_id) VALUES(?,?)",id,permissionId);
        db.update("UPDATE role SET update_time=NOW() WHERE id=?",id);
        ensureWorkOrderStaff(); return role(id);
    }

    public List<Map<String,Object>> permissions() {
        return db.query("SELECT id,name,permission_key FROM permission WHERE is_deleted=0 ORDER BY permission_key",this::row);
    }

    private void validateEmployee(EmployeeRequest r) {
        reference("department",r.departmentId()); reference("position",r.positionId());
    }
    private void reference(String table,Long id) {
        if(id!=null && count("SELECT COUNT(*) FROM "+table+" WHERE id=? AND is_deleted=0 AND status='active'",id)!=1)
            throw new BusinessException("关联部门或岗位不存在或已停用");
    }
    private void validatePassword(String password) {
        if(password.getBytes(StandardCharsets.UTF_8).length>72) throw new BusinessException("密码不能超过72个UTF-8字节");
    }
    private void validateIds(String table,List<Long> ids) {
        if(new HashSet<>(ids).size()!=ids.size()) throw new BusinessException("关联ID不能重复");
        for(Long id:ids) if(count("SELECT COUNT(*) FROM "+table+" WHERE id=? AND is_deleted=0"+("role".equals(table)?" AND status='active'":""),id)!=1)
            throw new BusinessException("角色或权限不存在或已停用");
    }
    private void customRole(Long id) {
        if("admin".equals(role(id).get("roleKey"))) throw new BusinessException("内置管理员角色不能删除或调整权限");
    }
    private void ensureAdministrator() {
        if(count("SELECT COUNT(*) FROM employee e JOIN employee_role er ON er.employee_id=e.id JOIN role r ON r.id=er.role_id WHERE e.is_deleted=0 AND e.status='active' AND r.is_deleted=0 AND r.status='active' AND r.role_key='admin'")==0)
            throw new BusinessException("必须保留至少一个有效的内置管理员账号");
    }
    private void ensureWorkOrderStaff() {
        long invalid=count("""
            SELECT COUNT(*) FROM work_order w
            WHERE w.is_deleted=0 AND w.status IN ('accepted','processing') AND w.employee_id IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM employee e
                WHERE e.id=w.employee_id AND e.status='active' AND e.is_deleted=0
                  AND EXISTS (SELECT 1 FROM employee_role er JOIN role r ON r.id=er.role_id
                    JOIN role_permission rp ON rp.role_id=r.id JOIN permission p ON p.id=rp.permission_id
                    WHERE er.employee_id=e.id AND r.status='active' AND r.is_deleted=0 AND rp.is_deleted=0
                      AND p.is_deleted=0 AND p.permission_key='workorder:process'))
            """);
        if(invalid>0) throw new BusinessException("存在未完结工单，须先改派或完结，再调整员工或撤销处理权限");
    }
    private void lock() { db.queryForObject("SELECT id FROM management_lock WHERE id=1 FOR UPDATE",Integer.class); }
    private long count(String sql,Object...args) { return db.queryForObject(sql,Long.class,args); }
    private String last4(String phone) { return phone.substring(phone.length()-4); }
    private void softDelete(String table,Long id) { db.update("UPDATE "+table+" SET is_deleted=1,update_time=NOW() WHERE id=?",id); }
    private void updateUnique(String sql,Object...args) {
        try { db.update(sql,args); } catch(DuplicateKeyException ex) { throw new BusinessException("账号或角色标识已存在（含已删除记录）"); }
    }
    private Long insert(String sql,Object...args) {
        GeneratedKeyHolder key=new GeneratedKeyHolder();
        try {
            db.update(connection->{
                PreparedStatement statement=connection.prepareStatement(sql,new String[]{"id"});
                for(int i=0;i<args.length;i++) statement.setObject(i+1,args[i]);
                return statement;
            },key);
        } catch(DuplicateKeyException ex) { throw new BusinessException("账号或角色标识已存在（含已删除记录）"); }
        return Objects.requireNonNull(key.getKey()).longValue();
    }
}
