-- 安全监控审计日志表
CREATE TABLE IF NOT EXISTS agent_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    memory_id VARCHAR(128) COMMENT '会话/记忆ID',
    tool_name VARCHAR(64) COMMENT '工具名称',
    skill_name VARCHAR(64) COMMENT '调用的技能名称，无则为NULL',
    call_time DATETIME(3) COMMENT '调用时间',
    duration_ms INT COMMENT '执行时长(毫秒)',
    llm_model VARCHAR(64) COMMENT 'LLM模型',
    iteration_count INT COMMENT '当前迭代次数',
    params TEXT COMMENT '调用参数(JSON,脱敏)',
    result TEXT COMMENT '执行结果',
    success BOOLEAN COMMENT '是否成功',
    error_msg TEXT COMMENT '错误信息',
    cre_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_memory_id (memory_id),
    INDEX idx_tool_name (tool_name),
    INDEX idx_call_time (call_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体工具调用审计日志';