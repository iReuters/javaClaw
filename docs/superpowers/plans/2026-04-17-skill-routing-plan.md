# Skill 路由架构重构实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 实现 skill 语义路由机制，LLM 根据 skill 清单选择最匹配的 skill

**架构：** 两阶段路由：1) LLM 选择 skill；2) 加载完整 skill 内容执行

**技术栈：** Spring Boot, MyBatis, Java

---

## 文件结构

| 文件 | 职责 |
|------|------|
| `SkillsLoader.java` (修改) | 新增 `buildSkillListForPrompt()` 生成技能清单 |
| `ContextBuilder.java` (修改) | system prompt 注入 skill 清单 |
| `AgentLoop.java` (修改) | 解析 LLM 回复选择 skill，加载完整内容 |
| `SkillRecord.java` (修改) | 新增 `updTime` 字段 |
| `schema.sql` (修改) | agent_skills 表增加 upd_time 字段 |

---

## 任务 1：数据库表扩展

**文件：**
- 修改：`src/main/resources/schema.sql`

- [ ] **步骤 1：添加 upd_time 字段**

在 agent_skills 表中添加 `upd_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` 字段

```sql
ALTER TABLE agent_skills ADD COLUMN upd_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';
```

- [ ] **步骤 2：Commit**

```bash
git add src/main/resources/schema.sql
git commit -m "feat: agent_skills 表增加 upd_time 字段"
```

---

## 任务 2：SkillRecord 实体增加 updTime 字段

**文件：**
- 修改：`src/main/java/com/javaclaw/entity/SkillRecord.java`

- [ ] **步骤 1：添加字段**

```java
import java.sql.Timestamp;

/**
 * 更新时间
 * <p>记录该技能的最后修改时间戳，用于元数据缓存失效
 */
private Timestamp updTime;
```

- [ ] **步骤 2：Commit**

```bash
git add src/main/java/com/javaclaw/entity/SkillRecord.java
git commit -m "feat: SkillRecord 增加 updTime 字段"
```

---

## 任务 3：SkillsLoader 新增 buildSkillListForPrompt 方法

**文件：**
- 修改：`src/main/java/com/javaclaw/agent/SkillsLoader.java`

- [ ] **步骤 1：新增方法**

在 `SkillsLoader.java` 中新增 `buildSkillListForPrompt()` 方法：

```java
/**
 * 构建 skill 清单用于 prompt 注入
 * 格式：
 * 1. metadata (始终可用)
 *    描述：基础助手能力...
 * 2. news-skill
 *    描述：...
 */
public String buildSkillListForPrompt() {
    List<SkillRecord> records = skillMapper.findAllEnabled();
    if (records == null || records.isEmpty()) {
        return "";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("请根据用户意图选择最匹配的技能，若无匹配或意图不明显，使用 metadata：\n\n");

    // metadata 固定在第一个位置
    sb.append("1. metadata (始终可用)\n");
    sb.append("   描述：基础助手能力，支持通用对话、问答、计算、翻译等\n\n");

    int idx = 2;
    for (SkillRecord record : records) {
        // 跳过 metadata，因为已手动添加
        if ("metadata".equals(record.getSkillId())) {
            continue;
        }
        sb.append(idx).append(". ").append(record.getSkillId()).append("\n");
        sb.append("   描述：").append(record.getDescription() != null ? record.getDescription() : "").append("\n\n");
        idx++;
    }

    return sb.toString();
}
```

- [ ] **步骤 2：Commit**

```bash
git add src/main/java/com/javaclaw/agent/SkillsLoader.java
git commit -m "feat: SkillsLoader 新增 buildSkillListForPrompt 方法"
```

---

## 任务 4：ContextBuilder 修改 system prompt 注入

**文件：**
- 修改：`src/main/java/com/javaclaw/agent/ContextBuilder.java`

- [ ] **步骤 1：修改 buildSystemPrompt 方法**

在 `buildSystemPrompt()` 方法中，在 skill 内容之前注入 skill 清单：

```java
public String buildSystemPrompt(List<String> skillNames) {
    StringBuilder sb = new StringBuilder();
    sb.append("Current time: ").append(ZonedDateTime.now()).append("\n\n");
    sb.append("Workspace: ").append(workspace).append("\n\n");
    for (String name : BOOTSTRAP_FILES) {
        Path p = workspace.resolve(name);
        if (Files.isRegularFile(p)) {
            try {
                String content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                sb.append("--- ").append(name).append(" ---\n").append(content).append("\n\n");
            } catch (Exception e) {
                // skip
            }
        }
    }

    // 注入 skill 清单
    sb.append("=== Available Skills ===\n");
    sb.append(skillsLoader.buildSkillListForPrompt());
    sb.append("\n请根据用户请求，回复：USE_SKILL: <skill_id>\n\n");

    if (skillNames != null && !skillNames.isEmpty()) {
        sb.append(skillsLoader.loadSkillsForContext(skillNames));
    }
    sb.append(skillsLoader.buildSkillsSummary());
    return sb.toString();
}
```

- [ ] **步骤 2：Commit**

```bash
git add src/main/java/com/javaclaw/agent/ContextBuilder.java
git commit -m "feat: ContextBuilder 注入 skill 清单到 system prompt"
```

---

## 任务 5：AgentLoop 实现 skill 选择逻辑

**文件：**
- 修改：`src/main/java/com/javaclaw/agent/AgentLoop.java`

- [ ] **步骤 1：修改 runAgentLoop 方法**

在 `runAgentLoop` 中，解析 LLM 第一次回复获取 skillId：

```java
public RunResult runAgentLoop(List<Map<String, Object>> initialMessages, Consumer<String> streamConsumer, Map<String, Object> requestContext, String skillName, Consumer<AgentStep> stepCallback) {
    List<Map<String, Object>> messages = new ArrayList<>(initialMessages);
    List<String> toolsUsed = new ArrayList<>();
    List<AgentStep> steps = new ArrayList<>();
    int iter = 0;

    // 第一次 LLM 调用：让模型选择 skill
    LLMResponse firstResponse = provider.chat(
            messages,
            null,  // 第一次不需要 tool definitions
            model,
            maxTokens,
            temperature,
            streamConsumer);

    // 解析 LLM 回复，获取 skillId
    String selectedSkillId = parseSkillIdFromResponse(firstResponse.getContent());
    log.info("LLM selected skill: {}", selectedSkillId);

    // 加载选中的 skill 内容
    String skillContent = skillsLoader.loadSkill(selectedSkillId).orElse("");
    if (!skillContent.isEmpty()) {
        // 用 skill 内容更新 system message
        updateSystemMessageWithSkill(messages, selectedSkillId, skillContent);
    }

    // 设置当前 skill 到 ThreadLocal，供 AOP 切面获取
    SkillContext.setSkill(selectedSkillId);
    try {
        // 继续正常执行流程...
    } finally {
        SkillContext.clear();
    }
}

/**
 * 从 LLM 回复中解析 skillId
 * 期望格式：USE_SKILL: <skill_id>
 */
private String parseSkillIdFromResponse(String content) {
    if (content == null || content.isEmpty()) {
        return "metadata";
    }
    // 查找 USE_SKILL: xxx 模式
    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("USE_SKILL:\\s*(\\S+)");
    java.util.regex.Matcher matcher = pattern.matcher(content);
    if (matcher.find()) {
        return matcher.group(1);
    }
    // 如果没匹配到，回退到 metadata
    log.warn("Failed to parse skillId from response: {}", content);
    return "metadata";
}

/**
 * 用 skill 内容更新 system message
 */
private void updateSystemMessageWithSkill(List<Map<String, Object>> messages, String skillId, String skillContent) {
    if (messages.isEmpty()) return;
    Map<String, Object> systemMsg = messages.get(0);
    String existingContent = (String) systemMsg.getOrDefault("content", "");
    // 在现有 system prompt 末尾追加 skill 内容
    String updatedContent = existingContent + "\n\n=== Selected Skill: " + skillId + " ===\n\n" + skillContent;
    systemMsg.put("content", updatedContent);
}
```

- [ ] **步骤 2：Commit**

```bash
git add src/main/java/com/javaclaw/agent/AgentLoop.java
git commit -m "feat: AgentLoop 实现 skill 选择逻辑"
```

---

## 自检清单

- [ ] skill 清单正确生成（metadata 在第一位）
- [ ] LLM 回复能正确解析 skillId
- [ ] 未匹配时回退到 metadata
- [ ] SkillContext 正确设置和清理

---

## 执行交接

计划已完成并保存到 `docs/superpowers/plans/2026-04-17-skill-routing-plan.md`。

**两种执行方式：**

**1. 子代理驱动（推荐）** - 每个任务调度一个新的子代理，任务间进行审查，快速迭代

**2. 内联执行** - 在当前会话中使用 executing-plans 执行任务，批量执行并设有检查点

选哪种方式？