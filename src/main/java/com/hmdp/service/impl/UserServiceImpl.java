package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

    @Resource
    private StringRedisTemplate stringRedisTemplate;

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

        // 4. 保存验证码到Redis,并且设置有效期2分钟
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
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
        if (RegexUtils.isPhoneInvalid(phone)){

            // 2. 如果不符合return错误信息
            return Result.fail("手机号格式错误");
        }
        // 2. 从Redis中获取验证码，并且校验

        String cashCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cashCode == null || !cashCode.equals(code)){
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

        //7. 存在，登录，保存到Redis
        // 7.1 随机生成token，作为令牌
        String token = UUID.randomUUID().toString(true);
        // 7.2 将User对象转换为Hash存储
        UserDTO userDTO=BeanUtil.copyProperties(user, UserDTO.class);
        // Info: 这里因为使用了StringRedisTemplate，要求字段必须为String类型,但是我们的id为Long类型，所以需要转换
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(), CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((field, value) -> value.toString())
        );
        // 7.3 保存到Redis中
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token, userMap);
        // 7.4 设置有效期3小时
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 8 返回token信息


        return Result.ok(token);
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


    /**
     * 退出登录
     * @param token 用户token
     * @return
     */
    @Override
    public Result loginOut(String token) {
        // 1. 获取当前登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }
        
        // 2. 从Redis中删除当前用户
        if (token != null && !token.isEmpty()) {
            stringRedisTemplate.delete(RedisConstants.LOGIN_USER_KEY + token);
        }
        
        // 3. 返回成功
        return Result.ok();
    }
}
