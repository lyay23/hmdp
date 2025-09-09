package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    /**
     * 发送验证码功能
     * @return
     */
    Result sendCode(String phone, HttpSession session);


    /**
     * 验证码登录功能
     * @param loginForm 手机号与验证码(密码)
     * @param session 用户信息存入session
     * @return 返回成功结果
     */
    Result login(LoginFormDTO loginForm, HttpSession session);

    /**
     * 登出
     * @param token 用户token
     * @return
     */
    Result loginOut(String token);
}
