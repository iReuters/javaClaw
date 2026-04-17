# 安全监控功能实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 为金融行业智能体添加安全可控的监控能力，包括 Skill/Tool 调用记录审计和可用资源查询接口

**架构：** 使用 AOP 拦截工具执行，异步记录审计日志，提供 REST API 查询

**技术栈：** Spring Boot, MyBatis, AOP, Lombok

---

## 文件结构

| 文件 | 职责 |
|------|------|
| `src/main/java/com/javaclaw/entity/AuditLog.java` | 审计日志实体类 |
| `src/main/java/com/javaclaw/mapper/AuditLogMapper.java` | 审计日志 Mapper 接口 |
| `src/main/resources/mybatis/mapper/AuditLogMapper.xml` | MyBatis XML 映射 |
| `src/main/java/com/javaclaw/service/AuditLogService.java` | 审计日志服务 |
| `src/main/java/com/javaclaw/aspect/SecurityMonitorAspect.java` | 安全监控切面 |
| `src/main/java/com/javaclaw/controller/AuditController.java` | 审计和查询 API |
| `src/main/resources/schema.sql` | 数据库表初始化 SQL |

---

## 任务 1：创建 AuditLog 实体类

**文件：**
- 创建：`src/main/java/com/javaclaw/entity/AuditLog.java`

- [ ] **步骤 1：创建实体类**

```java
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
```

- [ ] **步骤 2：Commit**

```bash
git add src/main/java/com/javaclaw/entity/AuditLog.java
git commit -m "feat: 添加 AuditLog 实体类"
```

---

## 任务 2：创建 AuditLogMapper

**文件：**
- 创建：`src/main/java/com/javaclaw/mapper/AuditLogMapper.java`
- 创建：`src/main/resources/mybatis/mapper/AuditLogMapper.xml`

- [ ] **步骤 1：创建 Mapper 接口**

```java
package com.javaclaw.mapper;

import com.javaclaw.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface AuditLogMapper {

    void insert(AuditLog auditLog);

    AuditLog findById(@Param("id") Long id);

    List<AuditLog> findBySessionId(@Param("sessionId") String sessionId);

    List<AuditLog> findByToolName(@Param("toolName") String toolName);

    List<AuditLog> findByTimeRange(@Param("startTime") String startTime, @Param("endTime") String endTime);

    List<AuditLog> findByPage(@Param("offset") int offset, @Param("limit") int limit);

    Long countAll();

    List<Map<String, Object>> statsByTool();

    List<Map<String, Object>> statsByDay();
}
```

- [ ] **步骤 2：创建 MyBatis XML**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.javaclaw.mapper.AuditLogMapper">

    <insert id="insert" parameterType="com.javaclaw.entity.AuditLog" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO agent_audit_log (session_id, tool_name, tool_type, call_time, duration_ms,
            llm_model, iteration_count, params, result, success, error_msg, cre_time)
        VALUES (#{sessionId}, #{toolName}, #{toolType}, #{callTime}, #{durationMs},
            #{llmModel}, #{iterationCount}, #{params}, #{result}, #{success}, #{errorMsg}, NOW())
    </insert>

    <select id="findById" resultType="com.javaclaw.entity.AuditLog">
        SELECT * FROM agent_audit_log WHERE id = #{id}
    </select>

    <select id="findBySessionId" resultType="com.javaclaw.entity.AuditLog">
        SELECT * FROM agent_audit_log WHERE session_id = #{sessionId} ORDER BY call_time DESC
    </select>

    <select id="findByToolName" resultType="com.javaclaw.entity.AuditLog">
        SELECT * FROM agent_audit_log WHERE tool_name = #{toolName} ORDER BY call_time DESC
    </select>

    <select id="findByTimeRange" resultType="com.javaclaw.entity.AuditLog">
        SELECT * FROM agent_audit_log
        WHERE call_time BETWEEN #{startTime} AND #{endTime}
        ORDER BY call_time DESC
    </select>

    <select id="findByPage" resultType="com.javaclaw.entity.AuditLog">
        SELECT * FROM agent_audit_log ORDER BY call_time DESC LIMIT #{offset}, #{limit}
    </select>

    <select id="countAll" resultType="long">
        SELECT COUNT(*) FROM agent_audit_log
    </select>

    <select id="statsByTool" resultType="java.util.Map">
        SELECT tool_name as toolName, COUNT(*) as callCount,
            SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) as successCount
        FROM agent_audit_log
        GROUP BY tool_name
        ORDER BY callCount DESC
    </select>

    <select id="statsByDay" resultType="java.util.Map">
        SELECT DATE(call_time) as day, COUNT(*) as callCount,
            SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) as successCount
        FROM agent_audit_log
        GROUP BY DATE(call_time)
        ORDER BY day DESC
    </select>

</mapper>
```

- [ ] **步骤 3：Commit**

```bash
git add src/main/java/com/javaclaw/mapper/AuditLogMapper.java src/main/resources/mybatis/mapper/AuditLogMapper.xml
git commit -m "feat: 添加 AuditLogMapper"
```

---

## 任务 3：创建 AuditLogService

**文件：**
- 创建：`src/main/java/com/javaclaw/service/AuditLogService.java`

- [ ] **步骤 1：创建 Service**

```java
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
```

- [ ] **步骤 2：Commit**

```bash
git add src/main/java/com/javaclaw/service/AuditLogService.java
git commit -m "feat: 添加 AuditLogService"
```

---

## 任务 4：创建 SecurityMonitorAspect 切面

**文件：**
- 创建：`src/main/java/com/javaclaw/aspect/SecurityMonitorAspect.java`

- [ ] **步骤 1：创建切面**

```java
package com.javaclaw.aspect;

import com.javaclaw.service.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Aspect
@Component
@Slf4j
public class SecurityMonitorAspect {

    @Autowired
    private AuditLogService auditLogService;

    private static final String DEFAULT_LLM_MODEL = "deepseek-chat";
    private static final String DEFAULT_SESSION_ID = "unknown";
    private static final String DEFAULT_TOOL_TYPE = "tool";

    @Pointcut("execution(* com.javaclaw.agent.tools.ToolRegistry.execute(..))")
    public void toolExecutePointcut() {}

    @Pointcut("execution(* com.javaclaw.agent.tools.DynamicToolLoader.executeDynamicTool(..))")
    public void dynamicToolExecutePointcut() {}

    @Around("toolExecutePointcut() || dynamicToolExecutePointcut()")
    public Object aroundToolExecute(ProceedingJoinPoint joinPoint) throws Throwable {
        String toolName = extractToolName(joinPoint);
        long startTime = System.currentTimeMillis();
        String sessionId = extractSessionId(joinPoint);
        String result;
        boolean success = true;
        String errorMsg = null;

        try {
            result = (String) joinPoint.proceed();
        } catch (Throwable t) {
            success = false;
            errorMsg = t.getMessage();
            result = "[Error: " + t.getMessage() + "]";
            throw t;
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;
            Map<String, Object> params = extractParams(joinPoint);

            auditLogService.logToolCall(
                    sessionId,
                    toolName,
                    DEFAULT_TOOL_TYPE,
                    durationMs,
                    DEFAULT_LLM_MODEL,
                    0,
                    params,
                    result,
                    success,
                    errorMsg
            );
        }
    }

    private String extractToolName(ProceedingJoinPoint joinPoint) {
        String signature = joinPoint.getSignature().toShortString();
        if (signature.contains("execute")) {
            Object[] args = joinPoint.getArgs();
            if (args.length > 0 && args[0] instanceof String) {
                return (String) args[0];
            }
        }
        return "unknown";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractParams(ProceedingJoinPoint joinPoint) {
        Map<String, Object> params = new HashMap<>();
        Object[] args = joinPoint.getArgs();
        if (args.length > 1 && args[1] instanceof Map) {
            params.putAll((Map<String, Object>) args[1]);
        }
        params.remove("channel");
        params.remove("chatId");
        params.remove("metadata");
        return params;
    }

    private String extractSessionId(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length > 1 && args[1] instanceof Map) {
            Map<String, Object> params = (Map<String, Object>) args[1];
            Object sessionId = params.get("sessionId");
            if (sessionId != null) {
                return sessionId.toString();
            }
        }
        return DEFAULT_SESSION_ID;
    }
}
```

- [ ] **步骤 2：确保启用 AspectJ**

检查 `JavaclawApplication.java` 是否有 `@EnableAspectJAutoProxy`（Spring Boot 默认启用，通常不需要显式添加）

- [ ] **步骤 3：Commit**

```bash
git add src/main/java/com/javaclaw/aspect/SecurityMonitorAspect.java
git commit -m "feat: 添加 SecurityMonitorAspect 切面"
```

---

## 任务 5：创建 AuditController 和查询 API

**文件：**
- 创建：`src/main/java/com/javaclaw/controller/AuditController.java`

- [ ] **步骤 1：创建 Controller**

```java
package com.javaclaw.controller;

import com.javaclaw.agent.SkillsLoader;
import com.javaclaw.agent.tools.DynamicToolLoader;
import com.javaclaw.agent.tools.ToolRegistry;
import com.javaclaw.entity.AuditLog;
import com.javaclaw.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AuditController {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private SkillsLoader skillsLoader;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private DynamicToolLoader dynamicToolLoader;

    @GetMapping("/audit/logs")
    public Map<String, Object> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<AuditLog> logs = auditLogService.getByPage(page, size);
        long total = auditLogService.countAll();
        Map<String, Object> result = new HashMap<>();
        result.put("logs", logs);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @GetMapping("/audit/logs/{id}")
    public AuditLog getLog(@PathVariable Long id) {
        return auditLogService.getById(id);
    }

    @GetMapping("/audit/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("byTool", auditLogService.getStatsByTool());
        stats.put("byDay", auditLogService.getStatsByDay());
        return stats;
    }

    @GetMapping("/skills")
    public List<Map<String, String>> getSkills() {
        return skillsLoader.listSkills(true);
    }

    @GetMapping("/tools")
    public Map<String, Object> getTools() {
        Map<String, Object> result = new HashMap<>();
        result.put("beanTools", toolRegistry.getToolNames());
        result.put("dynamicTools", new ArrayList<>(dynamicToolLoader.getAllDynamicToolNames()));
        return result;
    }
}
```

- [ ] **步骤 2：Commit**

```bash
git add src/main/java/com/javaclaw/controller/AuditController.java
git commit -m "feat: 添加 AuditController 查询 API"
```

---

## 任务 6：创建数据库表 SQL

**文件：**
- 创建：`src/main/resources/schema.sql`

- [ ] **步骤 1：创建 SQL 脚本**

```sql
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
```

- [ ] **步骤 2：Commit**

```bash
git add src/main/resources/schema.sql
git commit -m "feat: 添加 agent_audit_log 表结构"
```

---

## 任务 7：启用异步日志

**文件：**
- 修改：`src/main/java/com/javaclaw/JavaclawApplication.java`

- [ ] **步骤 1：检查并添加 @EnableAsync**

```java
package com.javaclaw;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class JavaclawApplication {
    public static void main(String[] args) {
        SpringApplication.run(JavaclawApplication.class, args);
    }
}
```

- [ ] **步骤 2：Commit**

```bash
git add src/main/java/com/javaclaw/JavaclawApplication.java
git commit -m "feat: 启用异步支持用于审计日志异步写入"
```

---

## 自检清单

- [ ] 所有实体类字段与数据库表对应
- [ ] Mapper XML 中 SQL 语句正确
- [ ] 敏感参数脱敏逻辑完整
- [ ] 异步日志不影响主流程
- [ ] API 返回格式一致

---

## 执行交接

计划已完成并保存到 `docs/superpowers/plans/2026-04-17-security-monitoring-plan.md`。

**两种执行方式：**

**1. 子代理驱动（推荐）** - 每个任务调度一个新的子代理，任务间进行审查，快速迭代

**2. 内联执行** - 在当前会话中使用 executing-plans 执行任务，批量执行并设有检查点

选哪种方式？