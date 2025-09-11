package com.hmdp.config;


import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: 李阳
 * @Date: 2025/09/11/14:41
 * @Description: Redisson的客户端
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){

        Config config = new Config();

        config.useSingleServer().setAddress("redis://192.168.163.129:6379").setPassword("abc123");


        return Redisson.create(config);
    }
}
