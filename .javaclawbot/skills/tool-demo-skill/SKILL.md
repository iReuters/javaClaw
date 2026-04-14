---
name: tool-demo-skill
description: |
  演示 skill 动态调用工具的能力。支持：
  1. 调用已注册的 Spring Bean 工具（get_weather, get_exchange_rate）
  2. 调用动态加载的工具（get_server_time, calculate）

  当用户说「测试工具」「演示工具调用」「查看当前时间」「计算数学题」时使用。
tools: [get_weather, get_server_time, calculate]
---

# 工具演示 Skill

这个 skill 演示如何让 AI 通过 skill 调用各种工具。

## 可用工具

| 工具名 | 类型 | 说明 |
|--------|------|------|
| get_weather | Spring Bean | 查询城市天气 |
| get_server_time | 动态工具 | 获取服务器当前时间 |
| calculate | 动态工具 | 执行数学计算 |

## 使用示例

- 「北京今天天气怎么样？」→ 调用 get_weather
- 「现在几点了？」→ 调用 get_server_time
- 「帮我算一下 (2+3)*4」→ 调用 calculate
