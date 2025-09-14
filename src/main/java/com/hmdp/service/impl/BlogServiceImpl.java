package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

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
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null){
            // 用户未登录无需查询是否点赞
            return ;
        }
        Long userId = UserHolder.getUser().getId();
        // 2. 判断是否点赞
        Double score = stringRedisTemplate.opsForZSet().score(RedisConstants.BLOG_LIKED_KEY + blog.getId(), userId.toString());

        blog.setIsLike(score!=null);
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
        Double score = stringRedisTemplate.opsForZSet().score(RedisConstants.BLOG_LIKED_KEY + id, userId.toString());
        if(score==null){
            // 3. 未点赞可用点赞
            // 3.1 数据库点赞+1
            boolean isSucceed = update().setSql("liked = liked + 1").eq("id", id).update();
            // 3.2 保存用户id到Redis中的set集合中
            if(BooleanUtil.isTrue(isSucceed)){
                stringRedisTemplate.opsForZSet().add(RedisConstants.BLOG_LIKED_KEY + id, userId.toString(),System.currentTimeMillis());
            }
        }else {
            // 4. 如果已经点赞取消点赞
            // 4.1 数据库点赞-1
            boolean isSucceed = update().setSql("liked = liked - 1").eq("id", id).update();
            // 4.2 把用户从Redis中的set集合中删除
            if(BooleanUtil.isTrue(isSucceed)) {
                stringRedisTemplate.opsForZSet().remove(RedisConstants.BLOG_LIKED_KEY + id, userId.toString());
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


    /**
     * 查询点赞排行
     * @param id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        String key =RedisConstants.BLOG_LIKED_KEY + id;
        // 1. 查询top5的用户，zrang key 0 4
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(top5==null|| top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        // 2. 解析出其中的用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());

        String idStr= StrUtil.join(",",ids);
        // 3. 根据用户id查询用户
        List<UserDTO> userDtos = userService.query()
                .in("id",ids)
                .last("ORDER BY FIELD(id,"+idStr+")").list()
                .stream()
                .map(user-> BeanUtil.copyProperties(user,UserDTO.class))
                .collect(Collectors.toList());

        // 4. 返回

        return Result.ok(userDtos);
    }


    /**
     * 保存博客
     * @param blog 博客实体
     * @return
     */
    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
       // 保存探店笔记
        boolean save = save(blog);

        if(!save){
            return Result.fail("新增笔记失败");
        }
        // 查询笔记的所有粉丝
        List<Follow> followUserId = followService.query().eq("follow_user_id", user.getId()).list();
        // 推送笔记id给所有粉丝
        for (Follow follow : followUserId) {
            // 获取粉丝id
            Long userId = follow.getUserId();
            // 推送
            String key ="follows:"+userId;
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }

        // 返回id
        return Result.ok(blog.getId());
    }
}
