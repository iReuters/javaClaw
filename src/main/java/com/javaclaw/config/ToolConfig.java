package com.javaclaw.config;

import com.javaclaw.agent.tools.ExecTool;
import com.javaclaw.agent.tools.ListDirTool;
import com.javaclaw.agent.tools.ReadFileTool;
import com.javaclaw.agent.tools.WeatherTool;
import com.javaclaw.agent.tools.WriteFileTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

@Configuration
public class ToolConfig {

    @Autowired
    private Config config;

    @Bean
    public ReadFileTool readFileTool() {
        String workspacePath = config.getAgents().getWorkspace();
        if (workspacePath == null || workspacePath.isEmpty()) {
            workspacePath = ".javaclawbot/workspace";
        }
        return new ReadFileTool(Paths.get(workspacePath), config.getTools().isRestrictToWorkspace());
    }

    @Bean
    public WriteFileTool writeFileTool() {
        String workspacePath = config.getAgents().getWorkspace();
        if (workspacePath == null || workspacePath.isEmpty()) {
            workspacePath = ".javaclawbot/workspace";
        }
        return new WriteFileTool(Paths.get(workspacePath), config.getTools().isRestrictToWorkspace());
    }

    @Bean
    public ListDirTool listDirTool() {
        String workspacePath = config.getAgents().getWorkspace();
        if (workspacePath == null || workspacePath.isEmpty()) {
            workspacePath = ".javaclawbot/workspace";
        }
        return new ListDirTool(Paths.get(workspacePath), config.getTools().isRestrictToWorkspace());
    }

    @Bean
    public ExecTool execTool() {
        return new ExecTool(config.getTools().getExec());
    }

    @Bean
    public WeatherTool weatherTool() {
        return new WeatherTool();
    }
}
