# Skill 路由架构重构设计

## 需求概述

重构 skill 路由机制，实现：
- 技能清单元数据注入，帮助 LLM 语义匹配最合适的 skill
- 追踪当前请求使用了哪个 skill
- 审计日志能记录 skill 与 tool 的关联

## 架构设计

### 核心思路：两阶段 Skill 路由

```
用户消息 → LLM（带 skill 清单）→ 模型选择最佳 skill → 加载完整 skill 内容 → 执行
```

### System Prompt 注入格式

```
=== Available Skills ===
请根据用户意图选择最匹配的技能，若无匹配或意图不明显，使用 metadata：

1. metadata (始终可用)
   描述：基础助手能力，支持通用对话、问答、计算、翻译等

2. news-skill
   描述：获取最新新闻资讯，支持搜索和分类浏览

3. stock-skill
   描述：查询股票价格、涨跌幅、市场行情

4. weather-skill
   描述：查询城市天气、空气质量、天气预报

请根据用户"[用户消息]"，回复：USE_SKILL: <skill_id>
```

### LLM 选择逻辑

模型收到后，回复格式：`USE_SKILL: <skill_id>`

- `USE_SKILL: metadata` → 使用 metadata skill（基础能力，兜底）
- `USE_SKILL: news-skill` → 使用 news skill
- 其他 → 对应 skill

### 流程图

```
用户消息
    ↓
buildMessages()
    ↓
注入 skill 清单（所有 enabled skills + metadata）
    ↓
LLM 第一次调用（选择 skill）
    ↓
解析 LLM 回复：USE_SKILL: xxx
    ↓
loadSkillContent(skillId) → 完整 content
    ↓
继续执行 tool_calls + 审计
```

## 数据库设计

### agent_skills 表扩展

| 字段 | 类型 | 说明 |
|------|------|------|
| upd_time | TIMESTAMP | 最后更新时间，用于元数据缓存失效 |

## 组件改动

| 组件 | 改动 |
|------|------|
| `SkillsLoader` | 新增 `buildSkillListForPrompt()` 方法生成技能清单 |
| `ContextBuilder` | system prompt 注入 skill 清单 |
| `AgentLoop` | 模型返回 skillId → 加载完整 skill → 执行 |
| `SkillContext` | 复用已有的 ThreadLocal 机制 |

## 元数据更新机制

| 事件 | 触发 |
|------|------|
| 新增 skill | 插入时 `upd_time = NOW()` |
| 更新 skill | `upd_time = NOW()` |
| 删除 skill | 删除记录 |

## 关键约束

1. **单 skill 激活**：每次请求只激活一个 skill
2. **metadata 兜底**：当所有 skill 匹配度都低时，使用 metadata 作为默认能力
3. **降级路径**：若模型未按格式回复，回退到 metadata skill