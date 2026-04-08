package com.javaclaw.providers;

import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * LLM 对话补全响应：content、toolCalls、finishReason、usage、reasoningContent。
 */
@Data
public class LLMResponse {

    private String content;
    private List<ToolCallRequest> toolCalls;
    private String finishReason = "stop";
    private Map<String, Integer> usage;
    private String reasoningContent;

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public List<ToolCallRequest> getToolCalls() {
        return toolCalls == null ? Collections.<ToolCallRequest>emptyList() : toolCalls;
    }

    public Map<String, Integer> getUsage() {
        return usage == null ? Collections.<String, Integer>emptyMap() : usage;
    }
}
