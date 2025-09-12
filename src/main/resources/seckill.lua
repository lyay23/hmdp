---
--- Created by 李阳.
--- DateTime: 2025/9/12 9:29
--- 使用Lua脚本将优惠券秒杀相关数据写入到redis中
--- 判断库存是否充足，判断用户是否下单成功，扣减库存
--- 将用户id存入当前优惠券的set集合
---

-- 1. 参数列表：
-- 1.1 优惠券的id
local voucherId = ARGV[1]
-- 1.2. 用户id
local userId = ARGV[2]

-- 2. 数据KEY
-- 2.1. 优惠券的库存
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2. 订单Key
local orderKey = 'seckill:order:' .. voucherId

-- 3. 业务逻辑
-- 3.1. 判断库存是否充足 get stockKey
if(tonumber(redis.call('get',stockKey))<=0) then
    -- 3.1.1 库存不足
    return 1
end

-- 3.2 判断用户是否下单成功 SISMEMEBER orderKey userId
if(redis.call('SISMEMBER',orderKey,userId)==1) then
    -- 3.2.1 存在说明是重复下单，返回2
    return 2
end

-- 3.3 扣减库存 incrby stockKey -1
redis.call('incrby',stockKey,-1)

-- 3.4 将用户id存入当前优惠券的set集合 sadd orderKey userId
redis.call('sadd',orderKey,userId)

return 0
