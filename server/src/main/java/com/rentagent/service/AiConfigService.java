package com.rentagent.service;

import com.rentagent.dto.AdminDto;

/**
 * AI 供应商配置管理服务（管理员在线可视化配置，结果入库持久化并支持运行时热切换）。
 */
public interface AiConfigService {

    /** 获取所有 AI 供应商配置列表与当前运行时生效状态 */
    AdminDto.AiConfigOverviewVO getOverview();

    /** 保存新 AI 供应商配置并持久化入库 */
    AdminDto.AiConfigVO save(AdminDto.AiConfigSaveReq req, Long adminId);

    /** 更新指定的 AI 供应商配置 */
    AdminDto.AiConfigVO update(Long id, AdminDto.AiConfigSaveReq req, Long adminId);

    /** 激活指定的 AI 供应商配置（设为全站生效） */
    void activate(Long id, Long adminId);

    /** 停用所有数据库中的生效配置（回退为配置文件/环境变量或未配置状态） */
    void deactivate(Long adminId);

    /** 删除指定的 AI 供应商配置（生效中的不可删除） */
    void delete(Long id, Long adminId);

    /** 连通性测试（验证指定配置是否能正常调用大模型，返回耗时与状态） */
    AdminDto.AiConfigTestVO testConnection(AdminDto.AiConfigTestReq req);

    /** 从 application.yml / 环境变量导入当前配置并存入数据库 */
    AdminDto.AiConfigVO importFromFile(Long adminId);
}

