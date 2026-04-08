package com.javaclaw.agent;

import lombok.Value;

import java.util.Collections;
import java.util.List;

/**
 * runAgentLoop 的返回：最终回复文本与使用过的工具名列表。
 */
@Value
public class RunResult {

    String content;
    List<String> toolsUsed;

    public RunResult(String content, List<String> toolsUsed) {
        this.content = content != null ? content : "";
        this.toolsUsed = toolsUsed != null ? toolsUsed : Collections.<String>emptyList();
    }
}
