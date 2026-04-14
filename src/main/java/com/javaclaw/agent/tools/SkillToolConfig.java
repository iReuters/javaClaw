package com.javaclaw.agent.tools;

import java.util.List;
import java.util.Map;

/**
 * Skill工具配置
 */
public class SkillToolConfig {
    private Map<String, List<String>> skills;
    private List<String> defaultTools;

    public Map<String, List<String>> getSkills() {
        return skills;
    }

    public void setSkills(Map<String, List<String>> skills) {
        this.skills = skills;
    }

    public List<String> getDefaultTools() {
        return defaultTools;
    }

    public void setDefaultTools(List<String> defaultTools) {
        this.defaultTools = defaultTools;
    }
}