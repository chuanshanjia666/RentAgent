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
            AuditLog entry = new AuditLog();
            entry.setOperatorId(operatorId);
            entry.setAction(action);
            entry.setTargetType(targetType);
            entry.setTargetId(targetId);
            entry.setDetail(detail == null ? null : objectMapper.writeValueAsString(detail));
            entry.setIp(ip);
            mapper.insert(entry);
        } catch (Exception e) {
            log.warn("审计日志写入失败: {}", e.getMessage());
        }
    }
}
