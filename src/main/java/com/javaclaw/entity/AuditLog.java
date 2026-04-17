package com.javaclaw.entity;

import lombok.Data;
import java.sql.Timestamp;

/**
 * 审计日志实体类
 * 对应数据库表：agent_audit_log
 */
@Data
public class AuditLog {

    private Long id;
    private String sessionId;
    private String toolName;
    private String toolType;  // tool 或 skill
    private Timestamp callTime;
    private Integer durationMs;
    private String llmModel;
    private Integer iterationCount;
    private String params;    // JSON 格式，脱敏
    private String result;
    private Boolean success;
    private String errorMsg;
    private Timestamp creTime;
}