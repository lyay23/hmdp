package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Test
    void contextLoads() {
        shopService.saveShopToRedis(1L,10L);
    }

    /**
     * 注入地图
     */
    @Test
    void loadShopDate(){
        // 1. 查询店铺信息
        List<Shop> list = shopService.list();
        // 2. 将店铺信息按照typeID进行分组，放到一个集合
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 3. 分批存储写入Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            // 3.1 获取店铺信息key
            Long typeId = entry.getKey();
            String key = "shop:geo:" + typeId;
            // 3.2 获取同类型店铺的集合
            List<Shop> shops = entry.getValue();
            // 3.3 将店铺信息写入Redis GEOADD key 经度 纬度 member
            List<RedisGeoCommands.GeoLocation<String>> geoLocations = new ArrayList<>();
            for (Shop shop : shops) {
              //  stringRedisTemplate.opsForGeo().add(key,new Point(shop.getX(),shop.getY()),shop.getId().toString());
                geoLocations.add(new RedisGeoCommands.GeoLocation(shop.getId().toString(),new Point(shop.getX(),shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key,geoLocations);
        }
    }
}
