package com.gas.ecommerce.modules.user.controller;

import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.common.result.Result;
import com.gas.ecommerce.common.security.JwtTokenProvider;
import com.gas.ecommerce.common.security.SecurityUtils;
import com.gas.ecommerce.common.utils.EncryptUtils;
import com.gas.ecommerce.modules.auth.dto.LoginResponse;
import com.gas.ecommerce.modules.user.dto.*;
import com.gas.ecommerce.modules.user.vo.RegistrationVO;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/users")
public class UserManagementController {
    private final JdbcTemplate db;
    private final PasswordEncoder passwords; 
    private final JwtTokenProvider tokens; 
    private final String key;
    public UserManagementController(JdbcTemplate db, PasswordEncoder passwords, JwtTokenProvider tokens, @org.springframework.beans.factory.annotation.Value("${app.encryption-key}") String key) { this.db=db;this.passwords=passwords;this.tokens=tokens;this.key=key; }
    @PostMapping("/register") @Transactional public Result<RegistrationVO> register(@Valid @RequestBody RegisterRequest r) {
        if (!strong(r.password())) throw new BusinessException("密码需为8至64位，并包含大小写字母和数字");
        String phoneHash = EncryptUtils.fingerprint(r.phone(),key);
        if (db.queryForObject("SELECT COUNT(*) FROM `user` WHERE phone_hash=?",Long.class,phoneHash)>0)
            throw new BusinessException("该手机号已注册或关联已有账号，请直接登录或联系平台处理");
        String phone=EncryptUtils.encryptAes(r.phone(),key);
        String nickname = r.nickname() == null || r.nickname().isBlank() ? null : r.nickname().trim();
        try { db.update("INSERT INTO `user`(phone,phone_hash,phone_last4,nickname,password_hash,status) VALUES(?,?,?,?,?, 'active')",phone,phoneHash,r.phone().substring(7),nickname,passwords.encode(r.password())); }
        catch (org.springframework.dao.DuplicateKeyException e) { throw new BusinessException("该手机号已注册或关联已注销账号，请联系平台处理"); }
        Long id=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class); return Result.success(new RegistrationVO(id.toString(),r.phone().substring(7),"active"));
    }
    @PostMapping("/login/password") public Result<LoginResponse> login(@Valid @RequestBody PasswordLoginRequest r) {
        String hash=EncryptUtils.fingerprint(r.account(),key); List<Map<String,Object>> found=db.queryForList("SELECT id,password_hash,status FROM `user` WHERE phone_hash=? AND is_deleted=0",hash);
        if (found.isEmpty()) { recordLoginFailure(hash); throw new BusinessException("手机号或密码错误"); } Map<String,Object> u=found.get(0);
        if (u.get("password_hash") == null || !passwords.matches(r.password(),(String)u.get("password_hash")) || !"active".equals(u.get("status"))) { db.update("INSERT INTO user_login_log(user_id,login_type,success) VALUES(?,'password',false)",u.get("id")); throw new BusinessException("手机号或密码错误"); } Long id=((Number)u.get("id")).longValue();
        db.update("INSERT INTO user_login_log(user_id,login_type,success) VALUES(?,'password',true)",id); return Result.success(new LoginResponse(tokens.generateAccessToken(id,"user"),tokens.generateRefreshToken(id),7200));
    }
    @GetMapping("/me") public Result<Map<String,Object>> profile() { Long id=SecurityUtils.currentUserId(); return Result.success(db.queryForMap("SELECT id,nickname,avatar,email,gender,birthday,member_level,points,status FROM `user` WHERE id=?",id)); }
    @PutMapping("/me") public Result<Void> update(@RequestBody ProfileRequest r) { Long id=SecurityUtils.currentUserId(); db.update("UPDATE `user` SET nickname=?,avatar=?,email=?,email_hash=?,gender=?,birthday=? WHERE id=?",r.nickname(),r.avatar(),r.email(),r.email()==null?null:EncryptUtils.fingerprint(r.email(),key),r.gender(),r.birthday(),id); return Result.success(); }
    @PutMapping("/me/password") public Result<Void> password(@Valid @RequestBody PasswordRequest r) { if(!strong(r.newPassword())) throw new BusinessException("密码强度不足"); Long id=SecurityUtils.currentUserId(); String old=db.queryForObject("SELECT password_hash FROM `user` WHERE id=?",String.class,id); if(old==null||!passwords.matches(r.currentPassword(),old)) throw new BusinessException("原密码错误"); db.update("UPDATE `user` SET password_hash=? WHERE id=?",passwords.encode(r.newPassword()),id); return Result.success(); }
    @DeleteMapping("/me") @Transactional public Result<Void> cancel() { Long id=SecurityUtils.currentUserId(); db.update("UPDATE `user` SET status='cancelled',is_deleted=1 WHERE id=?",id); return Result.success(); }
    @GetMapping("/me/points") public Result<?> points() { Long id=SecurityUtils.currentUserId(); return Result.success(db.queryForList("SELECT change_amount,balance,reason,created_at FROM user_points_log WHERE user_id=? ORDER BY created_at DESC",id)); }
    @GetMapping("/me/security/logins") public Result<?> logins() { return Result.success(db.queryForList("SELECT login_type,ip,user_agent,success,created_at FROM user_login_log WHERE user_id=? ORDER BY created_at DESC LIMIT 100",SecurityUtils.currentUserId())); }
    @GetMapping("/me/privacy") public Result<?> privacy() { List<Map<String,Object>> rows=db.queryForList("SELECT profile_authorized,push_enabled FROM user_privacy_setting WHERE user_id=?",SecurityUtils.currentUserId()); return Result.success(rows.isEmpty()?Map.of("profile_authorized",true,"push_enabled",true):rows.getFirst()); }
    @PutMapping("/me/privacy") public Result<Void> privacy(@RequestBody Map<String,Boolean> r) { Long id=SecurityUtils.currentUserId(); db.update("INSERT INTO user_privacy_setting(user_id,profile_authorized,push_enabled) VALUES(?,?,?) ON DUPLICATE KEY UPDATE profile_authorized=VALUES(profile_authorized),push_enabled=VALUES(push_enabled)",id,r.getOrDefault("profileAuthorized",true),r.getOrDefault("pushEnabled",true)); return Result.success(); }
    @PostMapping("/me/feedback") public Result<Void> feedback(@Valid @RequestBody FeedbackRequest r) { db.update("INSERT INTO user_feedback(user_id,content,contact) VALUES(?,?,?)",SecurityUtils.currentUserId(),r.content(),r.contact()); return Result.success(); }
    @GetMapping("/me/bindings") public Result<?> bindings() { return Result.success(db.queryForList("SELECT provider,provider_uid,created_at FROM user_account_binding WHERE user_id=?",SecurityUtils.currentUserId())); }
    @PostMapping("/me/bindings/{provider}") public Result<Void> bind(@PathVariable String provider,@RequestBody Map<String,String> body) { String uid=body.get("uid"); if(uid==null||uid.isBlank()) throw new BusinessException("第三方账号标识不能为空"); try { db.update("INSERT INTO user_account_binding(user_id,provider,provider_uid) VALUES(?,?,?)",SecurityUtils.currentUserId(),provider,uid); } catch(org.springframework.dao.DuplicateKeyException e) { throw new BusinessException("账号已绑定其他用户"); } return Result.success(); }
    @DeleteMapping("/me/bindings/{provider}") public Result<Void> unbind(@PathVariable String provider) { if(db.queryForObject("SELECT COUNT(*) FROM user_account_binding WHERE user_id=?",Long.class,SecurityUtils.currentUserId())<=1) throw new BusinessException("至少保留一种登录方式"); db.update("DELETE FROM user_account_binding WHERE user_id=? AND provider=?",SecurityUtils.currentUserId(),provider); return Result.success(); }
    private boolean strong(String p) { return p.length()>=8 && p.matches(".*[A-Z].*") && p.matches(".*[a-z].*") && p.matches(".*\\d.*"); }
    private void recordLoginFailure(String phoneHash) {
        db.update("INSERT INTO user_login_log(user_id,login_type,success) SELECT id,'password',false FROM `user` WHERE phone_hash=? ORDER BY id DESC LIMIT 1",phoneHash);
    }
}
