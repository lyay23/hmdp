package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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
    private RedisIdWorker idWorker;

    /**
     * 秒杀
     * @param voucherId 优惠券id
     * @return 订单信息
     */
    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        // 1. 查询优惠券信息
        SeckillVoucher voucherOrder = seckillVoucherService.getById(voucherId);
        // 2. 判断优惠券是否有效
        if(voucherOrder.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("优惠券秒杀还未开始");
        }
        if(voucherOrder.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("优惠券秒杀已结束");
        }
        // 3. 判断优惠券是否被抢完
        if(voucherOrder.getStock() <1){
            return Result.fail("优惠券已被抢完");
        }
        // 4. 扣减优惠券数量
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", voucherId).update();
        if(!success){
            return Result.fail("优惠券秒杀失败");
        }
        // 5. 创建订单
        VoucherOrder order = new VoucherOrder();
        long orderId = idWorker.nextId("order");
        order.setId(orderId);
        Long userId = UserHolder.getUser().getId();
        order.setUserId(userId);
        // 代金券id
        order.setVoucherId(voucherId);
        save(order);


        // 6. 返回订单信息

        return Result.ok(orderId);
    }
}
