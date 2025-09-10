package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: 李阳
 * @Date: 2025/09/10/9:57
 * @Description: Redis的相关字段
 */
@Data
public class RedisDate {
    private LocalDateTime expireTime;
    private Object data;
}
