package com.javaclaw.providers;

import lombok.Data;

import java.util.Collections;
import java.util.Map;

/**
 * LLM 返回的单条工具调用请求。id、name、arguments（JSON 解析后的 Map）。
 */
@Data
public class ToolCallRequest {

    private String id;
    private String name;
    private Map<String, Object> arguments;

    public Map<String, Object> getArguments() {
        return arguments == null ? Collections.<String, Object>emptyMap() : arguments;
    }
}
