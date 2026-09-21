package com.rentagent.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    /** 分页 size 上限：接口约定 page/size 默认 10、上限 50（见 AGENTS.md 与概要设计），在此统一兜底 */
    private static final long MAX_PAGE_SIZE = 50L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 逐个服务层夹 size 会漏（曾只有 SearchService 做了），漏掉的那个接口
        // 一次 ?size=100000 就能把整表拉走；这里统一把 size 压到上限
        pagination.setMaxLimit(MAX_PAGE_SIZE);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
