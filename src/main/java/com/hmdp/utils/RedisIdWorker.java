package com.hmdp.utils;

import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;


import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: 李阳
 * @Date: 2025/09/10/13:45
 * @Description: 使用Redis实现全局唯一id
 */
@Component
public class RedisIdWorker {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 开始的时间戳
    private static final long BEGIN_TIMESTAMP = 1735689600L;
    // Count的位数
    private static final int COUNT_BITS = 32;
    /**
     * 生成唯一id
     * @param keyPrefix 业务前缀
     * @return 唯一id
     */
    public long nextId(String keyPrefix){

        // 1. 生成时间戳
        // 当前时间
        LocalDateTime now = LocalDateTime.now();
        // 当前的秒
        long epochSecond = now.toEpochSecond(ZoneOffset.UTC);
        // 生成的时间戳
        long timestamp = epochSecond - BEGIN_TIMESTAMP;

        // 2. 生成序列号
        // 获取当前的日期精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long increment = stringRedisTemplate.opsForValue().increment("icr" + keyPrefix + ":" + date);


        // 3. 拼接并且返回

        return timestamp << COUNT_BITS | increment;
    }


}
