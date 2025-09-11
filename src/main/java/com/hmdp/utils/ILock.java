package com.hmdp.utils;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: 李阳
 * @Date: 2025/09/10/19:33
 * @Description: 分布式锁的接口
 */
public interface ILock {

    /**
     * 尝试获取锁
     * @param timeoutSec 锁的持有时间，过期自动释放
     * @return true代表获取锁成功，false代表获取锁失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
