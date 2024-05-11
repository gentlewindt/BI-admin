package com.gentlewind.project.config;



import lombok.Data;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "spring.redis") // 从application.yml文化中读取前缀为"spring.redis"的配置项
@Data
public class RedissonConfig {
    private Integer database;

    private String host;

    private Integer port;

    // 如果redis默认没有密码，则不用写
    //private String password;

    // spring启动时，会自动创建一个RedissonClient对象
    @Bean
    public RedissonClient getRedissionClient() {
        // 1. 创建配置对象
        Config config = new Config();

        // 1.2 添加单机Redisson配置
        config.useSingleServer()

        // 1.3 设置数据库
        .setDatabase(database)

        // 1.4 设置redis的地址
        .setAddress("redis://" + host + ":" + port);

        // 1.5 设置redis的密码（如果没有密码，则不用设置）
        //.setPassword(password);

        // 2. 创建Redisson实例
        RedissonClient redisson = Redisson.create(config);

        return redisson;



    }
}
