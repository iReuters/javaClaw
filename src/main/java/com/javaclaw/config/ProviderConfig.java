package com.javaclaw.config;

import lombok.Data;

import java.util.Collections;
import java.util.Map;

/**
 * 单个 LLM 提供商配置：apiKey、apiBase、可选 extraHeaders。
 * 对应 config.json 中 providers.<name> 节点。
 */
@Data
public class ProviderConfig {

    private String apiKey;
    private String apiBase;
    private Map<String, String> extraHeaders;

    public Map<String, String> getExtraHeaders() {
        return extraHeaders == null ? Collections.<String, String>emptyMap() : extraHeaders;
    }

    // 手动添加getter方法，因为Lombok可能没有正确生成
    public String getApiKey() {
        return apiKey;
    }

    public String getApiBase() {
        return apiBase;
    }
}
