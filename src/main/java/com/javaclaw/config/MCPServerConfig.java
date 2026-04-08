package com.javaclaw.config;

import lombok.Data;

/**
 * 单个 MCP 服务配置。对应 config.json 中 tools.mcpServers.&lt;name&gt;。
 */
@Data
public class MCPServerConfig {

    private String command;
    private String[] args;
    private String url;
}
