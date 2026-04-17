package com.javaclaw.controller;

import com.javaclaw.agent.SkillsLoader;
import com.javaclaw.agent.tools.DynamicToolLoader;
import com.javaclaw.agent.tools.ToolRegistry;
import com.javaclaw.entity.AuditLog;
import com.javaclaw.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AuditController {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private SkillsLoader skillsLoader;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private DynamicToolLoader dynamicToolLoader;

    @GetMapping("/audit/logs")
    public Map<String, Object> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<AuditLog> logs = auditLogService.getByPage(page, size);
        long total = auditLogService.countAll();
        Map<String, Object> result = new HashMap<>();
        result.put("logs", logs);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @GetMapping("/audit/logs/{id}")
    public AuditLog getLog(@PathVariable Long id) {
        return auditLogService.getById(id);
    }

    @GetMapping("/audit/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("byTool", auditLogService.getStatsByTool());
        stats.put("byDay", auditLogService.getStatsByDay());
        return stats;
    }

    @GetMapping("/skills")
    public List<Map<String, String>> getSkills() {
        return skillsLoader.listSkills(true);
    }

    @GetMapping("/tools")
    public Map<String, Object> getTools() {
        Map<String, Object> result = new HashMap<>();
        result.put("beanTools", toolRegistry.getToolNames());
        result.put("dynamicTools", new ArrayList<>(dynamicToolLoader.getAllDynamicToolNames()));
        return result;
    }
}
