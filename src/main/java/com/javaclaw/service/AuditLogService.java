package com.javaclaw.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javaclaw.entity.AuditLog;
import com.javaclaw.mapper.AuditLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class AuditLogService {

    @Autowired
    private AuditLogMapper auditLogMapper;

    private final ObjectMapper jsonMapper = new ObjectMapper();

    private static final Set<String> SENSITIVE_KEYS = new HashSet<>(Arrays.asList(
            "password", "token", "key", "secret", "apiKey", "api_key", "accessToken"
    ));

    @Async
    public void logToolCall(String sessionId, String toolName, String toolType,
                           long durationMs, String llmModel, int iterationCount,
                           Map<String, Object> params, String result, boolean success, String errorMsg) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setSessionId(sessionId);
            auditLog.setToolName(toolName);
            auditLog.setToolType(toolType);
            auditLog.setCallTime(new java.sql.Timestamp(System.currentTimeMillis()));
            auditLog.setDurationMs((int) durationMs);
            auditLog.setLlmModel(llmModel);
            auditLog.setIterationCount(iterationCount);
            auditLog.setParams(sanitizeParams(params));
            auditLog.setResult(truncateResult(result));
            auditLog.setSuccess(success);
            auditLog.setErrorMsg(errorMsg);
            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            log.error("Failed to log audit: ", e);
        }
    }

    private String sanitizeParams(Map<String, Object> params) {
        if (params == null) return "{}";
        try {
            Map<String, Object> sanitized = new HashMap<>(params);
            sanitized.replaceAll((k, v) -> {
                if (isSensitiveKey(k)) return "***";
                return v;
            });
            return jsonMapper.writeValueAsString(sanitized);
        } catch (Exception e) {
            return "{}";
        }
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String lowerKey = key.toLowerCase();
        for (String sensitive : SENSITIVE_KEYS) {
            if (lowerKey.contains(sensitive)) return true;
        }
        return false;
    }

    private String truncateResult(String result) {
        if (result == null) return null;
        return result.length() > 2000 ? result.substring(0, 2000) + "..." : result;
    }

    public AuditLog getById(Long id) {
        return auditLogMapper.findById(id);
    }

    public List<AuditLog> getBySessionId(String sessionId) {
        return auditLogMapper.findBySessionId(sessionId);
    }

    public List<AuditLog> getByPage(int page, int size) {
        int offset = page * size;
        return auditLogMapper.findByPage(offset, size);
    }

    public long countAll() {
        return auditLogMapper.countAll();
    }

    public List<Map<String, Object>> getStatsByTool() {
        return auditLogMapper.statsByTool();
    }

    public List<Map<String, Object>> getStatsByDay() {
        return auditLogMapper.statsByDay();
    }
}