package com.gas.ecommerce.modules.inventory.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.common.result.*;
import com.gas.ecommerce.modules.inventory.dto.*;
import com.gas.ecommerce.modules.inventory.entity.Inventory;
import com.gas.ecommerce.modules.inventory.mapper.InventoryMapper;
import com.gas.ecommerce.modules.inventory.service.InventoryService;
import com.gas.ecommerce.modules.inventory.vo.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryServiceImpl extends ServiceImpl<InventoryMapper, Inventory> implements InventoryService {
    private final JdbcTemplate jdbc;
    public InventoryServiceImpl(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final String VIEW="SELECT i.*,p.name AS product_name,s.name AS store_name FROM inventory i LEFT JOIN product p ON p.id=i.product_id LEFT JOIN store s ON s.id=i.store_id";
    private final RowMapper<InventoryVO> stockMapper=(r,n)->new InventoryVO(r.getLong("id"),r.getLong("product_id"),r.getString("product_name"),r.getObject("store_id",Long.class),r.getObject("store_id")==null?"总部仓":r.getString("store_name"),r.getInt("quantity"),r.getInt("locked_quantity"),Math.max(0,r.getInt("quantity")-r.getInt("locked_quantity")),r.getInt("version"),r.getTimestamp("create_time").toLocalDateTime(),r.getTimestamp("update_time").toLocalDateTime());
    private final RowMapper<InventoryLogVO> logMapper=(r,n)->new InventoryLogVO(r.getLong("id"),r.getObject("inventory_id",Long.class),r.getLong("product_id"),r.getObject("store_id",Long.class),r.getString("change_type"),r.getInt("change_quantity"),integer(r,"quantity_before"),integer(r,"quantity_after"),integer(r,"locked_before"),integer(r,"locked_after"),r.getObject("operator_id",Long.class),r.getString("request_id"),r.getString("reference_no"),r.getString("remark"),r.getTimestamp("create_time").toLocalDateTime());
    private static Integer integer(ResultSet r,String name) throws SQLException { return r.getObject(name,Integer.class); }

    @Override public InventoryOptionsVO options() {
        RowMapper<InventoryOptionsVO.Option> mapper = (r,n) -> new InventoryOptionsVO.Option(r.getLong("id"),r.getString("name"));
        return new InventoryOptionsVO(
                jdbc.query("SELECT id,name FROM product WHERE is_deleted=0 ORDER BY name,id",mapper),
                jdbc.query("SELECT id,name FROM store WHERE is_deleted=0 AND status='active' ORDER BY name,id",mapper));
    }

    @Override public PageResult<InventoryVO> list(InventoryQuery q) {
        StringBuilder where=new StringBuilder(" WHERE i.is_deleted=0"); List<Object> args=new ArrayList<>();
        filter(where,args,"i.product_id",q.getProductId()); warehouse(where,args,"i.",q.getStoreId(),q.getHeadquarters());
        if(q.getKeyword()!=null&&!q.getKeyword().isBlank()) {where.append(" AND p.name LIKE ?");args.add("%"+q.getKeyword().trim()+"%");}
        if(q.getAvailableMax()!=null) {where.append(" AND GREATEST(0,i.quantity-i.locked_quantity)<=?");args.add(q.getAvailableMax());}
        String sort=q.resolveSort(Map.of("id","i.id","createTime","i.create_time","updateTime","i.update_time","quantity","i.quantity","availableQuantity","(i.quantity-i.locked_quantity)"));
        return page(VIEW,"SELECT COUNT(*) FROM inventory i LEFT JOIN product p ON p.id=i.product_id",where,args,q,sort,stockMapper);
    }
    @Override public InventoryVO detail(Long id) {
        var rows=jdbc.query(VIEW+" WHERE i.id=? AND i.is_deleted=0",stockMapper,id);
        if(rows.isEmpty()) throw new BusinessException("库存记录不存在"); return rows.getFirst();
    }
    @Override public PageResult<InventoryLogVO> logs(InventoryLogQuery q) {
        StringBuilder where=new StringBuilder(" WHERE is_deleted=0");List<Object> args=new ArrayList<>();
        filter(where,args,"inventory_id",q.getInventoryId());filter(where,args,"product_id",q.getProductId());
        filter(where,args,"operator_id",q.getOperatorId());filter(where,args,"change_type",q.getChangeType());filter(where,args,"reference_no",q.getReferenceNo());
        warehouse(where,args,"",q.getStoreId(),q.getHeadquarters());
        return page("SELECT * FROM inventory_log","SELECT COUNT(*) FROM inventory_log",where,args,q,q.resolveSort(Map.of("id","id","createTime","create_time")),logMapper);
    }
    private <T> PageResult<T> page(String select,String count,StringBuilder where,List<Object> args,PageRequest q,String sort,RowMapper<T> mapper) {
        long total=jdbc.queryForObject(count+where,Long.class,args.toArray());
        List<Object> paged=new ArrayList<>(args);paged.add(q.getSize());paged.add(((long)q.getPage()-1)*q.getSize());
        var rows=jdbc.query(select+where+" ORDER BY "+sort+" "+("asc".equals(q.getOrder())?"ASC":"DESC")+", id "+("asc".equals(q.getOrder())?"ASC":"DESC")+" LIMIT ? OFFSET ?",mapper,paged.toArray());
        return new PageResult<>(rows,total,q.getPage(),q.getSize(),(total+q.getSize()-1)/q.getSize());
    }
    private static void filter(StringBuilder w,List<Object> args,String column,Object value) {if(value!=null){w.append(" AND ").append(column).append("=?");args.add(value);}}
    private static void warehouse(StringBuilder w,List<Object> args,String prefix,Long store,Boolean hq) {
        if(store!=null&&Boolean.TRUE.equals(hq)) throw new BusinessException("总部仓筛选不能同时指定门店");
        filter(w,args,prefix+"store_id",store);
        if(hq!=null) w.append(" AND ").append(prefix).append("store_id IS ").append(hq?"NULL":"NOT NULL");
    }
    // Same lock order as organizational/catalog changes; order transactions only lock catalog/inventory.
    private void lock(Long operator) {
        jdbc.queryForObject("SELECT id FROM management_lock WHERE id=1 FOR UPDATE",Integer.class);
        jdbc.queryForObject("SELECT id FROM catalog_lock WHERE id=1 FOR UPDATE",Integer.class);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM employee WHERE id=? AND status='active' AND is_deleted=0",Integer.class,operator)!=1) throw new BusinessException("操作员工不可用");
    }
    private void validateLocation(Long product,Long store) {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE id=? AND is_deleted=0",Integer.class,product)!=1) throw new BusinessException("商品不存在");
        if(store!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM store WHERE id=? AND is_deleted=0 AND status='active'",Integer.class,store)!=1) throw new BusinessException("门店不存在或已停用");
    }
    private InventoryLogVO retry(Long operator,String key,String type,Long id,Long product,Long store,int amount,String remark) {
        var rows=jdbc.query("SELECT * FROM inventory_log WHERE operator_id=? AND request_id=?",logMapper,operator,key);
        if(rows.isEmpty())return null;
        var old=rows.getFirst();
        Integer requested=jdbc.queryForObject("SELECT requested_quantity FROM inventory_log WHERE id=?",Integer.class,old.id());
        if(!type.equals(old.changeType())||!Objects.equals(requested,amount)||!Objects.equals(remark,old.remark())||
            (id!=null&&!id.equals(old.inventoryId()))||(id==null&&(!Objects.equals(product,old.productId())||!Objects.equals(store,old.storeId())))) throw new BusinessException("请求编号已用于不同库存操作");
        return old;
    }
    @Override @Transactional public InventoryLogVO create(Long operator,InventoryCreateRequest r) {
        lock(operator);var old=retry(operator,r.requestId(),"initial",null,r.productId(),r.storeId(),r.quantity(),r.remark());if(old!=null)return old;
        validateLocation(r.productId(),r.storeId());
        int count=jdbc.queryForObject("SELECT COUNT(*) FROM inventory WHERE product_id=? AND "+(r.storeId()==null?"store_id IS NULL":"store_id=?"),Integer.class,r.storeId()==null?new Object[]{r.productId()}:new Object[]{r.productId(),r.storeId()});
        if(count>0)throw new BusinessException("该商品在此仓库已有库存记录，请使用入库接口");
        Inventory entity=new Inventory();entity.setProductId(r.productId());entity.setStoreId(r.storeId());entity.setQuantity(r.quantity());entity.setLockedQuantity(0);entity.setVersion(0);entity.setIsDeleted(0);baseMapper.insert(entity);
        return writeLog(operator,entity.getId(),r.productId(),r.storeId(),"initial",r.quantity(),r.quantity(),0,r.quantity(),0,0,r.requestId(),r.remark());
    }
    @Override @Transactional public InventoryLogVO inbound(Long operator,Long id,InventoryChangeRequest r) {return change(operator,id,"inbound",r.quantity(),r.requestId(),r.remark());}
    @Override @Transactional public InventoryLogVO outbound(Long operator,Long id,InventoryChangeRequest r) {return change(operator,id,"outbound",r.quantity(),r.requestId(),r.remark());}
    @Override @Transactional public InventoryLogVO adjust(Long operator,Long id,InventoryAdjustRequest r) {return change(operator,id,"adjustment",r.quantity(),r.requestId(),r.remark());}
    private InventoryLogVO change(Long operator,Long id,String type,int amount,String key,String remark) {
        lock(operator);var old=retry(operator,key,type,id,null,null,amount,remark);if(old!=null)return old;
        if(jdbc.query("SELECT id FROM inventory WHERE id=? AND is_deleted=0 FOR UPDATE",(r,n)->r.getLong(1),id).isEmpty())throw new BusinessException("库存记录不存在");
        var before=detail(id);
        long target=switch(type) {case "inbound"->(long)before.quantity()+amount;case "outbound"->(long)before.quantity()-amount;default->amount;};
        if(target<before.lockedQuantity())throw new BusinessException("可用库存不足，不能减少订单已锁定的库存");
        if(target>Integer.MAX_VALUE)throw new BusinessException("库存数量超过上限");
        if(target>before.quantity()) validateLocation(before.productId(),before.storeId());
        jdbc.update("UPDATE inventory SET quantity=?,version=version+1,update_time=CURRENT_TIMESTAMP WHERE id=?",target,id);
        return writeLog(operator,id,before.productId(),before.storeId(),type,(int)target-before.quantity(),amount,before.quantity(),(int)target,before.lockedQuantity(),before.lockedQuantity(),key,remark);
    }
    private InventoryLogVO writeLog(Long operator,Long id,Long product,Long store,String type,int delta,int requested,int before,int after,int lockedBefore,int lockedAfter,String key,String remark) {
        jdbc.update("INSERT INTO inventory_log(inventory_id,product_id,store_id,operator_id,change_type,change_quantity,requested_quantity,quantity_before,quantity_after,locked_before,locked_after,request_id,remark) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",id,product,store,operator,type,delta,requested,before,after,lockedBefore,lockedAfter,key,remark);
        return jdbc.queryForObject("SELECT * FROM inventory_log WHERE operator_id=? AND request_id=?",logMapper,operator,key);
    }
}
