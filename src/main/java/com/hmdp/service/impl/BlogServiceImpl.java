package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

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

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryBlogById(Long id) {
        // 1. 查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("该笔记不存在");
        }
        // 2. 查询用户信息
        queryBlogUser(blog);

        isBlogUser(blog);
        
        return Result.ok(blog);
    }

    /**
     * 查询博客用户信息
     * @param blog 博客对象
     */
    private void queryBlogUser(Blog blog) {
        // 查询用户信息
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    /**
     * 判断用户是否点赞博客
     * @param blog 博客对象
     */
    private void isBlogUser(Blog blog) {
        // 1. 获取登录用户
        Long userId = UserHolder.getUser().getId();
        // 2. 判断是否点赞
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(RedisConstants.BLOG_LIKED_KEY + blog.getId(), userId.toString());

        blog.setIsLike(BooleanUtil.isTrue(isMember));
    }


    /**
     * 点赞博客
     * @param id 博客id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        // 1. 获取登录用户
        Long userId = UserHolder.getUser().getId();
        // 2. 判断是否点赞
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(RedisConstants.BLOG_LIKED_KEY + id, userId.toString());
        if(BooleanUtil.isFalse(isMember)){
            // 3. 未点赞可用点赞
            // 3.1 数据库点赞+1
            boolean isSucceed = update().setSql("liked = liked + 1").eq("id", id).update();
            // 3.2 保存用户id到Redis中的set集合中
            if(BooleanUtil.isTrue(isSucceed)){
                stringRedisTemplate.opsForSet().add(RedisConstants.BLOG_LIKED_KEY + id, userId.toString());
            }
        }else {
            // 4. 如果已经点赞取消点赞
            // 4.1 数据库点赞-1
            boolean isSucceed = update().setSql("liked = liked - 1").eq("id", id).update();
            // 4.2 把用户从Redis中的set集合中删除
            if(BooleanUtil.isTrue(isSucceed)) {
                stringRedisTemplate.opsForSet().remove(RedisConstants.BLOG_LIKED_KEY + id, userId.toString());
            }
        }


        return Result.ok();
    }

    /**
     * 查询热门博客
     * @param current 当前页码
     * @return 博客列表
     */
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogUser(blog);
        });
        return Result.ok(records);
    }
}
