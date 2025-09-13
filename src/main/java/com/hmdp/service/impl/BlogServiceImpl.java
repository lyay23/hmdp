package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Override
    public Result queryBlogById(Long id) {
        // 1. 查询blog
        Blog byId = getById(id);
        if (byId == null) {
            return Result.fail("该笔记不存在");
        }
        // 2. 查询用户信息
        Long userId = byId.getUserId();
        User user = userService.getById(userId);
        byId.setName(user.getNickName());
        byId.setIcon(user.getIcon());

        return Result.ok(byId);
    }



}
