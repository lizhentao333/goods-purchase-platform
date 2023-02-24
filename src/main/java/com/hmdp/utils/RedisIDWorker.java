package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author lzt
 * @date 2023/2/23 18:06
 * @description:
 */
@Component
public class RedisIDWorker {
    /**
     * 开始时间戳
     */
    private static final long BEGIN_TIMESTAMP = 1677110400L;
    /**
     * 序列号位数
     */
    private static final int COUNT_BITS = 32;

    private StringRedisTemplate stringRedisTemplate;

    public RedisIDWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextID(String keyPrefix) {
        // 1.时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        // 2.序列号
        // 获取当前日期
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // 自增长
        long increment = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);


        return timestamp << COUNT_BITS | increment;
    }


}
