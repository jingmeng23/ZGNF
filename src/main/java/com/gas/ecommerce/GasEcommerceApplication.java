package com.gas.ecommerce;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot 项目的启动入口。启动后会扫描本包下的配置和组件，
 * 同时开启 MyBatis Mapper 扫描、定时任务，并将 JVM 默认时区设为北京时间。
 */
@SpringBootApplication
@MapperScan(basePackages = "com.gas.ecommerce.modules", annotationClass = org.apache.ibatis.annotations.Mapper.class)
@EnableScheduling
public class GasEcommerceApplication {
    /** Java 程序入口；SpringApplication 会创建 Spring 容器并启动 Web 服务。 */
    public static void main(String[] args) {
        // LocalDateTime 本身不携带时区；这里设置默认时区，确保相关框架按北京时间处理。
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
        SpringApplication.run(GasEcommerceApplication.class, args);
    }
}
