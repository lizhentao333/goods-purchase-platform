package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.ssh.JschUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    // 缓存击穿
    public Result queryById(Long id) {
        // 互斥锁
//        Shop shop = queryWithMutex(id);
        // 逻辑过期
        Shop shop = queryWithLogicalExpire(id);

        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    // 互斥锁解决缓存击穿问题
    public Shop queryWithMutex(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        // 1. 从redis中查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        // 2.判断是否存在
        if(StrUtil.isNotBlank(shopJson)) {
            // 3.存在，直接返回

            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 如果命中结果是空值
        if( shopJson != null) {
            // 返回错误信息
            return null;
        }
        // 4.实现缓存重建
        // a) 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            // b) 判断是否获取成功
            if (!isLock){
                // c) 失败则休眠
                Thread.sleep(50);
                queryWithMutex(id);
            }
            // d) 成功则根据id查询数据库
            shop = getById(id);
            // 5.不存在，返回错误
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 6.存在，写入redis
            stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        }catch (InterruptedException e){
            throw new RuntimeException(e);
        }finally {
            // 7.返回,释放互斥锁
            unLock(lockKey);
        }


        return shop;
    }
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    // 使用逻辑过期时间解决缓存击穿问题
    public Shop queryWithLogicalExpire(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        // 1. 从redis中查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        // 2.判断是否存在
        if(StrUtil.isBlank(shopJson)) {
            // 3.不存在，直接返回
            return null;
        }
        // 4. 命中，需要先把json反序列化为对象，
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);

        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5. 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // a) 未过期，直接返回店铺信息
            return shop;
        }
        // b) 过期，进行缓存重建

        // 6.缓存重建
        String lockKey = LOCK_SHOP_KEY + id;
        // a) 获取互斥锁
        boolean isLock = tryLock(lockKey);

        // b) 判断是否获取锁成功
        if (isLock) {
            // c) 成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
               try {
                   //缓存重建
                   this.saveShop2Redis(id, CACHE_SHOP_TTL);
               }catch (Exception e){
                    throw new RuntimeException(e);
               }finally {
                   //释放锁
                   unLock(lockKey);
               }
            });

        }
        // d) 失败，返回过期店铺信息

        return shop;
    }

    // 缓存穿透
    public Shop queryWithPassThrough(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        // 1. 从redis中查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        // 2.判断是否存在
        if(StrUtil.isNotBlank(shopJson)) {
            // 3.存在，直接返回

            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 如果命中结果是空值
        if( shopJson != null) {
            // 返回错误信息
            return null;
        }
        // 4.不存在，根据id查询数据库
        Shop shop = getById(id);

        // 5.不存在，返回错误
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 6.存在，写入redis
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 7.返回

        return shop;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    public void saveShop2Redis(Long id, Long expireSeconds){
        // 查询店铺数据
        Shop shop = getById(id);
        // 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            // 判断是否符合要求
            return Result.fail("店铺id不能为空");
        }
        // 1. 更新数据库
        updateById(shop);
        // 2.删除缓存

        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
