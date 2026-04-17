# 安全监控功能设计

## 需求概述

为金融行业智能体添加安全可控的监控能力，包括：
- Skill 和 Tool 调用记录（增强审计）
- 可用 Skill 和 Tool 查询接口

## 审计字段（增强审计）

| 字段 | 说明 |
|------|------|
| sessionId | 会话ID |
| toolName | 工具/技能名称 |
| toolType | 类型（tool/skill） |
| callTime | 调用时间（毫秒精度） |
| durationMs | 执行时长 |
| llmModel | LLM模型 |
| iterationCount | 当前迭代次数 |
| params | 调用参数（JSON，脱敏） |
| result | 执行结果 |
| success | 是否成功 |
| errorMsg | 错误信息 |

## 架构设计

```
AgentLoop.executeTool()
    ↓ 拦截
SecurityMonitorAspect (AOP)
    ↓ 异步记录
AuditLogService
    ↓ 写入
agent_audit_log 表
    ↓ 提供
AuditController (REST API)
```

## 数据库设计

```sql
CREATE TABLE agent_audit_log (
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
);
```

## REST API

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/audit/logs` | GET | 分页查询审计日志 |
| `/api/audit/logs/{id}` | GET | 查询单条记录 |
| `/api/audit/stats` | GET | 统计信息 |
| `/api/skills` | GET | 查询所有可用技能 |
| `/api/tools` | GET | 查询所有可用工具 |

## 实现计划

1. 创建 AuditLog 实体类
2. 创建 AuditLogMapper
3. 创建 AuditLogService
4. 创建 SecurityMonitorAspect 切面
5. 创建 AuditController
6. 注册组件扫描

## 脱敏规则

- 密码类参数：替换为 `***`
- 敏感字段（password, token, key, secret）：值替换为 `***`
- 其他参数正常记录