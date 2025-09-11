package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import javax.annotation.Resource;
import java.util.Collections;
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
    private static final String ID_PREFIX = UUID.randomUUID().toString(true)+"-";

    // 加载Lua脚本
    private static final DefaultRedisScript<Long> UNLUCK_SCRIPT ;
    static {
        UNLUCK_SCRIPT = new DefaultRedisScript<>();
        UNLUCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLUCK_SCRIPT.setResultType(Long.class);

    }


    // 这里需要传入
    public SimpleRedisLock( String name,StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(long timeoutSec) {

        // 获取线程标识
        String threadId =ID_PREFIX+  Thread.currentThread().getId();

        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX + name, threadId + "", timeoutSec, TimeUnit.SECONDS);

        // 解决拆箱问题
        return BooleanUtil.isTrue(success);
    }

    @Override
    public void unlock() {
//
//        // 获取线程标识
//        String threadId =ID_PREFIX+  Thread.currentThread().getId();
//        // 获取锁中标识
//        String lockId = stringRedisTemplate.opsForValue().get(LOCK_PREFIX + name);
//        // 判断标识是否一致
//        if (threadId.equals(lockId)){
//            // 删除锁
//            stringRedisTemplate.delete(LOCK_PREFIX + name);
//        }

        // 调用Lua脚本删除锁
        stringRedisTemplate.execute(UNLUCK_SCRIPT,
                Collections.singletonList(LOCK_PREFIX + name),
                ID_PREFIX+  Thread.currentThread().getId()
        );


    }
}
