package com.gas.ecommerce.modules.user.vo;
/**
 * 视图对象（VO），整理后返回给客户端展示的数据。
 */
public record UserVO(Long id, String nickname, String avatar, String phoneLast4) { }
