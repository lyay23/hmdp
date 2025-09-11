package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: 李阳
 * @Date: 2025/09/10/19:35
 * @Description: 分布式锁的简单实现
 */
public class SimpleRedisLock  implements ILock{

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 业务/锁的名称
    private String name;
    // 锁的前缀
    private static final String LOCK_PREFIX = "lock:";

    // 这里需要传入
    public SimpleRedisLock( String name,StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(long timeoutSec) {

        // 获取线程标识
        long threadId = Thread.currentThread().getId();

        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX + name, threadId + "", timeoutSec, TimeUnit.SECONDS);

        // 解决拆箱问题
        return BooleanUtil.isTrue(success);
    }

    @Override
    public void unlock() {

        stringRedisTemplate.delete(LOCK_PREFIX + name);
    }
}
