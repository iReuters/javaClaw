package com.javaclaw.cli;

import picocli.CommandLine;

/**
 * Agent 命令的直接运行入口
 * 可以在 IDEA 中直接运行此文件来启动 Agent
 */
public class AgentMain {

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new AgentCommand());
        int exit = cmd.execute(args);
        System.exit(exit);
    }
}
