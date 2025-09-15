package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
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

    // 阻塞队列
//    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);
    private final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    String quequeName = "stream.orders";
    // 在类加载时就执行秒杀相关的逻辑
    @PostConstruct
    public void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

        private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            while (true){

                try {
                    // 1. 获取消息队列中的信息、
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(quequeName, ReadOffset.lastConsumed())
                    );
                    // 2. 判断消息是否成功
                    if (list == null || list.isEmpty()){
                        // 如果获取失败，说明没有消息，继续下一个循环
                        continue;
                    }
                    MapRecord<String,Object, Object> value = list.get(0);
                    Map<Object, Object> values = value.getValue();
                    VoucherOrder order = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    // 2.2 如果成功就可以下单
                    handleVoucherOrder(order);
                    // 3. ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(quequeName,"g1",value.getId());
                } catch (Exception e) {
                    log.error("获取队列中的信息异常",e);
                    handlePendingList();

                }

            }
        }
        private void handlePendingList(){
            while (true){

                try {
                    // 1. 获取pending-list消息队列中的信息、
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(quequeName, ReadOffset.from("0"))
                    );
                    // 2. 判断消息是否成功
                    if (list == null || list.isEmpty()){
                        // 如果获取失败，说明pendlist没有消息，继续下一个循环
                        break;
                    }
                    MapRecord<String,Object, Object> value = list.get(0);
                    Map<Object, Object> values = value.getValue();
                    VoucherOrder order = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    // 2.2 如果成功就可以下单
                    handleVoucherOrder(order);
                    // 3. ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(quequeName,"g1",value.getId());
                } catch (Exception e) {
                    log.error("获取队列中的信息异常",e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }

                }

            }
        }
    }



//    // 创建阻塞队列-当一个线程尝试从阻塞队列中获取数据时，如果队列为空，线程会等待，直到有数据放入队列
//    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);
//
//    // 创建线程池-单线程
//    private final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
//
//
//    // 创建线程任务-当用户秒杀购物之前就开始执行
//    private class VoucherOrderHandler implements Runnable{
//
//        @Override
//        public void run() {
//            while (true){
//
//                try {
//                    // 1. 获取队列中的信息、
//                    VoucherOrder voucherOrder = orderTasks.take();
//                    // 2. 创建订单信息
//                    handleVoucherOrder(voucherOrder);
//                } catch (Exception e) {
//                    log.error("获取队列中的信息异常",e);
//                }
//
//            }
//        }
//    }

    // 创建订单信息
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
                // -----------------使用Redisson来进行分布式锁--------------------------
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 获取锁
        boolean b = lock.tryLock();
        if(!b){
            log.error("不允许重复下单");
            return ;
        }
        try {
            // 获取代理对象（代理对象事务）
            // createVouncherOrder 方法上有 @Transactional 注解，但是当在同一个类内部直接调用时，Spring AOP 的事务代理不会生效
            // Spring AOP 是基于代理的，只有通过代理对象调用方法时，AOP 增强（如事务）才会生效

             context.createVouncherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    // 代理对象
    private IVoucherOrderService context;


    /**
     * 秒杀 -使用Redis提供的Stream的消息队列来改造
     * @param voucherId 优惠券id
     * @return 订单信息
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取用户id
        Long userId = UserHolder.getUser().getId();
        // 2.3 保存订单信息
        long orderId = idWorker.nextId("order");
        // 1. 执行Lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)
        );
        // 2. 判断结果是否为0
        int r=result.intValue();
        // 2.1 不为0，代表没有购买资格
        if(r!=0){
            return Result.fail(r==1?"库存不足":"不能重复下单");
        }

        // 2.2 为0，代表有购买资格，把下单信息保存到阻塞队列中
        VoucherOrder order = new VoucherOrder();

        order.setId(orderId);
        // 2.4 保存用户id
        order.setUserId(userId);
        // 2.5 保存优惠券id
        order.setVoucherId(voucherId);

        // 2.6 创建阻塞队列
        //orderTasks.add(order);

        // 获取代理对象
        context = (IVoucherOrderService) AopContext.currentProxy();

        // 2.3 返回订单id

        return Result.ok(orderId);



    }









//
//    /**
//     * 秒杀 -使用Lua脚本改造
//     * @param voucherId 优惠券id
//     * @return 订单信息
//     */
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 获取用户id
//        Long userId = UserHolder.getUser().getId();
//        // 1. 执行Lua脚本
//        Long result = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(),
//                userId.toString()
//        );
//        // 2. 判断结果是否为0
//        int r=result.intValue();
//        // 2.1 不为0，代表没有购买资格
//        if(r!=0){
//            return Result.fail(r==1?"库存不足":"不能重复下单");
//        }
//
//        // 2.2 为0，代表有购买资格，把下单信息保存到阻塞队列中
//        VoucherOrder order = new VoucherOrder();
//        // 2.3 保存订单信息
//        long orderId = idWorker.nextId("order");
//        order.setId(orderId);
//        // 2.4 保存用户id
//        order.setUserId(userId);
//        // 2.5 保存优惠券id
//        order.setVoucherId(voucherId);
//
//        // 2.6 创建阻塞队列
//        orderTasks.add(order);
//
//        // 获取代理对象
//         context = (IVoucherOrderService) AopContext.currentProxy();
//
//        // 2.3 返回订单id
//
//        return Result.ok(orderId);
//
//
//
//    }














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
     *
     * @param voucherOrder
     */
    @Override
    @Transactional
    public void createVouncherOrder(VoucherOrder voucherOrder) {
        // 5. 一人一单
        Long userId = voucherOrder.getUserId();

        // intern() 保证字符串常量池中的对象唯一,


            // 5.1 查询订单
            int count = Math.toIntExact(query()
                    .eq("user_id", userId)
                    .eq("voucher_id", voucherOrder.getVoucherId()).count());
            // 5.2 判断订单是否存在
            if (count > 0) {
                log.error("用户已经购买过该优惠券");
                return ;
            }

            // 4. 扣减优惠券数量
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherOrder.getVoucherId())
                    // CSA乐观锁
                    .gt("stock", 0)
                    .update();
            if (!success) {
                log.error("库存不足");
                return ;
            }

            // 5. 创建订单

            save(voucherOrder);


        }

}
