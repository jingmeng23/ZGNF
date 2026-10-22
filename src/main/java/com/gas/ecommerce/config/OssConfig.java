package com.gas.ecommerce.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
/**
 * 应用配置类，负责向 Spring 注册和配置基础设施组件。
 */
public class OssConfig {
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "aliyun.oss", name = "enabled", havingValue = "true")
    OSS ossClient(@Value("${aliyun.oss.endpoint}") String endpoint,
            @Value("${aliyun.oss.access-key-id}") String accessKeyId,
            @Value("${aliyun.oss.access-key-secret}") String accessKeySecret) {
        return new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    }
}
