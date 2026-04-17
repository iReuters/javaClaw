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
    private String memoryId;
    private String toolName;
    private String skillName;  // 调用时所在的 skill 名称，没有则为空
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