package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.DefaultScriptExecutor;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author lzt
 * @date 2023/2/24 16:32
 * @description:
 */

public class SimpleRedisLock implements ILock{
    private String name;
    private StringRedisTemplate stringRedisTemplate;
    private static final String  KEY_PREFIX = "lock:";
    private static final String  VALUE_PREFIX = UUID.randomUUID().toString(true) + "-";
    // 脚本初始化
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        String value = VALUE_PREFIX +  Thread.currentThread().getName();
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, value, timeoutSec, TimeUnit.SECONDS));
    }

    @Override
    public void unlock() {
//        String value = VALUE_PREFIX + Thread.currentThread().getName();
//        String lockValue = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        // 确保释放的锁是自己添加的锁，否则不释放
//        if (value.equals(lockValue)) {
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }

        // 调用lua脚本进行释放锁的操作
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                VALUE_PREFIX + Thread.currentThread().getName());

    }
}
