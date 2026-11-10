package com.gas.ecommerce.modules.payment.wechat;

import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.common.security.JwtTokenProvider;
import com.gas.ecommerce.modules.auth.dto.LoginResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bind only to a separately authenticated existing mall account. */
@Service
public class WechatIdentityService {
    private final JdbcTemplate db;
    private final WechatClient client;
    private final WechatProperties config;
    private final JwtTokenProvider tokens;
    public WechatIdentityService(JdbcTemplate db,WechatClient client,WechatProperties config,JwtTokenProvider tokens) {
        this.db=db; this.client=client; this.config=config; this.tokens=tokens;
    }
    @Transactional
    public void bind(Long userId,String code) {
        String openId=client.openId(code);
        var user=db.queryForList("SELECT id FROM `user` WHERE id=? AND is_deleted=0 AND status='active' FOR UPDATE",userId);
        if(user.isEmpty()) throw new BusinessException("商城账号已停用");
        var existing=db.queryForList("SELECT user_id FROM wechat_mini_binding WHERE app_id=? AND open_id=?",config.getAppId(),openId);
        if(!existing.isEmpty()) {
            if(((Number)existing.getFirst().get("user_id")).longValue()==userId) return;
            throw new BusinessException("此微信已绑定其他商城账号");
        }
        try { db.update("INSERT INTO wechat_mini_binding(app_id,open_id,user_id) VALUES(?,?,?)",config.getAppId(),openId,userId); }
        catch(DuplicateKeyException ex) { throw new BusinessException("商城账号或微信已绑定，请联系平台处理"); }
    }
    public LoginResponse login(String code) {
        String openId=client.openId(code);
        var ids=db.queryForList("SELECT u.id FROM wechat_mini_binding b JOIN `user` u ON u.id=b.user_id WHERE b.app_id=? AND b.open_id=? AND u.is_deleted=0 AND u.status='active'",Long.class,config.getAppId(),openId);
        if(ids.isEmpty()) throw new BusinessException("请先使用商城账号登录并绑定微信");
        Long id=ids.getFirst();
        return new LoginResponse(tokens.generateAccessToken(id,"user"),tokens.generateRefreshToken(id),7200);
    }
    public String openId(Long userId) {
        var ids=db.queryForList("SELECT b.open_id FROM wechat_mini_binding b JOIN `user` u ON u.id=b.user_id WHERE b.app_id=? AND b.user_id=? AND u.is_deleted=0 AND u.status='active'",String.class,config.getAppId(),userId);
        if(ids.isEmpty()) throw new BusinessException("请在小程序中绑定当前微信后支付");
        return ids.getFirst();
    }
    public String paymentOpenId(Long userId) {
        // Historical receipts must still settle if an account is disabled after collecting money.
        var ids=db.queryForList("SELECT open_id FROM wechat_mini_binding WHERE app_id=? AND user_id=?",String.class,config.getAppId(),userId);
        if(ids.isEmpty()) throw new BusinessException("支付用户的微信绑定记录缺失");
        return ids.getFirst();
    }
}
