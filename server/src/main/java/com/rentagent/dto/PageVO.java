package com.rentagent.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.function.Function;

/** 分页响应包装 */
public record PageVO<T>(List<T> list, long total, long page, long size) {

    public static <T> PageVO<T> of(IPage<T> p) {
        return new PageVO<>(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    /**
     * 实体页 → VO 页，保留分页元数据。
     * 早先各处都手写"new Page + setRecords + 再包一层"的样板，写漏一处就会丢掉 total/page。
     */
    public static <E, V> PageVO<V> map(IPage<E> page, Function<E, V> mapper) {
        return new PageVO<>(page.getRecords().stream().map(mapper).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }
}
