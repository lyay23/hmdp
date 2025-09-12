package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;


    @Resource
    private RedisIdWorker idWorker;

    // 加载Lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT ;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);

    }


    /**
     * 秒杀 -使用Lua脚本改造
     * @param voucherId 优惠券id
     * @return 订单信息
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取用户id
        Long userId = UserHolder.getUser().getId();
        // 1. 执行Lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        // 2. 判断结果是否为0
        int r=result.intValue();
        // 2.1 不为0，代表没有购买资格
        if(r!=0){
            return Result.fail(r==1?"库存不足":"不能重复下单");
        }

        // 2.2 为0，代表有购买资格，把下单信息保存到阻塞队列中
        long orderId = idWorker.nextId("order");

        // 2.3 返回订单id

        return Result.ok(0);



    }














    /**
     * 秒杀
     * @param voucherId 优惠券id
     * @return 订单信息
     */
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 1. 查询优惠券信息
//        SeckillVoucher voucherOrder = seckillVoucherService.getById(voucherId);
//        // 2. 判断优惠券是否有效
//        if(voucherOrder.getBeginTime().isAfter(LocalDateTime.now())){
//            return Result.fail("优惠券秒杀还未开始");
//        }
//        if(voucherOrder.getEndTime().isBefore(LocalDateTime.now())){
//            return Result.fail("优惠券秒杀已结束");
//        }
//        // 3. 判断优惠券是否被抢完
//        if(voucherOrder.getStock() <1){
//            return Result.fail("优惠券已被抢完");
//        }
//
//        // 悲观锁-
//        // 加在方法上意思是所有用户都使用同一把锁，都需要等待上一把释放，而加在return那里判断的是用户ID，是并行的
//        Long userId = UserHolder.getUser().getId();
//
//
//        //-----------使用悲观锁解决一人一单的方案-------------------------------
//     //   synchronized (userId.toString().intern()) {
//        // -----------悲观锁结束-----------------
//
//
//
//        // -----------------使用分布式锁解决一人一单的方案-------------------------
//
//        // 创建锁
//        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId,stringRedisTemplate );
//
//        // -----------------使用Redisson来进行分布式锁--------------------------
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        // 获取锁
//        boolean b = lock.tryLock();
//        if(!b){
//            return Result.fail("开挂小子");
//        }
//        try {
//            // 获取代理对象（代理对象事务）
//            // createVouncherOrder 方法上有 @Transactional 注解，但是当在同一个类内部直接调用时，Spring AOP 的事务代理不会生效
//            // Spring AOP 是基于代理的，只有通过代理对象调用方法时，AOP 增强（如事务）才会生效
//            IVoucherOrderService context = (IVoucherOrderService) AopContext.currentProxy();
//            return context.createVouncherOrder(voucherId);
//        } finally {
//            lock.unlock();
//        }
//
//
//        // }
//    }

    /**
     * 使用悲观锁来完成一户一单
     * @param voucherId
     * @return
     */
    @Override
    @Transactional
    public  Result createVouncherOrder(Long voucherId) {
        // 5. 一人一单
        Long userId = UserHolder.getUser().getId();

        // intern() 保证字符串常量池中的对象唯一,



            // 5.1 查询订单
            int count = query()
                    .eq("user_id", userId)
                    .eq("voucher_id", voucherId).count();
            // 5.2 判断订单是否存在
            if (count > 0) {
                return Result.fail("您已购买过该优惠券");
            }

            // 4. 扣减优惠券数量
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    // CSA乐观锁
                    .gt("stock", 0)
                    .update();
            if (!success) {
                return Result.fail("优惠券秒杀失败");
            }

            // 5. 创建订单
            VoucherOrder order = new VoucherOrder();
            long orderId = idWorker.nextId("order");
            order.setId(orderId);

            order.setUserId(userId);
            // 代金券id
            order.setVoucherId(voucherId);
            save(order);


            // 6. 返回订单信息

            return Result.ok(orderId);
        }

}
