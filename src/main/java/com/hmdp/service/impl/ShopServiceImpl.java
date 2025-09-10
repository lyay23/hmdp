package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisDate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {

        // 缓存穿透
        //Shop shop = queryWithPassThrough(id);
        Shop shop=cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY ,id,Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);


        // 使用互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        // 使用逻辑过期的方式解决缓存击穿
        // Shop shop = cacheClient.queryWithLogicExpire(RedisConstants.CACHE_SHOP_KEY ,id,Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.SECONDS);

        if(shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);

    }

//    // 重建缓存-创建线程池
//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
//
//    /**
//     * 使用逻辑过期的方式解决缓存击穿
//     * @param id 店铺id
//     * @return 店铺详情数据
//     */
//    public Shop queryWithLogicExpire(Long id) {
//        //1. 从Redis中查询缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
//        //2. 判断是否存在
//        if (StrUtil.isBlank(shopJson)) {
//            //2.1. 不存在直接返回
//            return null;
//        }
//
//        // 2.1 命中则需要将json发序列化为商品对象
//        RedisDate redisDate = JSONUtil.toBean(shopJson, RedisDate.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisDate.getData(), Shop.class);
//        LocalDateTime expireTime = redisDate.getExpireTime();
//        // 2.2 存在则判断缓存是否过期
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            // 2.3.1 未过期直接返回
//            return shop;
//        }
//
//        // 2.4 已过期需要缓存重建
//        // 2.5 获取互斥锁
//        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//        boolean isLock = lock(lockKey);
//        // 2.6 判断是否获取成功
//        if (isLock) {
//            // 2.7 成功则开启独立线程，实现缓存重建
//            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                // 重建缓存
//                try {
//                    this.saveShopToRedis(id,20L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }finally {
//                    // 释放锁
//                    unlock(lockKey);
//                }
//
//            });
//
//        }
//
//
//        // 2.8 返回过期的商品信息
//
//
//        return shop;
//    }



    public Shop queryWithMutex(Long id) {
        //1. 从Redis中查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //2. 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3. 存在直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 判断命中是否为空值-不等于null就是等于空字符串""
        if (shopJson!= null) {
            return null;
        }

        //---------------------互斥锁解决缓存击穿---------------------------
        // 4. 实现缓存重建
        // 4.1 获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = lock(lockKey);
            // 4.2 判断是否获取成功
            if (!isLock) {
                // 4.3 如果失败则休眠并且重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }


            // 4.5 成功，根据id查询数据库
            // 4.5.1 再次查询缓存是否存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            //3. 存在直接返回
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
            shop = getById(id);
            // 5. 不存在返回错误
            if (shop == null) {
                // 将空值写入Redis缓存
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                // 返回错误信息
                return null;
            }
            // 6. 存在，将查询结果放入Redis缓存
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            // 7. 释放锁
            unlock(lockKey);
        }

        // 8. 返回结果
        return shop;
    }



    /**
     * 查询店铺数据，包含密码--缓存穿透的代码
     * @param id 店铺id
     * @return 店铺详情数据
     */
   /* ----------------使用工具类来完成缓存穿透的代码-------------------
    public Shop queryWithPassThrough(Long id) {
        //1. 从Redis中查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //2. 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3. 存在直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 判断命中是否为空值-不等于null就是等于空字符串""
        if (shopJson!= null) {
            return null;
        }

        //4. 不存在从数据库查询
        Shop shop = baseMapper.selectById(id);
        // 4.1 不存在返回错误
        if (shop == null) {
            // 将空值写入Redis缓存
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 返回错误信息
            return null;
        }
        //5. 将查询结果放入Redis缓存
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //6. 返回结果

        return shop;
     }

------------------使用工具类来完成缓存穿透的代码-------------------*/
    /**
     * 更新商铺数据
     * @param shop 商铺数据
     * @return 无
     */
    @Override
    @Transactional
    public Result update(Shop shop) {

        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }

        //1. 更新数据库
        updateById(shop);
        // 2. 删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
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

    /**
     * 将店铺信息保存到Redis
     * @param id 店铺id
     */
    public void saveShopToRedis(Long id,Long expireTime){
        //1. 从数据库查询店铺信息
        Shop shop = getById(id);
        // 2. 封装过期时间
        RedisDate redisDate = new RedisDate();
        redisDate.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
        redisDate.setData(shop);
        // 3. 将店铺信息保存到Redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisDate));
    }

}
