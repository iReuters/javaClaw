package com.javaclaw.agent;

import lombok.Value;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * runAgentLoop 的返回结果
 */
@Value
public class RunResult {

    /** 最终回复文本 */
    String content;

    /** 使用过的工具名列表 */
    List<String> toolsUsed;

    /** 所有执行步骤 */
    List<AgentStep> steps;

    public RunResult(String content, List<String> toolsUsed, List<AgentStep> steps) {
        this.content = content != null ? content : "";
        this.toolsUsed = toolsUsed != null ? toolsUsed : Collections.emptyList();
        this.steps = steps != null ? steps : new ArrayList<>();
    }

    /** 兼容旧代码 */
    public RunResult(String content, List<String> toolsUsed) {
        this(content, toolsUsed, new ArrayList<>());
    }
}
