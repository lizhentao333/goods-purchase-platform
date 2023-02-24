package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIDWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIDWorker redisIDWorker;

    @org.junit.jupiter.api.Test
    void testSaveShop() {
        shopService.saveShop2Redis(1L, 10L);
    }

    private final ExecutorService es = Executors.newFixedThreadPool(500);
    @org.junit.jupiter.api.Test
    void testNextID() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(500);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIDWorker.nextID("test");
                System.out.println("id = " + id);
            }
            countDownLatch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 500; i++) {
            es.submit(task);
        }
        countDownLatch.await();
        long end = System.currentTimeMillis();

        System.out.println("耗时：" + (end - begin) + " ms" );
    }
}
