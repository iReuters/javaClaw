package com.javaclaw.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册表：管理 Spring Bean 工具（已注册 Tool 实现）。
 * 动态工具由 DynamicToolLoader 独立管理，不在此注册。
 */
@Component
@Slf4j
public class ToolRegistry {

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();
    private final List<Tool> toolBeans;

    @Autowired
    public ToolRegistry(List<Tool> toolBeans) {
        this.toolBeans = toolBeans;
    }

    @PostConstruct
    public void initialize() {
        for (Tool tool : toolBeans) {
            register(tool);
        }
        log.info("Tools initialized (beans): {}, names={}", toolBeans.size(), getToolNames());
    }

    public void register(Tool tool) {
        if (tool != null) {
            tools.put(tool.getName(), tool);
        }
    }

    public void unregister(String name) {
        tools.remove(name);
    }

    public Optional<Tool> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public boolean has(String name) {
        return tools.containsKey(name);
    }

    /** 所有 Bean 工具的 OpenAI function 定义 */
    public List<Map<String, Object>> getDefinitions() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Tool t : tools.values()) {
            if (t instanceof BaseTool) {
                out.add(((BaseTool) t).toSchema());
            } else {
                Map<String, Object> fn = new HashMap<>();
                fn.put("type", "function");
                Map<String, Object> f = new HashMap<>();
                f.put("name", t.getName());
                f.put("description", t.getDescription());
                f.put("parameters", t.getParameters());
                fn.put("function", f);
                out.add(fn);
            }
        }
        return out;
    }

    /** 执行 Bean 工具 */
    public String execute(String name, Map<String, Object> params) {
        Tool tool = tools.get(name);
        if (tool == null) {
            return "[Error: tool not found: " + name + "]";
        }
        if (tool instanceof BaseTool) {
            List<String> errs = ((BaseTool) tool).validateParams(params);
            if (errs != null && !errs.isEmpty()) {
                return "[Error: invalid params: " + String.join("; ", errs) + "]";
            }
        }
        try {
            return tool.execute(params != null ? params : Collections.<String, Object>emptyMap());
        } catch (Exception e) {
            return "[Error: " + e.getMessage() + "]";
        }
    }

    public List<String> getToolNames() {
        return new ArrayList<>(tools.keySet());
    }
}