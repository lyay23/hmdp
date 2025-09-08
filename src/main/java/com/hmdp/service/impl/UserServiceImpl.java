package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexPatterns;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {


    /**
     * 发送验证码功能
     * @param phone 手机号
     * @param session 保存到session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {

        //1. 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)){

            // 2. 如果不符合return错误信息
            return Result.fail("手机号格式错误");
        }

        // 3. 符合生成验证码- 利用hutool包随机生成6位数验证码
        String code = RandomUtil.randomNumbers(6);

        // 4. 保存验证码到session
        session.setAttribute("code",code);
        // 5. 发送验证码
        log.debug("验证码为："+code);

        return Result.ok();
    }


    /**
     * 登录功能
     * @param loginForm 手机号与验证码(密码)
     * @param session 用户信息存入session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        String phone = loginForm.getPhone();
        // 1. 校验手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())){

            // 2. 如果不符合return错误信息
            return Result.fail("手机号格式错误");
        }
        // 2. 校验验证码
        Object cashCode = session.getAttribute("code");
        String code = loginForm.getCode();
        if (cashCode == null || !cashCode.toString().equals(code)){
            // 3. 不一致报错
            return Result.fail("验证码错误");
        }

        // 4. 一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();
        // 5.判断用户是否存在
        if (user == null){
            // 6. 不存在，注册用户，保存到数据库
            user=createUserWithPhone(phone);
        }

        //7. 存在，登录，保存session
        session.setAttribute("user",user);

        return Result.ok();
    }

    /**
     * 注册用户
     * @param phone 根据手机号
     * @return 返回创建的用户信息
     */
    private User createUserWithPhone(String phone) {
        // 1. 创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX +RandomUtil.randomString(6));

        // 2. 保存到数据库
        save(user);
        return user;
    }
}
