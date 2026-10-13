package com.rentagent.config;

/**
 * AI 供应商配置发生变更事件。
 * 当管理员在后台新增、修改、启用或停用 AI 供应商配置时触发，
 * 驱动 LLM 网关与智能体刷新运行时客户端，实现无重启平滑热更新。
 */
public record AiConfigChangedEvent(Long configId, String action) {
}

