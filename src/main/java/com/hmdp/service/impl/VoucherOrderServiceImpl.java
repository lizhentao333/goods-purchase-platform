package com.hmdp.service.impl;

import cn.hutool.core.lang.UUID;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIDWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
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
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIDWorker redisIDWorker;
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final String  VALUE_PREFIX = UUID.randomUUID().toString(true) + "-";
    // 脚本初始化
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    // 获取队列中的订单信息
                    VoucherOrder order = orderTasks.take();
                    // 创建订单
                    handleVoucherOrder(order);
                }catch (InterruptedException e){
//                    e.printStackTrace();
                    log.error("处理订单异常", e);
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 获取用户
        Long userId = voucherOrder.getUserId();

        // 创建锁对象
        RLock lock = redissonClient.getLock("order:" + userId);
        // 获取锁
        boolean isLock = lock.tryLock();

        if (!isLock){
            // 获取锁失败，
            log.error("不允许重复下单");
            return;
        }

        try{
            // 获取代理对象
            proxy.createVoucherOrder(voucherOrder);

        }finally {
            // 释放锁
            lock.unlock();
        }
    }

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }


    private IVoucherOrderService proxy;

    // 实现异步秒杀
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 1.执行lua脚本，根据运行结果进行后续操作
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        int ret = result.intValue();
        // 2.返回结果：
        if (ret != 0) {
            // 1：库存不足
            // 2：不允许重复下单
            return  Result.fail(ret == 1 ? "库存不足" : "不允许重复下单");
        }
        // 0：有资格购买，把下单信息保存到阻塞队列中
        // 3.1创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 3.1 订单id
        long orderId = redisIDWorker.nextID("order");
        voucherOrder.setId(orderId);
        // 3.2 用户id

        voucherOrder.setUserId(userId);
        // 3.3 代金券id
        voucherOrder.setVoucherId(voucherId);
        // TODO: 保存阻塞队列
        // 3.4 放进阻塞队列中
        orderTasks.add(voucherOrder);
        // 获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        return Result.ok(orderId);
    }
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 1.查询优惠券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        // 2.判断秒杀是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())){
//            return Result.fail("秒杀尚未开始");
//        }
//        // 3.判断秒杀是否结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())){
//            return Result.fail("秒杀已经结束");
//        }
//        // 4.判断库存是否充足
//        if (voucher.getStock() < 1) {
//            return Result.fail("库存不足");
//        }
//
//        // 8.返回订单id
//        Long userId = UserHolder.getUser().getId();
//        // userId.toString().intern():确保同一用户持有同一把锁
//        // 确保事务提交完成后再释放锁
//        // 使用redis实现的分布式锁
//        // 创建锁对象
////        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        // 获取锁
//        RLock lock = redissonClient.getLock("order:" + userId);
//
//        boolean isLock = lock.tryLock();
//        if (!isLock){
//            // 获取锁失败，
//            return Result.fail("不允许重复下单");
//        }
//
//        try{
//            // 获取代理对象
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }finally {
//            // 释放锁
//            lock.unlock();
//        }
//    }
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder){
        // 5.一人一单
        Long userId = voucherOrder.getUserId();

            // 5.1 查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
            // 5.2 判断是否存在
            if (count>0) {
                // 用户已经下单
                log.error("用户已经下过单");
                return;
            }

            // 6.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock=stock-1")
                    .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)
                    .update();
            if( !success ) {
                log.error("库存不足");
                return;
            }

            save(voucherOrder);
    }
}
