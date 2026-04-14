package com.javaclaw.config;

import com.javaclaw.agent.AgentLoop;
import com.javaclaw.agent.tools.ToolRegistry;
import com.javaclaw.agent.tools.DynamicToolLoader;
import com.javaclaw.providers.LLMProvider;
import com.javaclaw.session.SessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

@Configuration
public class AppConfig {

    @Autowired
    private Config config;

    @Autowired
    private LLMProvider llmProvider;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired(required = false)
    private DynamicToolLoader dynamicToolLoader;

    @Bean
    public AgentLoop agentLoop() {
        // 初始化工作区
        String workspacePath = config.getAgents().getWorkspace();
        if (workspacePath == null || workspacePath.isEmpty()) {
            workspacePath = ".javaclawbot/workspace";
        }

        return new AgentLoop(
                llmProvider,
                Paths.get(workspacePath),
                config.getAgents().getModel(),
                config.getAgents().getMaxToolIterations(),
                config.getAgents().getTemperature(),
                config.getAgents().getMaxTokens(),
                config.getAgents().getMemoryWindow(),
                config.getTools().getWebSearchApiKey(),
                config.getTools().getExec(),
                null, // cronService - 可以后续注入
                config.getTools().isRestrictToWorkspace(),
                sessionManager,
                config.getTools().getMcpServers(),
                toolRegistry,
                dynamicToolLoader
        );
    }
}
