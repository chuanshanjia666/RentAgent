package com.rentagent.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单元测试辅助：为实体注册 MyBatis-Plus 表元数据。
 * 纯 Mockito 单测没有 MyBatis 容器初始化实体缓存，一旦代码需要渲染 lambda 列名
 * （如 {@code LambdaUpdateWrapper.set(...)}、{@code Wrapper.getSqlSegment()}）就会抛
 * "can not find lambda cache for this entity"。此处按需初始化，保证断言可读真实列名。
 */
public final class EntityMetadataHelper {

    private static final Set<Class<?>> INITIALIZED = ConcurrentHashMap.newKeySet();

    private EntityMetadataHelper() {
    }

    public static synchronized void init(Class<?>... entities) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> entity : entities) {
            if (INITIALIZED.add(entity)) {
                TableInfoHelper.initTableInfo(assistant, entity);
            }
        }
    }
}
