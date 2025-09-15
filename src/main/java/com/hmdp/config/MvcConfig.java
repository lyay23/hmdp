package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: 李阳
 * @Date: 2025/09/08/19:12
 * @Description: 拦截器
 */
@Configuration
public class MvcConfig  implements WebMvcConfigurer {

    @Autowired
    private RefreshTokenInterceptor refreshTokenInterceptor;
    
    @Autowired
    private LoginInterceptor loginInterceptor;

    /**
     * 添加拦截器
     * @param registry 注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        /**
         * 拦截所有请求-token的拦截器
         */
        registry.addInterceptor(refreshTokenInterceptor).order(0);

        /**
         * 拦截部分请求-登录的拦截器
         */
        registry.addInterceptor(loginInterceptor)
                .excludePathPatterns(
                        "/shop/**",
                        "voucher/**",
                        "shop-type/**",
                        "/upload/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login"
                ).order(1);


    }
}
