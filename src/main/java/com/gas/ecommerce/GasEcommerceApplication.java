package com.gas.ecommerce;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan(basePackages = "com.gas.ecommerce.modules", annotationClass = org.apache.ibatis.annotations.Mapper.class)
@EnableScheduling
public class GasEcommerceApplication {
    public static void main(String[] args) {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
        SpringApplication.run(GasEcommerceApplication.class, args);
    }
}
