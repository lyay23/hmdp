package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public List<ShopType> queryTypeList() {
        //1. 从Redis中查询缓存
        String shopTypeJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_TYPE_KEY);
        //2. 判断是否存在
        if (StrUtil.isNotBlank(shopTypeJson)) {
            //3. 存在直接返回
            return JSONUtil.toList(JSONUtil.parseArray(shopTypeJson), ShopType.class);
        }

        //4. 不存在从数据库查询
        List<ShopType> sort = this.query().orderByAsc("sort").list();
        // 4.1 不存在返回错误
         if (sort == null){
             return new ArrayList<>();
         }
        //5. 将查询结果放入Redis缓存
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(sort));
        //6. 返回结果

        return sort;
    }
}
