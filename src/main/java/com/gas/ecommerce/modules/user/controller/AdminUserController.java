package com.gas.ecommerce.modules.user.controller;

import com.gas.ecommerce.common.annotation.RequiresPermission;
import com.gas.ecommerce.common.result.PageResult;
import com.gas.ecommerce.common.result.Result;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import com.gas.ecommerce.common.exception.BusinessException;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final JdbcTemplate db;
    public AdminUserController(JdbcTemplate db) { this.db = db; }

    @GetMapping @RequiresPermission("user:read")
    public Result<PageResult<Map<String,Object>>> list(@RequestParam(defaultValue="1") @Min(1) int page,
            @RequestParam(defaultValue="10") @Min(1) @Max(100) int size,
            @RequestParam(required=false) String keyword, @RequestParam(required=false) String status) {
        String term = keyword == null ? "" : keyword.trim();
        String pattern = "%" + term + "%";
        String state = status == null || status.isBlank() ? null : status;
        Long total = db.queryForObject("SELECT COUNT(*) FROM `user` WHERE is_deleted=0 AND (? IS NULL OR status=?) AND (?='' OR nickname LIKE ? OR phone_last4 LIKE ? OR CAST(id AS CHAR)=?)", Long.class, state,state,term,pattern,pattern,term);
        List<Map<String,Object>> rows = db.queryForList("SELECT id,nickname,phone_last4,status,member_level,points,create_time FROM `user` WHERE is_deleted=0 AND (? IS NULL OR status=?) AND (?='' OR nickname LIKE ? OR phone_last4 LIKE ? OR CAST(id AS CHAR)=?) ORDER BY id DESC LIMIT ? OFFSET ?", state,state,term,pattern,pattern,term,size,(page-1)*size);
        long count = total == null ? 0 : total;
        return Result.success(new PageResult<>(rows,count,page,size,(count+size-1)/size));
    }

    @GetMapping("/{id}") @RequiresPermission("user:read")
    public Result<Map<String,Object>> detail(@PathVariable Long id) {
        return Result.success(db.queryForMap("SELECT id,nickname,avatar,phone_last4,status,email,gender,birthday,member_level,points,create_time,update_time FROM `user` WHERE id=? AND is_deleted=0",id));
    }

    @PutMapping("/{id}/status") @RequiresPermission("user:write")
    public Result<Void> status(@PathVariable Long id,@RequestBody Map<String,String> body) {
        String status=body.get("status");
        if (!List.of("active","inactive").contains(status)) throw new BusinessException("用户状态无效");
        if (db.update("UPDATE `user` SET status=? WHERE id=? AND is_deleted=0",status,id)==0) throw new BusinessException("用户不存在");
        return Result.success();
    }

    @DeleteMapping("/{id}") @RequiresPermission("user:write")
    public Result<Void> delete(@PathVariable Long id) {
        if (db.update("UPDATE `user` SET is_deleted=1,status='cancelled' WHERE id=? AND is_deleted=0",id)==0) throw new BusinessException("用户不存在");
        return Result.success();
    }
}
