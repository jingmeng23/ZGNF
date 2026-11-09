package com.gas.ecommerce.modules.workorder.service.impl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.common.result.PageRequest;
import com.gas.ecommerce.common.result.PageResult;
import com.gas.ecommerce.common.utils.EncryptUtils;
import com.gas.ecommerce.modules.order.vo.OrderAddressVO;
import com.gas.ecommerce.modules.system.entity.Employee;
import com.gas.ecommerce.modules.user.entity.UserAddress;
import com.gas.ecommerce.modules.user.mapper.UserAddressMapper;
import com.gas.ecommerce.modules.user.mapper.UserMapper;
import com.gas.ecommerce.modules.workorder.dto.WorkOrderActionRequest;
import com.gas.ecommerce.modules.workorder.dto.WorkOrderAssignRequest;
import com.gas.ecommerce.modules.workorder.dto.WorkOrderCreateRequest;
import com.gas.ecommerce.modules.workorder.dto.WorkOrderQuery;
import com.gas.ecommerce.modules.workorder.entity.WorkOrder;
import com.gas.ecommerce.modules.workorder.entity.WorkOrderLog;
import com.gas.ecommerce.modules.workorder.mapper.WorkOrderLogMapper;
import com.gas.ecommerce.modules.workorder.mapper.WorkOrderMapper;
import com.gas.ecommerce.modules.workorder.service.WorkOrderService;
import com.gas.ecommerce.modules.workorder.vo.WorkOrderLogVO;
import com.gas.ecommerce.modules.workorder.vo.WorkOrderVO;
import com.gas.ecommerce.modules.workorder.vo.WorkOrderOptionsVO;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
// MyBatis-Plus 3.5.17 将 Spring Service 接口包从 extension.service 调整到了 spring.service。
/**
 * 工单状态与日志在同一事务内变更，组织锁保护派单和员工权限校验。
 */
@Service
public class WorkOrderServiceImpl extends ServiceImpl<WorkOrderMapper, WorkOrder> implements WorkOrderService {
    private final UserMapper users;
    private final UserAddressMapper addresses;
    private final WorkOrderLogMapper history;
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final String key;
    public WorkOrderServiceImpl(UserMapper users,
            UserAddressMapper addresses,
            WorkOrderLogMapper history,
            JdbcTemplate db,ObjectMapper json,
            @Value("${app.encryption-key}") String key) {
        this.users=users; this.addresses=addresses; this.history=history; this.db=db; this.json=json; this.key=key;
    }
    @Override @Transactional
    public WorkOrderVO create(Long userId,WorkOrderCreateRequest r) {
        lockManagement(); active(userId,true);
        var existing=getOne(Wrappers.<WorkOrder>lambdaQuery()
                .eq(WorkOrder::getUserId,userId).eq(WorkOrder::getRequestId,r.requestId()));
        if(existing!=null) return view(existing);
        if(r.appointmentTime()==null || !r.appointmentTime().isAfter(LocalDateTime.now())) throw error("预约时间必须晚于当前时间");
        var a=addresses.selectOne(Wrappers.<UserAddress>lambdaQuery()
                .eq(UserAddress::getId,r.addressId())
                .eq(UserAddress::getUserId,userId));
        if(a==null) throw error("服务地址不存在");
        WorkOrder w=new WorkOrder(); w.setUserId(userId);
        w.setWorkOrderNo("WO"+UUID.randomUUID().toString().replace("-",""));
        w.setRequestId(r.requestId()); w.setType(r.type()); w.setTitle(r.title()); w.setDescription(r.description());
        w.setStatus("pending"); w.setAppointmentTime(r.appointmentTime()); w.setIsDeleted(0);
        try { w.setAddressSnapshot(json.writeValueAsString(new OrderAddressVO(
                a.getReceiver(),a.getPhone(),a.getProvince(),a.getCity(),a.getDistrict(),a.getDetail()))); }
        catch(JsonProcessingException ex) { throw new IllegalStateException("服务地址序列化失败",ex); }
        save(w); log(w,userId,"user","created","提交服务预约"); return view(w);
    }
    @Override public PageResult<WorkOrderVO> listMine(Long userId,WorkOrderQuery q) {
        active(userId,false); return pageOrders(userId,q);
    }
    @Override public PageResult<WorkOrderVO> listAdmin(WorkOrderQuery q) { return pageOrders(null,q); }
    @Override public WorkOrderOptionsVO options() {
        var candidates=db.query("SELECT DISTINCT e.id,COALESCE(e.name,e.username) AS name FROM employee e JOIN employee_role er ON er.employee_id=e.id AND er.is_deleted=0 JOIN role r ON r.id=er.role_id AND r.status='active' AND r.is_deleted=0 JOIN role_permission rp ON rp.role_id=r.id AND rp.is_deleted=0 JOIN permission p ON p.id=rp.permission_id AND p.is_deleted=0 WHERE e.status='active' AND e.is_deleted=0 AND p.permission_key='workorder:process' ORDER BY e.id",
                (rs,n)->new WorkOrderOptionsVO.EmployeeOption(rs.getLong("id"),rs.getString("name")));
        return new WorkOrderOptionsVO(candidates);
    }
    @Override public PageResult<WorkOrderVO> listAssigned(Long employeeId,WorkOrderQuery q) {
        employee(employeeId); return pageOrders(null,q,employeeId);
    }
    @Override public WorkOrderVO detailAssigned(Long employeeId,Long id) {
        employee(employeeId); var w=require(id,false);
        if(!employeeId.equals(w.getEmployeeId())) throw new AccessDeniedException("仅可查看本人被指派的工单");
        return view(w);
    }
    @Override public PageResult<WorkOrderLogVO> logsAssigned(Long employeeId,Long id,PageRequest q) {
        detailAssigned(employeeId,id); return logs(null,id,q);
    }
    private PageResult<WorkOrderVO> pageOrders(Long userId,WorkOrderQuery q) {
        return pageOrders(userId,q,null);
    }
    private PageResult<WorkOrderVO> pageOrders(Long userId,WorkOrderQuery q,Long assignedId) {
        String sort=q.resolveSort(Map.of("createTime","create_time","updateTime","update_time","appointmentTime","appointment_time"));
        var w=Wrappers.<WorkOrder>lambdaQuery().eq(userId!=null,WorkOrder::getUserId,userId)
                .eq(q.getStatus()!=null,WorkOrder::getStatus,q.getStatus()).eq(q.getType()!=null,WorkOrder::getType,q.getType())
                .eq(q.getEmployeeId()!=null,WorkOrder::getEmployeeId,q.getEmployeeId());
        w.eq(assignedId!=null,WorkOrder::getEmployeeId,assignedId);
        if(Boolean.TRUE.equals(q.getOverdue())) w.in(WorkOrder::getStatus,List.of("pending","accepted","processing")).lt(WorkOrder::getAppointmentTime,LocalDateTime.now());
        if(q.getKeyword()!=null && !q.getKeyword().isBlank()) w.and(x->x.like(WorkOrder::getTitle,q.getKeyword()).or().like(WorkOrder::getWorkOrderNo,q.getKeyword()));
        w.last("ORDER BY "+sort+("asc".equals(q.getOrder())?" ASC":" DESC")+", id DESC");
        var p=page(new Page<>(q.getPage(),q.getSize()),w);
        return new PageResult<>(p.getRecords().stream().map(this::view).toList(),p.getTotal(),p.getCurrent(),p.getSize(),p.getPages());
    }
    @Override public WorkOrderVO detail(Long userId,Long id) {active(userId,false); return view(owned(userId,id,false));}
    @Override public WorkOrderVO detailAdmin(Long id) {return view(require(id,false));}
    @Override public PageResult<WorkOrderLogVO> logs(Long userId,Long id,PageRequest q) {
        if(userId!=null) {active(userId,false); owned(userId,id,false);} else require(id,false);
        var p=history.selectPage(new Page<>(q.getPage(),q.getSize()),
                Wrappers.<WorkOrderLog>lambdaQuery()
                        .eq(WorkOrderLog::getWorkOrderId,id)
                        .orderByAsc(WorkOrderLog::getId));
        return new PageResult<>(p.getRecords().stream().map(l->new WorkOrderLogVO(
                l.getId(),l.getOperatorId(),l.getOperatorType(),l.getAction(),l.getContent(),l.getCreateTime())).toList(),p.getTotal(),p.getCurrent(),p.getSize(),p.getPages());
    }
    @Override @Transactional
    public WorkOrderVO cancel(Long userId,Long id,WorkOrderActionRequest r) {
        lockManagement(); active(userId,false); var w=owned(userId,id,true);
        if("cancelled".equals(w.getStatus())) return view(w);
        if(!Set.of("pending","accepted").contains(w.getStatus())) throw error("仅待派单或已接单工单可取消");
        w.setStatus("cancelled"); updateById(w); log(w,userId,"user","cancelled",r.remark()); return view(w);
    }
    @Override @Transactional
    public WorkOrderVO assign(Long operatorId,Long id,WorkOrderAssignRequest r) {
        lockManagement(); employee(operatorId); var w=require(id,true);
        if(!Set.of("pending","accepted").contains(w.getStatus())) throw error("当前工单不能派单或改派");
        employee(r.employeeId());
        long capable=db.queryForObject("SELECT COUNT(*) FROM employee_role er JOIN role r ON r.id=er.role_id AND r.status='active' AND r.is_deleted=0 JOIN role_permission rp ON rp.role_id=r.id AND rp.is_deleted=0 JOIN permission p ON p.id=rp.permission_id AND p.is_deleted=0 WHERE er.employee_id=? AND p.permission_key='workorder:process'",Long.class,r.employeeId());
        if(capable==0) throw error("服务人员未获工单处理权限");
        if("accepted".equals(w.getStatus()) && r.employeeId().equals(w.getEmployeeId())) return view(w);
        String prior="原员工="+w.getEmployeeId();
        w.setEmployeeId(r.employeeId()); w.setStatus("accepted"); w.setAcceptedTime(LocalDateTime.now());
        updateById(w); log(w,operatorId,"employee","assigned",prior+"; 新员工="+r.employeeId()+"; "+r.remark()); return view(w);
    }
    @Override @Transactional
    public WorkOrderVO start(Long operatorId,Long id,WorkOrderActionRequest r) {
        lockManagement(); var w=require(id,true); assignee(operatorId,w);
        if("processing".equals(w.getStatus())) return view(w);
        if(!"accepted".equals(w.getStatus())) throw error("仅已接单工单可开始处理");
        w.setStatus("processing"); w.setStartedTime(LocalDateTime.now()); updateById(w);
        log(w,operatorId,"employee","started",r.remark()); return view(w);
    }
    @Override @Transactional
    public WorkOrderVO complete(Long operatorId,Long id,WorkOrderActionRequest r) {
        lockManagement(); var w=require(id,true); assignee(operatorId,w);
        if("completed".equals(w.getStatus())) {
            if(!Objects.equals(r.remark(),w.getResult())) throw error("已完结工单不能修改处理结果");
            return view(w);
        }
        if(!"processing".equals(w.getStatus())) throw error("仅处理中工单可完结");
        w.setStatus("completed"); w.setCompletedTime(LocalDateTime.now()); w.setResult(r.remark()); updateById(w);
        log(w,operatorId,"employee","completed",r.remark()); return view(w);
    }
    private void assignee(Long id,WorkOrder w) {
        employee(id);
        if(!id.equals(w.getEmployeeId()))
            throw new AccessDeniedException("仅指派的服务人员可处理工单");
    }
    private Employee employee(Long id) {
        var e=db.query("SELECT * FROM employee WHERE id=? AND is_deleted=0 AND status='active'",new BeanPropertyRowMapper<>(Employee.class),id);
        if(e.isEmpty()) throw error("员工不存在或已停用"); return e.getFirst();
    }
    private void lockManagement() {db.queryForObject("SELECT id FROM management_lock WHERE id=1 FOR UPDATE",Integer.class);}
    private void active(Long id,boolean lock) {var u=lock?users.lockActiveUser(id):users.selectById(id); if(u==null || !"active".equals(u.getStatus())) throw error("用户不存在或已停用");}
    private WorkOrder require(Long id,boolean lock) {var w=lock?baseMapper.lockById(id):getById(id); if(w==null) throw error("工单不存在"); return w;}
    private WorkOrder owned(Long userId,Long id,boolean lock) {var w=require(id,lock); if(!userId.equals(w.getUserId())) throw error("工单不存在"); return w;}
    private void log(WorkOrder w,Long id,String type,String action,String content) {
        var l=new WorkOrderLog(); l.setWorkOrderId(w.getId()); l.setOperatorId(id);
        l.setOperatorType(type); l.setAction(action); l.setContent(content); l.setIsDeleted(0); history.insert(l);
    }
    private WorkOrderVO view(WorkOrder w) {
        OrderAddressVO address=null;
        if(w.getAddressSnapshot()!=null) try {
            var node=json.readTree(w.getAddressSnapshot()); for(int n=0;n<4 && node.isTextual();n++) node=json.readTree(node.asText());
            var a=json.treeToValue(node,OrderAddressVO.class);
            address=new OrderAddressVO(a.receiver(),EncryptUtils.decryptAes(a.phone(),key),a.province(),a.city(),a.district(),a.detail());
        } catch(JsonProcessingException ex) {throw new IllegalStateException("服务地址读取失败",ex);}
        return new WorkOrderVO(w.getId(),w.getWorkOrderNo(),w.getUserId(),w.getEmployeeId(),w.getTitle(),w.getDescription(),w.getType(),w.getStatus(),address,w.getAppointmentTime(),w.getAcceptedTime(),w.getStartedTime(),w.getCompletedTime(),w.getResult(),w.getCreateTime(),w.getUpdateTime());
    }
    private BusinessException error(String message) {return new BusinessException(message);}
}
