package com.rentagent.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/** 分页响应包装 */
public record PageVO<T>(List<T> list, long total, long page, long size) {

    public static <T> PageVO<T> of(IPage<T> p) {
        return new PageVO<>(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }
}
