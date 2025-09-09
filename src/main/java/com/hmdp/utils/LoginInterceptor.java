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
 * @Description: 拦截器
 */
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {


    private final StringRedisTemplate stringRedisTemplate;

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

        // 1. 获取请求头中的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            // 不存在进行拦截
            response.setStatus(401);
            return false;
        }
        // 2. 基于Token从Redis中获取用户信息
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);

        // 3. 判断用户信息是否为空
        if (userMap.isEmpty()){
            // 4. 如果为空，返回登录页面,进行拦截
            response.setStatus(401);
            return false;
        }

        // 5. 将HashMap转换为UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        // 6. 如果不为空，保存用户信息到ThreadLocal中
        UserHolder.saveUser(userDTO);

        // 7. 刷新token有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 8. 返回true，放行
        return true;
    }


    /**
     * 后置拦截
     * @param request 请求
     * @param response 响应
     * @param handler 处理器
     * @param ex 异常
     * @throws Exception 异常
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户
        UserHolder.removeUser();
    }
}
