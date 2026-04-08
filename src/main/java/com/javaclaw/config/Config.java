package com.javaclaw.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * 根配置 POJO，与 application.yml 结构一致（camelCase）。
 * 提供 getWorkspacePath、getDataDir、getProvider、getApiKey、getApiBase 等便捷方法。
 * providers 为 provider 名到配置的 map，对应 YAML： "providers": { "openai": { "apiKey": "...", "apiBase": "..." } }
 */
@Data
@ConfigurationProperties(prefix = "javaclaw")
public class Config {

    private AgentsConfig agents;
    /** provider 名称 -> 配置；对应 JSON 单层结构 "providers": { "openai": {...} } */
    private Map<String, ProviderConfig> providers = new java.util.HashMap<>();
    private GatewayConfig gateway;
    private ToolsConfig tools;

    /** 展开后的工作区路径 */
    private Path resolvedWorkspacePath;

    /** 数据目录（~/.javaclawbot） */
    private Path resolvedDataDir;

    public AgentsConfig getAgents() {
        return agents == null ? new AgentsConfig() : agents;
    }

    /** 返回 provider 名到配置的 map，对应 config.json 中 "providers": { "openai": {...}, ... } */
    public Map<String, ProviderConfig> getProviders() {
        return providers == null ? Collections.<String, ProviderConfig>emptyMap() : providers;
    }

    public GatewayConfig getGateway() {
        return gateway == null ? new GatewayConfig() : gateway;
    }

    public ToolsConfig getTools() {
        return tools == null ? new ToolsConfig() : tools;
    }

    /**
     * 根据模型名解析出使用的 provider 配置。若配置了 defaultProvider 则用该名；否则取第一个。
     */
    public Optional<ProviderConfig> getProvider(String model) {
        Map<String, ProviderConfig> map = getProviders();
        if (map.isEmpty()) {
            return Optional.empty();
        }
        String name = getAgents().getDefaultProvider();
        if (name != null && !name.isEmpty() && map.containsKey(name)) {
            return Optional.of(map.get(name));
        }
        return Optional.of(map.values().iterator().next());
    }

    /** 返回当前模型使用的 provider 名称 */
    public Optional<String> getProviderName(String model) {
        return getProvider(model).map(p -> {
            for (Map.Entry<String, ProviderConfig> e : getProviders().entrySet()) {
                if (e.getValue() == p) {
                    return e.getKey();
                }
            }
            return "default";
        });
    }

    public Optional<String> getApiKey(String model) {
        return getProvider(model).map(ProviderConfig::getApiKey).filter(s -> s != null && !s.isEmpty());
    }

    public Optional<String> getApiBase(String model) {
        return getProvider(model).map(ProviderConfig::getApiBase).filter(s -> s != null && !s.isEmpty());
    }
}
