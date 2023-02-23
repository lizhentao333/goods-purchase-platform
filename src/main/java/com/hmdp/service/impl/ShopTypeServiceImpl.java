package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;

import static com.hmdp.utils.RedisConstants.SHOP_TYPE_LIST;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        // 1.从redis获取对应的值，并将其转换为List<ShopType>
        String shopListStr = stringRedisTemplate.opsForValue().get(SHOP_TYPE_LIST);
//        log.debug("shopListStr:" + shopListStr);
        List<ShopType> shopTypeList = JSONUtil.toList(shopListStr, ShopType.class);
//        log.debug(shopTypeList.toString());
        // 2.判断是否为空

        if (!shopTypeList.isEmpty()) {
            // 3.不为空，则直接返回
            return Result.ok(shopTypeList);
        }
        // 4. 为空，继续查询数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();
        log.debug(typeList.toString());
        // 5.如果数据库不存在内容，那就报错
        if (typeList.isEmpty()) {
            return Result.fail("未查询到店铺类型信息");
        }
        // 6.将list实体类转换为json
        String jsonList = JSONUtil.toJsonStr(typeList);
//        log.debug(jsonList);
        // 7.存入redis中
        stringRedisTemplate.opsForValue().set(SHOP_TYPE_LIST, jsonList);

        return Result.ok(typeList);

    }
}
