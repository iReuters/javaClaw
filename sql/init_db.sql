-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS javaclaw DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE javaclaw;

-- 智能体工具定义表
CREATE TABLE IF NOT EXISTS agent_tools (
  `tool_key` varchar(100) NOT NULL COMMENT '工具标识键（主键）',
  `tool_json` json NOT NULL COMMENT '完整的工具定义（JSON格式）',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用：0-禁用，1-启用',
  `timeout_ms` int DEFAULT 30000 COMMENT '调用超时时间（毫秒）',
  `max_retries` int DEFAULT '3' COMMENT '最大重试次数',
  `user_id` varchar(100) DEFAULT NULL COMMENT '创建人',
  `cre_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `upd_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`tool_key`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_enabled` (`enabled`),
  KEY `idx_cre_time` (`cre_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='智能体工具定义表';

-- 智能体技能表
CREATE TABLE IF NOT EXISTS agent_skills (
  `skill_id` varchar(100) NOT NULL COMMENT '技能唯一标识（主键）',
  `name` varchar(200) NOT NULL COMMENT '技能名称',
  `description` text COMMENT '技能描述',
  `content` text COMMENT '技能内容（可以是提示词、模板、配置等）',
  `tools` json DEFAULT NULL COMMENT '关联的工具列表（JSON数组）',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用：0-禁用，1-启用',
  `domain` varchar(100) DEFAULT NULL COMMENT '技能领域/分类',
  `examples` json DEFAULT NULL COMMENT '使用示例',
  `max_iterations` int DEFAULT 5 COMMENT '最大迭代次数（用于复杂技能）',
  `user_id` varchar(100) DEFAULT NULL COMMENT '创建人',
  `cre_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `upd_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`skill_id`),
  KEY `idx_domain_enabled` (`domain`, `enabled`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_enabled` (`enabled`),
  KEY `idx_cre_time` (`cre_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='智能体技能表';

-- ==========================================
-- 初始化工具数据
-- ==========================================

-- get_weather - Bean调用类型
INSERT INTO agent_tools (tool_key, tool_json, enabled, timeout_ms, max_retries, user_id)
VALUES (
  'get_weather',
  '{"name":"get_weather","description":"查询城市天气信息","type":"bean","beanName":"weatherService","method":"getWeather","parameters":{"type":"object","properties":{"city":{"type":"string","description":"城市名称，例如：北京、上海、广州"}},"required":["city"]}}',
  1, 30000, 3, 'system'
) ON DUPLICATE KEY UPDATE tool_json=VALUES(tool_json), upd_time=NOW();

-- get_exchange_rate - 静态模板类型
INSERT INTO agent_tools (tool_key, tool_json, enabled, timeout_ms, max_retries, user_id)
VALUES (
  'get_exchange_rate',
  '{"name":"get_exchange_rate","description":"查询货币汇率","parameters":{"type":"object","properties":{"from_currency":{"type":"string","description":"源货币代码，例如：USD"},"to_currency":{"type":"string","description":"目标货币代码，例如：CNY"}},"required":["from_currency","to_currency"]},"response":"1 %from_currency% = %result% %to_currency%"}',
  1, 30000, 3, 'system'
) ON DUPLICATE KEY UPDATE tool_json=VALUES(tool_json), upd_time=NOW();

-- get_server_time - 无参数工具
INSERT INTO agent_tools (tool_key, tool_json, enabled, timeout_ms, max_retries, user_id)
VALUES (
  'get_server_time',
  '{"name":"get_server_time","description":"获取当前服务器时间","parameters":{"type":"object","properties":{}},"response":"当前服务器时间：%datetime%"}',
  1, 30000, 3, 'system'
) ON DUPLICATE KEY UPDATE tool_json=VALUES(tool_json), upd_time=NOW();

-- calculate - 带参数工具
INSERT INTO agent_tools (tool_key, tool_json, enabled, timeout_ms, max_retries, user_id)
VALUES (
  'calculate',
  '{"name":"calculate","description":"执行简单数学计算","parameters":{"type":"object","properties":{"expression":{"type":"string","description":"数学表达式，例如：2+3*4"}},"required":["expression"]},"response":"计算结果：%expression% = %result%"}',
  1, 30000, 3, 'system'
) ON DUPLICATE KEY UPDATE tool_json=VALUES(tool_json), upd_time=NOW();

-- get_stock_price - Bean调用类型
INSERT INTO agent_tools (tool_key, tool_json, enabled, timeout_ms, max_retries, user_id)
VALUES (
  'get_stock_price',
  '{"name":"get_stock_price","description":"查询股票价格","type":"bean","beanName":"stockService","method":"getStockPrice","parameters":{"type":"object","properties":{"symbol":{"type":"string","description":"股票代码，例如：AAPL、GOOGL、MSFT"}},"required":["symbol"]}}',
  1, 30000, 3, 'system'
) ON DUPLICATE KEY UPDATE tool_json=VALUES(tool_json), upd_time=NOW();

-- get_latest_news - 无参数工具
INSERT INTO agent_tools (tool_key, tool_json, enabled, timeout_ms, max_retries, user_id)
VALUES (
  'get_latest_news',
  '{"name":"get_latest_news","description":"获取最新新闻资讯","parameters":{"type":"object","properties":{}},"response":"【今日要闻】\n1. 人工智能持续引领科技浪潮，多家巨头发布新模型\n2. 全球经济复苏预期增强，市场信心提振\n3. 新能源汽车销量突破新高，产业链景气度上升\n4. 科技创新政策密集出台，数字经济迎来新机遇"}',
  1, 30000, 3, 'system'
) ON DUPLICATE KEY UPDATE tool_json=VALUES(tool_json), upd_time=NOW();

-- search_news - 带参数工具
INSERT INTO agent_tools (tool_key, tool_json, enabled, timeout_ms, max_retries, user_id)
VALUES (
  'search_news',
  '{"name":"search_news","description":"根据关键词搜索新闻","parameters":{"type":"object","properties":{"keyword":{"type":"string","description":"搜索关键词"}},"required":["keyword"]},"response":"【%keyword% 相关新闻】\n1. %keyword% 技术取得突破性进展，行业应用加速落地\n2. %keyword% 领域投资热潮持续，资本加速布局\n3. %keyword% 成为年度最受关注话题之一"}',
  1, 30000, 3, 'system'
) ON DUPLICATE KEY UPDATE tool_json=VALUES(tool_json), upd_time=NOW();

-- ==========================================
-- 初始化技能数据
-- ==========================================

-- tool-demo-skill
INSERT INTO agent_skills (skill_id, name, description, content, tools, enabled, domain, max_iterations, user_id)
VALUES (
  'tool-demo-skill',
  '工具演示技能',
  '演示 skill 动态调用工具的能力。支持调用已注册的 Spring Bean 工具和动态工具。当用户说「测试工具」「演示工具调用」时使用。',
  '# 工具演示 Skill\n\n这个 skill 演示如何让 AI 通过 skill 调用各种工具。\n\n## 可用工具\n\n| 工具名 | 类型 | 说明 |\n|--------|------|------|\n| get_weather | Spring Bean | 查询城市天气 |\n| get_server_time | 动态工具 | 获取服务器当前时间 |\n| calculate | 动态工具 | 执行数学计算 |\n\n## 使用示例\n\n- 「北京今天天气怎么样？」 → 调用 get_weather\n- 「现在几点了？」 → 调用 get_server_time\n- 「帮我算一下 (2+3)*4」 → 调用 calculate',
  '["get_weather", "get_server_time", "calculate"]',
  1, 'demo', 5, 'system'
) ON DUPLICATE KEY UPDATE content=VALUES(content), tools=VALUES(tools), upd_time=NOW();

-- news-skill
INSERT INTO agent_skills (skill_id, name, description, content, tools, enabled, domain, max_iterations, user_id)
VALUES (
  'news-skill',
  '新闻资讯技能',
  '新闻资讯助手技能。用于查询最新新闻资讯、热点事件，行业动态等。当用户说「最新新闻」「今天有什么新闻」时使用。',
  '# 新闻资讯 Skill\n\n基于公开模拟数据的新闻查询助手。\n\n## 可用工具\n\n| 工具名 | 说明 |\n|--------|------|\n| get_latest_news | 获取最新新闻（无需参数） |\n| search_news | 搜索新闻（需传入搜索关键词） |\n\n## 使用示例\n\n- 「今天有什么新闻？」 → 调用 get_latest_news\n- 「搜索AI相关的新闻」 → 调用 search_news，关键词为「AI」',
  '["get_latest_news", "search_news"]',
  1, 'info', 5, 'system'
) ON DUPLICATE KEY UPDATE content=VALUES(content), tools=VALUES(tools), upd_time=NOW();
