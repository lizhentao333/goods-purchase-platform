package com.hmdp.service.impl;

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
        // 1.从redis获取对应的值
        List<String> shopTypeList = stringRedisTemplate.opsForList().range(SHOP_TYPE_LIST, 0, -1);
        // 2.判断是否为空
        assert shopTypeList != null;
        if (!shopTypeList.isEmpty()) {
            // 3.不为空，则直接返回

            return Result.ok(shopTypeList);
        }
        // 4. 为空，继续查询数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();



    }
}
