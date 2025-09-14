package com.hmdp.dto;

import lombok.Data;

import java.util.List;

/**
 * @author 李阳
 * @date 2025/12/22 16:59
 * @description: 滚动分页
 */
@Data
public class ScrollResult {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
