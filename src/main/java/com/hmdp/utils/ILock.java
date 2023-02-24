package com.hmdp.utils;

/**
 * @author lzt
 * @date 2023/2/24 16:33
 * @description:
 */
public interface ILock {
    /**
     * 尝试获取锁
     * @param timeoutSec
     * @return
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
