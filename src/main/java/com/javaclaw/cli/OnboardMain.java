package com.javaclaw.cli;

import picocli.CommandLine;

/**
 * Onboard 命令的直接运行入口
 * 可以在 IDEA 中直接运行此文件来执行 Onboard 命令
 */
public class OnboardMain {

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new OnboardCommand());
        int exit = cmd.execute(args);
        System.exit(exit);
    }
}
