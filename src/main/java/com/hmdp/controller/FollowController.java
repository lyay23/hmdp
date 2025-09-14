package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService followService;

    /**
     * 关注
     * @param id 商铺id
     * @param isFollow 是否关注
     * @return 结果
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow (@PathVariable("id") Long id, @PathVariable("isFollow") Boolean isFollow){

        return followService.follow(id,isFollow);
    }

    /**
     * 查询是否关注
     * @param id 商铺id
     * @return 结果
     */
    @GetMapping("/or/not/{id}")
    public Result isFollow (@PathVariable("id") Long id){

        return followService.ifFollow(id);
    }

    /**
     * 查询共同关注
     * @param id 用户id
     * @return 结果
     */
    @GetMapping("/common/{id}")
    public Result queryCommonFollow (@PathVariable("id") Long id){
        return followService.queryCommonFollow(id);
    }
}
