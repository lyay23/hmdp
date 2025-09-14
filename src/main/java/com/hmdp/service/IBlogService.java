package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 查询博客详情
     * @param id
     * @return
     */
    Result queryBlogById(Long id);

    /**
     * 点赞博客
     * @param id 博客id
     * @return 点赞结果
     */
    Result likeBlog(Long id);

    /**
     * 查询热门博客
     * @param current 当前页码
     * @return 博客列表
     */
    Result queryHotBlog(Integer current);

    /**
     * 查询点赞排行
     * @param id
     * @return
     */
    Result queryBlogLikes(Long id);

    /**
     * 保存博客
     * @param blog 博客实体
     * @return 返回
     */
    Result saveBlog(Blog blog);

    /**
     * 滚动分页查询
     * @param max 最大值
     * @param offset 偏移量
     * @return 返回
     */
    Result queryBlogFollow(Long max, Integer offset);
}
