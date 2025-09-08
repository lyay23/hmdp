package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: 李阳
 * @Date: 2025/09/08/18:52
 * @Description: 拦截器
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

        // 1. 获取session
        HttpSession session = request.getSession();
        // 2. 获取session中的用户信息
        Object user = session.getAttribute("user");
        // 3. 判断用户信息是否为空
        if (user == null){
            // 4. 如果为空，返回登录页面,进行拦截
            response.setStatus(401);
            return false;
        }

        // 5. 如果不为空，保存用户信息到ThreadLocal中
        UserHolder.saveUser((UserDTO) user);
        // 6. 返回true，放行
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
