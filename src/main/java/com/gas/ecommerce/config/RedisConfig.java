package com.gas.ecommerce.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

@Configuration
/**
 * 应用配置类，负责向 Spring 注册和配置基础设施组件。
 */
public class RedisConfig {
    @Bean Jackson2ObjectMapperBuilderCustomizer longAsStringCustomizer() {
        return builder -> {
            var module = new SimpleModule();
            module.addSerializer(Long.class, new JsonSerializer<>() {
                @Override public void serialize(Long value, JsonGenerator generator, SerializerProvider serializers) throws IOException { generator.writeString(value.toString()); }
            });
            var formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            module.addSerializer(java.time.LocalDateTime.class, new com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer(formatter));
            module.addDeserializer(java.time.LocalDateTime.class, new com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer(formatter));
            builder.modulesToInstall(module);
        };
    }
    @Bean RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory, ObjectMapper mapper) {
        var template = new RedisTemplate<String, Object>();
        template.setConnectionFactory(factory);
        var strings = new StringRedisSerializer();
        var values = new Jackson2JsonRedisSerializer<>(mapper, Object.class);
        template.setKeySerializer(strings); template.setHashKeySerializer(strings);
        template.setValueSerializer(values); template.setHashValueSerializer(values);
        template.afterPropertiesSet();
        return template;
    }
}
