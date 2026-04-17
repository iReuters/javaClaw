-- 安全监控审计日志表
CREATE TABLE IF NOT EXISTS agent_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(128),
    tool_name VARCHAR(64),
    tool_type ENUM('tool', 'skill'),
    call_time DATETIME(3),
    duration_ms INT,
    llm_model VARCHAR(64),
    iteration_count INT,
    params TEXT,
    result TEXT,
    success BOOLEAN,
    error_msg TEXT,
    cre_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_id (session_id),
    INDEX idx_tool_name (tool_name),
    INDEX idx_call_time (call_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;