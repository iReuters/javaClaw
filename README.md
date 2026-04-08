# javaClaw - 超轻量个人 AI 助手 (Java 版)

[![Java](https://img.shields.io/badge/Java-8+-blue.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.x-red.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

参照 [openClaw](https://github.com/openClaw) 原理使用 **Java 8** 重写的超轻量个人 AI 助手，目前支持 QQ 机器人渠道，可轻松扩展其他渠道。核心逻辑保持精简，仅约 4000 行代码，不依赖任何复杂框架，适合学习与二次开发。

## ✨ 特性

- **多渠道支持**：**已完整实现 QQ 机器人对接**（WebSocket 网关 + 单聊消息收发）；钉钉（DingTalk）为预留接口，可通过消息总线与 Agent 解耦，便于扩展新渠道
- **LLM 兼容性**：HTTP 调用任意 OpenAI 兼容 API（如 OpenAI、DeepSeek、Azure、智谱等），由 `LLMProvider` 抽象层统一管理
- **智能 Agent + 工具系统**：支持多轮对话、记忆管理、技能调用，内置丰富工具（消息发送、文件读写、Shell 执行、MCP 等），LLM 通过 tool_calls 与 MessageTool 间接与渠道通信
- **极致轻量**：基于 Java 8，使用 Picocli CLI + JSON 配置，所有数据与工作区统一存储在 `~/.javaclawbot` 目录
- **模块化设计**：清晰的模块划分，渠道与 Agent 通过消息总线完全解耦，易于扩展和维护

## 🚀 快速开始

### 环境要求

- **JDK 8+** (推荐 JDK 11+)
- **Maven 3.x**
- **网络连接** (用于调用 LLM API)

### 安装与运行

1. **克隆项目**
   ```bash
   git clone https://github.com/iReuters/javaClaw.git
   cd javaClaw
   ```

2. **编译项目**
   ```bash
   mvn compile
   ```

3. **打包可执行 JAR**
   ```bash
   mvn package
   ```

4. **初始化配置**
   ```bash
   java -jar target/javaclaw.jar onboard
   ```
   这将在 `~/.javaclawbot/` 目录下生成默认配置文件和工作区。

5. **配置 LLM 和渠道**
   编辑 `~/.javaclawbot/config.json`，设置：
   - `providers.openai.apiKey`: 你的 OpenAI 兼容 API 密钥
   - `channels.qq`: QQ 机器人配置（appId, token, secret）
   - 其他可选配置

6. **运行模式选择**

   **CLI 交互模式** (测试 Agent 功能):
   ```bash
   java -jar target/javaclaw.jar agent
   ```

   **单条消息模式**:
   ```bash
   java -jar target/javaclaw.jar agent -m "你好，介绍一下你自己"
   ```

   **网关模式** (启动 QQ 机器人服务):
   ```bash
   java -jar target/javaclaw.jar gateway
   ```

## 📋 命令参考

| 命令 | 说明 | 示例 |
|------|------|------|
| `gateway` | 启动 Gateway（渠道 + Agent 循环 + 消息分发） | `java -jar target/javaclaw.jar gateway` |
| `agent` | CLI 运行 Agent（支持交互和单条消息） | `java -jar target/javaclaw.jar agent -m "Hello"` |
| `onboard` | 初始化配置与工作区 | `java -jar target/javaclaw.jar onboard` |
| `status` | 查看配置与工作区状态 | `java -jar target/javaclaw.jar status` |
| `cron` | 定时任务管理（占位功能） | `java -jar target/javaclaw.jar cron` |

## 🏗️ 项目架构

### 核心架构图

```
用户/渠道 → Channel → MessageBus(inbound) → AgentLoop → LLM + Tools
                                                      ↓
用户/渠道 ← Channel ← MessageBus(outbound) ← 回复/OutboundMessage
```

### 模块设计

| 模块 | 职责 | 关键类 |
|------|------|--------|
| **`agent`** | 核心 Agent 逻辑 | `AgentLoop`, `ContextBuilder`, `MemoryStore`, `SkillsLoader` |
| **`agent.tools`** | 工具系统 | `MessageTool`, `FileTool`, `ShellTool`, `MCPTool` |
| **`bus`** | 消息总线 | `MessageBus`, `InboundMessage`, `OutboundMessage` |
| **`channels`** | 渠道实现 | `QQChannel` (已实现), `DingTalkChannel` (预留) |
| **`providers`** | LLM 提供商 | `LLMProvider`, `OpenAICompatibleProvider` |
| **`config`** | 配置管理 | `Config`, `ConfigLoader` |
| **`session`** | 会话管理 | `Session`, `SessionManager` |
| **`cli`** | 命令行接口 | `Main`, `RootCommand`, 各子命令 |

### 调用链详解

1. **消息接收**: QQChannel 接收用户消息 → 转换为 `InboundMessage` → 发布到消息总线
2. **Agent 处理**: AgentLoop 从总线消费消息 → 构建上下文 → 调用 LLM → 执行工具调用
3. **消息发送**: MessageTool 生成 `OutboundMessage` → 发布到消息总线 → ChannelManager 分发 → QQChannel 发送回复

**设计优势**: 渠道与 Agent 完全解耦，通过消息总线间接通信，易于扩展新渠道。

## 🔧 配置说明

### 配置文件位置
- **Linux/macOS**: `~/.javaclawbot/config.json`
- **Windows**: `%USERPROFILE%\.javaclawbot\config.json`

### 主要配置项

```json
{
  "agents": {
    "default": {
      "model": "gpt-4o-mini",
      "systemPrompt": "你是一个有帮助的AI助手..."
    }
  },
  "providers": {
    "openai": {
      "type": "openai-compatible",
      "apiKey": "your-api-key",
      "baseUrl": "https://api.openai.com/v1"
    }
  },
  "channels": {
    "qq": {
      "appId": "your-qq-app-id",
      "token": "your-qq-token",
      "secret": "your-qq-secret",
      "enabled": true
    }
  },
  "gateway": {
    "port": 8765
  }
}
```

详细配置说明请参考：[06-config.json 填写说明](docs/06-config.json%20填写说明.md)

## 📸 示例展示

### 网关模式 (QQ 机器人)
通过 QQ 机器人可以远程执行命令、查看文件、管理主机等。

| ![QQ 机器人示例1](img/img.png) | ![QQ 机器人示例2](img/img_1.png) | ![QQ 机器人示例3](img/img_2.png) |
| :---: | :---: | :---: |

### Agent 模式 (命令行交互)
![CLI 交互示例1](img/img_4.png)
![CLI 交互示例2](img/img_5.png)
![CLI 交互示例3](img/img_6.png)

### Debug 模式 (本地调试)
![Debug 模式示例1](img/img_7.png)
![Debug 模式示例2](img/img_8.png)

## 🤖 QQ 机器人配置指南

QQ 渠道配置简单，三步即可完成：

1. **登录 QQ 开放平台**: https://q.qq.com/#/apps
2. **创建机器人应用**: 按照指引创建机器人
   ![创建机器人](img/img_9.png)
3. **配置机器人**:
   - 选择"开发" → "沙箱配置"
   - 在消息列表中添加自己的 QQ 号
   - 扫码关注即可开始测试（无需发布到线上）
   ![机器人配置](img/img_10.png)
4. **获取配置信息**: 在"开发管理"中获取 appId、token、secret
   ![获取配置](img/img_11.png)
5. **配置到 config.json**: 将获取的信息填入配置文件中

## 📚 详细文档

| 文档 | 说明 |
|------|------|
| [01-项目梳理-Java版](docs/01-项目梳理-Java版.md) | 项目架构、目录结构、流程与设计特点 |
| [02-调用链-Java版](docs/02-调用链-Java版.md) | 程序入口、Gateway 启动、消息处理流程、记忆合并 |
| [03-接口文档-Java版](docs/03-接口文档-Java版.md) | 消息总线、渠道、LLM、工具、Agent、会话、技能等接口说明 |
| [05-最终技术方案-Java版](docs/05-最终技术方案-Java版.md) | 技术栈选择、路径约定、模块职责与扩展约定 |
| [06-config.json 填写说明](docs/06-config.json%20填写说明.md) | config.json 各节点详解与常见场景配置 |

## 🛠️ 技术栈

| 类别 | 选型 | 说明 |
|------|------|------|
| **语言** | Java 8 | 保持兼容性，不使用虚拟线程 |
| **CLI 框架** | Picocli | 轻量级命令行接口框架 |
| **配置管理** | JSON + Jackson | 配置持久化与序列化 |
| **HTTP 客户端** | OkHttp 4.x | 高效的 HTTP 客户端 |
| **LLM 接口** | OpenAI 兼容 API | 支持多种 LLM 服务提供商 |
| **消息队列** | BlockingQueue | 简单的内存消息队列 |

## 📁 项目结构

```
javaClaw/
├── src/main/java/com/javaclaw/
│   ├── agent/              # 核心 Agent 逻辑
│   ├── bus/                # 消息总线
│   ├── channels/           # 渠道实现 (QQ/钉钉)
│   ├── cli/                # 命令行接口
│   ├── config/             # 配置管理
│   ├── providers/          # LLM 提供商
│   ├── session/            # 会话管理
│   ├── cron/               # 定时任务
│   └── heartbeat/          # 心跳服务
├── docs/                   # 项目文档
├── img/                    # 示例图片
├── web/                    # Web 界面
├── pom.xml                 # Maven 配置
└── README.md               # 项目说明
```

## 🔗 依赖项

项目仅依赖以下轻量级库：

```xml
<dependencies>
    <!-- JSON 处理：Jackson -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.15.3</version>
    </dependency>
    
    <!-- Java 8 日期时间支持 -->
    <dependency>
        <groupId>com.fasterxml.jackson.datatype</groupId>
        <artifactId>jackson-datatype-jsr310</artifactId>
        <version>2.15.3</version>
    </dependency>
    
    <!-- CLI 框架：Picocli -->
    <dependency>
        <groupId>info.picocli</groupId>
        <artifactId>picocli</artifactId>
        <version>4.7.5</version>
    </dependency>
    
    <!-- HTTP 客户端：OkHttp -->
    <dependency>
        <groupId>com.squareup.okhttp3</groupId>
        <artifactId>okhttp</artifactId>
        <version>4.12.0</version>
    </dependency>
</dependencies>
```

## 🌐 Web 界面

项目包含一个简单的 Web 聊天界面，位于 `web/index.html`，可通过以下方式访问：

1. 启动 Gateway 服务：`java -jar target/javaclaw.jar gateway`
2. 打开浏览器访问：`http://localhost:8765/web/`

## 🤝 贡献指南

欢迎贡献代码！请遵循以下步骤：

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 👤 作者

**iReuters** - [GitHub](https://github.com/iReuters)

![关注作者](img/qrCode.jpg)

## 🙏 致谢

- 感谢 [openClaw](https://github.com/openClaw) 项目的启发
- 感谢所有贡献者和用户的支持

---

**提示**: 如果在使用过程中遇到问题，请先查看 [文档](docs/) 或提交 Issue。项目正在积极开发中，欢迎反馈和建议！