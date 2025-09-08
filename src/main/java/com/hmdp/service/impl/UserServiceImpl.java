package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexPatterns;
import com.hmdp.utils.RegexUtils;
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
}
