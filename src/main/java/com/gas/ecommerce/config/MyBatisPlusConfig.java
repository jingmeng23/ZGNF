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
public class MyBatisPlusConfig {
    @Bean MybatisPlusInterceptor mybatisPlusInterceptor() {
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
    @Bean MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override public void insertFill(MetaObject metaObject) {
                strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
                strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
                strictInsertFill(metaObject, "isDeleted", Integer.class, 0);
            }
            @Override public void updateFill(MetaObject metaObject) { setFieldValByName("updateTime", LocalDateTime.now(), metaObject); }
        };
    }
    @Bean GlobalConfig globalConfig(MetaObjectHandler handler) {
        var config = new GlobalConfig();
        config.setMetaObjectHandler(handler);
        config.setDbConfig(new GlobalConfig.DbConfig().setLogicDeleteField("isDeleted").setLogicDeleteValue("1").setLogicNotDeleteValue("0"));
        return config;
    }
}
