package com.gas.ecommerce.config;

import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
/**
 * 应用配置类，负责向 Spring 注册和配置基础设施组件。
 */
public class MyBatisPlusConfig {
    /**
     * 注册 MyBatis-Plus 插件。
     * 乐观锁根据旧版本号避免并发覆盖；分页插件把分页请求转换成 MySQL 分页 SQL。
     */
    @Bean MybatisPlusInterceptor mybatisPlusInterceptor() {
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
    /** 按 BaseEntity 字段名自动填时间和软删除初始值。 */
    @Bean MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            // 新插入记录同时有创建和更新时间，软删除初始值为 0（未删除）。
            @Override public void insertFill(MetaObject metaObject) {
                strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
                strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
                strictInsertFill(metaObject, "isDeleted", Integer.class, 0);
            }
            // 更新时只改最后更新时间，不覆盖原创建时间。
            @Override public void updateFill(MetaObject metaObject) { setFieldValByName("updateTime", LocalDateTime.now(), metaObject); }
        };
    }
    /** 配置全局字段名以及逻辑删除时使用的数据库值。 */
    @Bean GlobalConfig globalConfig(MetaObjectHandler handler) {
        var config = new GlobalConfig();
        config.setMetaObjectHandler(handler);
        config.setDbConfig(new GlobalConfig.DbConfig().setLogicDeleteField("isDeleted").setLogicDeleteValue("1").setLogicNotDeleteValue("0"));
        return config;
    }
}
