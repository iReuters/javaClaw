package com.javaclaw.agent;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * 智能体执行步骤
 *
 * 代表大模型单次响应为一个完整步骤，包含：
 * - 思考内容（reasoning）
 * - 回复内容（content）
 * - 工具调用列表（toolCalls）
 * - 工具执行结果（toolResults）
 */
@Data
public class AgentStep {

    /**
     * 步骤序号（从1开始）
     */
    private int stepNumber;

    /**
     * 步骤类型
     * - think: 仅思考/推理
     * - tool_call: 有工具调用
     * - final: 最终回复
     * - max_iterations: 达到最大迭代次数
     */
    private String type;

    /**
     * 大模型的思考内容
     */
    private String reasoning;

    /**
     * 大模型的回复内容
     */
    private String content;

    /**
     * 工具调用列表
     */
    private List<ToolCallInfo> toolCalls = new ArrayList<>();

    /**
     * 工具执行结果列表
     */
    private List<ToolCallResult> toolResults = new ArrayList<>();

    public AgentStep(int stepNumber, String type) {
        this.stepNumber = stepNumber;
        this.type = type;
    }

    public void addToolCall(String id, String name, String arguments) {
        toolCalls.add(new ToolCallInfo(id, name, arguments));
    }

    public void addToolResult(String toolName, String result) {
        toolResults.add(new ToolCallResult(toolName, result));
    }

    /**
     * 工具调用信息
     */
    @Data
    public static class ToolCallInfo {
        private String id;
        private String name;
        private String arguments;

        public ToolCallInfo(String id, String name, String arguments) {
            this.id = id;
            this.name = name;
            this.arguments = arguments;
        }
    }

    /**
     * 工具执行结果
     */
    @Data
    public static class ToolCallResult {
        private String toolName;
        private String result;

        public ToolCallResult(String toolName, String result) {
            this.toolName = toolName;
            this.result = result;
        }
    }
}
