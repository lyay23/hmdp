package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    /**
     * 关注
     * @param id 商铺id
     * @param isFollow 是否关注
     * @return 结果
     */
    @Override
    public Result follow(Long id, Boolean isFollow) {
        // 获取登录用户
        Long userId = UserHolder.getUser().getId();
        // 1. 判断是否关注
        if (isFollow) {
            // 2. 如果关注，添加关注记录
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            boolean save = save(follow);
            if (save) {
                // 把关注记录放入redis中
                String key = "follow:" + userId;
                stringRedisTemplate.opsForSet().add(key,id.toString());
            }
        }else {
            // 3. 如果取消关注，删除关注记录
            boolean remove = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", id));
            if (remove) {
                // 移除关注的用户id从Redis的set集合中
                stringRedisTemplate.opsForSet().remove("follow:" + userId,id.toString());
            }


        }


        return Result.ok();
    }

    /**
     * 查询是否关注
     * @param id 商铺id
     * @return 结果
     */
    @Override
    public Result ifFollow(Long id) {

        // 获取用户id
        Long userId = UserHolder.getUser().getId();
        // 查询是否关注
        Integer count = query().eq("user_id", userId).eq("follow_user_id", id).count();
        if (count > 0) {
            return Result.ok(true);
        }else {
            return Result.ok(false);
        }
    }

    /**
     * 查询共同关注
     * @param id 用户id
     * @return 结果
     */
    @Override
    public Result queryCommonFollow(Long id) {
        // 1. 获取登录用户id
        Long userId = UserHolder.getUser().getId();

        // 登录用户与目标用户共同关注的KEY
        String key1 = "follow:" + userId;
        String key2 = "follow:" + id;

        // 2. 求交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 3. 解析出id查询用户
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());

        // 4. 查询用户信息
        List<UserDTO> collect = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(collect);
    }
}
