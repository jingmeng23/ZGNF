package com.gas.ecommerce.modules.coupon.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.common.result.PageResult;
import com.gas.ecommerce.modules.coupon.dto.UserCouponQuery;
import com.gas.ecommerce.modules.coupon.entity.UserCoupon;
import com.gas.ecommerce.modules.coupon.mapper.*;
import com.gas.ecommerce.modules.coupon.vo.UserCouponVO;
import com.gas.ecommerce.modules.order.entity.Order;
import com.gas.ecommerce.modules.system.dto.*;
import com.gas.ecommerce.modules.system.entity.Coupon;
import com.gas.ecommerce.modules.user.mapper.UserMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponService {
    private final CouponMapper coupons;
    private final UserCouponMapper owned;
    private final UserMapper users;
    public CouponService(CouponMapper coupons, UserCouponMapper owned, UserMapper users) {
        this.coupons=coupons; this.owned=owned; this.users=users;
    }
    public IPage<Coupon> coupons(CouponQuery q) {
        String sort=q.resolveSort(Map.of("createTime","create_time","amount","amount","startTime","start_time","endTime","end_time"));
        var now=LocalDateTime.now();
        var w=Wrappers.<Coupon>lambdaQuery().like(q.getKeyword()!=null && !q.getKeyword().isBlank(),Coupon::getName,q.getKeyword());
        if ("expired".equals(q.getStatus())) w.and(x->x.eq(Coupon::getStatus,"expired").or().le(Coupon::getEndTime,now));
        else if(q.getStatus()!=null) w.eq(Coupon::getStatus,q.getStatus()).gt(Coupon::getEndTime,now);
        w.last("ORDER BY "+sort+" "+("asc".equals(q.getOrder())?"ASC":"DESC")+", id DESC");
        var page=coupons.selectPage(new Page<Coupon>(q.getPage(),q.getSize()),w);
        page.getRecords().forEach(this::decorate); return page;
    }
    public Coupon detail(Long id) {
        Coupon c=coupons.selectById(id); if(c==null) throw error("优惠券不存在"); return decorate(c);
    }
    @Transactional public Coupon saveCoupon(Long id,CouponRequest r) {
        if(!r.endTime().isAfter(r.startTime())) throw error("结束时间必须晚于开始时间");
        Coupon c=id==null?new Coupon():coupons.lockById(id);
        if(c==null) throw error("优惠券不存在");
        long count=id==null?0:owned.claimedCount(id);
        if(r.quantity()<count) throw error("发行总量不能少于已领取数量");
        if(count>0 && (c.getAmount().compareTo(r.amount())!=0 || c.getMinimumAmount().compareTo(r.minimumAmount())!=0
                || !c.getStartTime().equals(r.startTime()) || !c.getEndTime().equals(r.endTime())))
            throw error("已有用户领取，不能修改面额、门槛和有效期");
        c.setName(r.name()); c.setAmount(r.amount()); c.setMinimumAmount(r.minimumAmount());
        c.setStartTime(r.startTime()); c.setEndTime(r.endTime()); c.setQuantity(r.quantity());
        c.setStatus(r.status()==null?"active":r.status());
        if(id==null) { c.setIsDeleted(0); coupons.insert(c); } else coupons.updateById(c);
        return decorate(c);
    }
    @Transactional public void deleteCoupon(Long id) {
        if(coupons.lockById(id)==null) throw error("优惠券不存在"); coupons.deleteById(id);
    }
    public PageResult<Coupon> available(Long userId,CouponQuery q) {
        active(userId,false);
        var now=LocalDateTime.now();
        var page=coupons.selectPage(new Page<Coupon>(q.getPage(),q.getSize()),Wrappers.<Coupon>lambdaQuery()
                .eq(Coupon::getStatus,"active").le(Coupon::getStartTime,now).gt(Coupon::getEndTime,now)
                .like(q.getKeyword()!=null && !q.getKeyword().isBlank(),Coupon::getName,q.getKeyword())
                .apply("quantity > (SELECT COUNT(*) FROM user_coupon uc WHERE uc.coupon_id=coupon.id)")
                .orderByDesc(Coupon::getCreateTime).orderByDesc(Coupon::getId));
        page.getRecords().forEach(this::decorate); return PageResult.from(page);
    }
    @Transactional public UserCouponVO claim(Long userId,Long id) {
        active(userId,true);
        Coupon c=coupons.lockById(id);
        // Repeated claims return the original entitlement even after the campaign ends.
        UserCoupon existing=owned.selectOne(Wrappers.<UserCoupon>lambdaQuery().eq(UserCoupon::getUserId,userId).eq(UserCoupon::getCouponId,id));
        if(existing!=null) return view(existing);
        var now=LocalDateTime.now();
        if(c==null || !"active".equals(c.getStatus()) || c.getStartTime().isAfter(now) || !c.getEndTime().isAfter(now))
            throw error("优惠券不在可领取时间内或已停用");
        if(owned.claimedCount(id)>=c.getQuantity()) throw error("优惠券已领完");
        UserCoupon u=new UserCoupon(); u.setUserId(userId); u.setCouponId(id); u.setName(c.getName());
        u.setAmount(c.getAmount()); u.setMinimumAmount(c.getMinimumAmount()); u.setStartTime(c.getStartTime());
        u.setExpireTime(c.getEndTime()); u.setStatus("unused"); u.setIsDeleted(0); owned.insert(u); return view(u);
    }
    public PageResult<UserCouponVO> mine(Long userId,UserCouponQuery q) {
        active(userId,false); var now=LocalDateTime.now();
        var w=Wrappers.<UserCoupon>lambdaQuery().eq(UserCoupon::getUserId,userId);
        if("expired".equals(q.getStatus())) w.and(x->x.eq(UserCoupon::getStatus,"expired").or(y->y.eq(UserCoupon::getStatus,"unused").le(UserCoupon::getExpireTime,now)));
        else if(q.getStatus()!=null) { w.eq(UserCoupon::getStatus,q.getStatus()); if("unused".equals(q.getStatus())) w.gt(UserCoupon::getExpireTime,now); }
        if(q.getOrderAmount()!=null) w.eq(UserCoupon::getStatus,"unused").le(UserCoupon::getStartTime,now).gt(UserCoupon::getExpireTime,now)
                .le(UserCoupon::getMinimumAmount,q.getOrderAmount()).lt(UserCoupon::getAmount,q.getOrderAmount());
        var p=owned.selectPage(new Page<UserCoupon>(q.getPage(),q.getSize()),w.orderByDesc(UserCoupon::getCreateTime).orderByDesc(UserCoupon::getId));
        return new PageResult<>(p.getRecords().stream().map(this::view).toList(),p.getTotal(),p.getCurrent(),p.getSize(),p.getPages());
    }
    /** Eligibility check without reserving the coupon. */
    public BigDecimal preview(Long userId, Long id, BigDecimal total) {
        if(id==null) return BigDecimal.ZERO;
        UserCoupon u=owned.selectById(id);
        return discount(userId, u, total);
    }
    private BigDecimal discount(Long userId, UserCoupon u, BigDecimal total) {
        var now=LocalDateTime.now();
        if(u==null || !userId.equals(u.getUserId()) || !"unused".equals(u.getStatus()) || u.getAmount()==null
                || u.getMinimumAmount()==null || u.getStartTime()==null || u.getExpireTime()==null
                || u.getStartTime().isAfter(now) || !u.getExpireTime().isAfter(now)) throw error("优惠券不存在、已占用或已过期");
        if(total.compareTo(u.getMinimumAmount())<0) throw error("订单未达到优惠券使用门槛");
        if(total.compareTo(u.getAmount())<=0) throw error("订单金额必须大于优惠券面额");
        return u.getAmount();
    }
    /** Caller holds the order transaction; row lock prevents spending the same coupon twice. */
    @Transactional public BigDecimal reserve(Long userId,Long id,Long orderId,BigDecimal total) {
        if(id==null) return BigDecimal.ZERO;
        UserCoupon u=owned.lockById(id);
        discount(userId, u, total);
        u.setStatus("locked"); u.setUsedOrderId(orderId); owned.updateById(u); return u.getAmount();
    }
    @Transactional public void releaseCheckout(Long userId, Long couponId, Long orderId) {
        if (couponId==null) return;
        UserCoupon u=owned.lockById(couponId);
        if (u==null || !userId.equals(u.getUserId()) || !"locked".equals(u.getStatus())
                || !java.util.Objects.equals(orderId,u.getUsedOrderId())) throw error("优惠券预占归属不一致");
        owned.update(null,Wrappers.<UserCoupon>lambdaUpdate().eq(UserCoupon::getId,u.getId())
                .set(UserCoupon::getStatus,u.getExpireTime().isAfter(LocalDateTime.now())?"unused":"expired")
                .set(UserCoupon::getUsedOrderId,null).set(UserCoupon::getUpdateTime,LocalDateTime.now()));
    }
    @Transactional public void release(Order order) {
        if(order.getUserCouponId()==null) return;
        UserCoupon u=bound(order);
        u.setStatus(u.getExpireTime().isAfter(LocalDateTime.now())?"unused":"expired");
        // Explicit SQL is required to clear a nullable field with MyBatis's NOT_NULL strategy.
        owned.update(null,Wrappers.<UserCoupon>lambdaUpdate().eq(UserCoupon::getId,u.getId())
                .set(UserCoupon::getStatus,u.getStatus()).set(UserCoupon::getUsedOrderId,null).set(UserCoupon::getUpdateTime,LocalDateTime.now()));
    }
    @Transactional public void markUsed(Order order) {
        if(order.getUserCouponId()==null) return;
        UserCoupon u=bound(order); u.setStatus("used"); owned.updateById(u);
    }
    private UserCoupon bound(Order order) {
        UserCoupon u=owned.lockById(order.getUserCouponId());
        if(u==null || !order.getUserId().equals(u.getUserId()) || !order.getId().equals(u.getUsedOrderId()) || !"locked".equals(u.getStatus()))
            throw error("订单优惠券状态不一致");
        return u;
    }
    private Coupon decorate(Coupon c) {
        long n=owned.claimedCount(c.getId()); c.setClaimedQuantity(n); c.setRemainingQuantity(Math.max(0,c.getQuantity()-n));
        if(!c.getEndTime().isAfter(LocalDateTime.now())) c.setStatus("expired"); return c;
    }
    private UserCouponVO view(UserCoupon u) {
        String status=u.getStatus();
        if("unused".equals(status) && u.getExpireTime()!=null && !u.getExpireTime().isAfter(LocalDateTime.now())) status="expired";
        return new UserCouponVO(u.getId(),u.getCouponId(),u.getName(),u.getAmount(),u.getMinimumAmount(),u.getStartTime(),u.getExpireTime(),status,u.getUsedOrderId(),u.getCreateTime());
    }
    private void active(Long id,boolean lock) {
        var u=lock?users.lockActiveUser(id):users.selectById(id);
        if(u==null || !"active".equals(u.getStatus())) throw error("用户不存在或已停用");
    }
    private BusinessException error(String message) { return new BusinessException(message); }
}
