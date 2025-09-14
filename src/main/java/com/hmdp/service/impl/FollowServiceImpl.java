package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

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
            save(follow);
        }else {
            // 3. 如果取消关注，删除关注记录
            remove(new QueryWrapper<Follow>().eq("user_id",userId).eq("follow_user_id",id));

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
}
