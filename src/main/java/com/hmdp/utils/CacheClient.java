package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: 李阳
 * @Date: 2025/09/10/10:58
 * @Description: 解决缓存穿透缓存击穿的工具类
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 方法一： 将任意Java对象序列化为JSON字符串，
     *        并存储到String类型的Key中
     *        并且可以设置TTL
     * @param key 传入的键值Key
     * @param value 传入的value
     * @param time 过期时间
     * @param unit  时间单位
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 方法二： 将任意Java对象序列化为JSON字符串，
     *        并存储到String类型的Key中
     *        并且可以设置逻辑过期时间
     *        用于处理缓存击穿的问题
     * @param key 传入的Key前缀
     * @param value 传入的value 逻辑过期时间
     * @param time 过期时间
     * @param unit  时间单位
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期时间
        RedisDate redisDate = new RedisDate();
        redisDate.setData(value);
        redisDate.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisDate));
    }


    /**
     * 方法三：根据指定的Key查询缓存，并反序列化为指定类型
     *        利用缓存空值解决缓存穿透的问题
     * @param keyPrefix 传入的Key前缀
     * @param id 传入的ID
     * @param type 传入的类型（商铺...）
     * @param dbFallback 查询数据库的方法
     * @param time 过期时间
     * @param unit 时间单位
     * @return 返回结果
     * @param <T> 类型
     * @param <ID> ID类型
     */
    public <T,ID> T queryWithPassThrough(String keyPrefix, ID id, Class<T> type, Function<ID, T> dbFallback,Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        //1. 从Redis中查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2. 判断是否存在
        if (StrUtil.isNotBlank(json)) {
            //3. 存在直接返回
            return JSONUtil.toBean(json,type);
        }
        // 判断命中是否为空值-不等于null就是等于空字符串""
        if (json!= null) {
            return null;
        }

        //4. 不存在从数据库查询
        T  t = dbFallback.apply(id);;
        // 4.1 不存在返回错误
        if (t == null) {
            // 将空值写入Redis缓存
            stringRedisTemplate.opsForValue().set(key, "",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 返回错误信息
            return null;
        }
        //5. 将查询结果放入Redis缓存
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(t),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //6. 返回结果
        this.set(key,t,time,unit);

        return t;
    }


    // 缓存重建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 方法四：根据指定的Key查询缓存，并反序列化为指定类型
     *        利用逻辑过期时间解决缓存击穿的问题
     * @param keyPrefix 传入的Key前缀
     * @param id 传入的ID
     * @param type 传入的类型（商铺...）
     * @param dbFallback 查询数据库的方法
     * @param time 过期时间
     * @param unit 时间单位
     * @return 返回结果
     * @param <T> 类型
     * @param <ID> ID类型
     */
    public <T,ID> T queryWithLogicExpire(String keyPrefix, ID id, Class<T> type, Function<ID, T> dbFallback,Long time, TimeUnit unit) {

        String key = keyPrefix + id;
        //1. 从Redis中查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2. 判断是否存在
        if (StrUtil.isBlank(json)) {
            //2.1. 不存在直接返回
            return null;
        }

        // 2.1 命中则需要将json发序列化为商品对象
        RedisDate redisDate = JSONUtil.toBean(json, RedisDate.class);
        T t = JSONUtil.toBean((JSONObject) redisDate.getData(), type);
        LocalDateTime expireTime = redisDate.getExpireTime();
        // 2.2 存在则判断缓存是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 2.3.1 未过期直接返回
            return t;
        }

        // 2.4 已过期需要缓存重建
        // 2.5 获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = lock(lockKey);
        // 2.6 判断是否获取成功
        if (isLock) {
            // 2.7 成功则开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                // 重建缓存
                try {
                   // 查询数据库
                    T t1 = dbFallback.apply(id);
                    this.setWithLogicalExpire(key,t1,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    // 释放锁
                    unlock(lockKey);
                }

            });

        }


        // 2.8 返回过期的商品信息


        return t;
    }

    /**
     * 创建锁
     * @param key 锁的key
     */
    private boolean lock(String key) {
        // value为1是随便起的，过期时间为10秒
        Boolean flag=  stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);

        return BooleanUtil.isTrue(flag);
    }

    /**
     * 删除锁
     * @param key 锁的key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }



}
