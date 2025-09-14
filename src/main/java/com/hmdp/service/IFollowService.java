package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注
     * @param id 商铺id
     * @param isFollow 是否关注
     * @return 结果
     */
    Result follow(Long id, Boolean isFollow);

    /**
     * 查询是否关注
     * @param id 商铺id
     * @return 结果
     */
    Result ifFollow(Long id);
}
