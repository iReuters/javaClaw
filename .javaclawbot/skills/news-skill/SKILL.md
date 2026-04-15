---
name: news-skill
description: |
  新闻资讯助手技能。用于查询最新新闻资讯、热点事件、行业动态等。
  当用户说「最新新闻」「今天有什么新闻」「行业动态」「热点事件」时使用。
tools: [get_latest_news, search_news]
---

# 新闻资讯 Skill

基于公开模拟数据的新闻查询助手。

## 可用工具

| 工具名 | 说明 |
|--------|------|
| get_latest_news | 获取最新新闻（无需参数） |
| search_news | 搜索新闻（需传入搜索关键词） |

## 使用示例

- 「今天有什么新闻？」→ 调用 get_latest_news
- 「搜索AI相关的新闻」→ 调用 search_news，关键词为「AI」
