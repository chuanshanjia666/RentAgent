package com.rentagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentagent.entity.AuditLog;
import com.rentagent.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/** 审计留痕（FR-07/22/23 验收要求：审核操作留痕） */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper mapper;
    private final ObjectMapper objectMapper;

    public void log(long operatorId, String action, String targetType, Long targetId, Map<String, Object> detail, String ip) {
        try {
            AuditLog log1 = new AuditLog();
            log1.setOperatorId(operatorId);
            log1.setAction(action);
            log1.setTargetType(targetType);
            log1.setTargetId(targetId);
            log1.setDetail(detail == null ? null : objectMapper.writeValueAsString(detail));
            log1.setIp(ip);
            mapper.insert(log1);
        } catch (Exception e) {
            log.warn("审计日志写入失败: {}", e.getMessage());
        }
    }
}
