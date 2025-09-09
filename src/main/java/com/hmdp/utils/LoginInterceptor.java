package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: 李阳
 * @Date: 2025/09/08/18:52
 * @Description: 拦截器,判断要不要拦截即可
 */
public class LoginInterceptor implements HandlerInterceptor {




    /**
     * 前置拦截
     * @param request 请求
     * @param response 响应
     * @param handler 处理器
     * @return true/false
     * @throws Exception 异常
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 1. 判断是否需要拦截
        if(UserHolder.getUser() == null){
            response.setStatus(401);
            // 拦截
            return false;
        }

        // 有用户则放行

        return true;
    }

}
