package com.javaclaw.cli;

import picocli.CommandLine;

/**
 * Status 命令的直接运行入口
 * 可以在 IDEA 中直接运行此文件来执行 Status 命令
 */
public class StatusMain {

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new StatusCommand());
        int exit = cmd.execute(args);
        System.exit(exit);
    }
}
