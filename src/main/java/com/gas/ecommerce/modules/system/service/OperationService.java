package com.gas.ecommerce.modules.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.common.result.PageRequest;
import com.gas.ecommerce.modules.system.mapper.OperationLogMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationService {
    private final JdbcTemplate db;
    private final OperationLogMapper logs;
    public OperationService(JdbcTemplate db, OperationLogMapper logs) { this.db=db; this.logs=logs; }

    public List<Map<String,Object>> configs(){ return db.query("SELECT config_key,config_value,description FROM system_config WHERE is_deleted=0 ORDER BY config_key",(rs,n)->{ Map<String,Object> row=new LinkedHashMap<>(); row.put("configKey",rs.getString("config_key")); row.put("configValue",rs.getString("config_value")); row.put("description",rs.getString("description")); return row; }); }
    @Transactional public void putConfig(String key,String value,String description){ int n=db.update("UPDATE system_config SET config_value=?,description=?,update_time=NOW() WHERE config_key=? AND is_deleted=0",value,description,key); if(n==0) db.update("INSERT INTO system_config(config_key,config_value,description) VALUES(?,?,?)",key,value,description); }
    public List<Map<String,Object>> hotwords(){ return db.query("SELECT id,word,search_count,status FROM search_hotword WHERE is_deleted=0 ORDER BY search_count DESC,id DESC",(rs,n)->{ Map<String,Object> row=new LinkedHashMap<>(); row.put("id",rs.getLong("id")); row.put("word",rs.getString("word")); row.put("searchCount",rs.getLong("search_count")); row.put("status",rs.getString("status")); return row; }); }
    public void saveHotword(Long id,String word,Long count,String status){ if(id==null) db.update("INSERT INTO search_hotword(word,search_count,status) VALUES(?,?,?)",word,count,status); else if(db.update("UPDATE search_hotword SET word=?,search_count=?,status=?,update_time=NOW() WHERE id=? AND is_deleted=0",word,count,status,id)==0) throw new BusinessException("热搜词不存在"); }
    public void deleteHotword(Long id){ if(db.update("UPDATE search_hotword SET is_deleted=1,update_time=NOW() WHERE id=? AND is_deleted=0",id)==0) throw new BusinessException("热搜词不存在"); }
    public IPage<com.gas.ecommerce.modules.system.entity.OperationLog> operationLogs(PageRequest q){ return logs.selectPage(new Page<>(q.getPage(),q.getSize()), com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.gas.ecommerce.modules.system.entity.OperationLog>lambdaQuery().orderByDesc(com.gas.ecommerce.modules.system.entity.OperationLog::getCreateTime)); }
}
