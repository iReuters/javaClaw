package com.javaclaw.agent.tools;

import com.javaclaw.agent.SkillService;
import com.javaclaw.context.SkillContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Skill 工具：用于动态获取 skill 完整内容
 * 当 LLM 需要某个 skill 的详细信息时调用此工具
 */
@Component
@Slf4j
public class SkillTool implements Tool {

    @Autowired
    private SkillService skillService;

    @Override
    public String getName() {
        return "get_skill_content";
    }

    @Override
    public String getDescription() {
        return "获取指定 skill 的完整内容。当用户请求需要使用某个具体技能时，先调用此工具获取技能详情。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> skillId = new HashMap<>();
        skillId.put("type", "string");
        skillId.put("description", "技能ID，如 news-skill, tool-demo-skill, metadata 等");
        params.put("skill_id", skillId);

        Map<String, Object> properties = new HashMap<>();
        properties.put("skill_id", skillId);
        params.put("type", "object");
        params.put("properties", properties);
        params.put("required", new String[]{"skill_id"});
        return params;
    }

    @Override
    public String execute(Map<String, Object> params) {
        Object skillIdObj = params.get("skill_id");
        if (skillIdObj == null) {
            return "[Error: skill_id is required]";
        }

        String skillId = skillIdObj.toString();
        log.info("get_skill_content called with skill_id: {}", skillId);

        // 设置当前 skill 到 ThreadLocal，供 AOP 切面获取
        SkillContext.setSkill(skillId);

        // 检查 skill 是否存在
        if (!skillService.getAllSkillIds().contains(skillId)) {
            return "[Error: skill not found: " + skillId + ". Available skills: " + skillService.getAllSkillIds() + "]";
        }

        Optional<String> content = skillService.getSkillContent(skillId);
        if (content.isPresent()) {
            // 返回 skill 完整内容
            return content.get();
        }

        // metadata 返回默认内容
        if ("metadata".equals(skillId)) {
            return getMetadataContent();
        }

        return "[Error: skill content not found: " + skillId + "]";
    }

    /**
     * 获取 metadata skill 的默认内容
     */
    private String getMetadataContent() {
        return "# Metadata Skill\n\n" +
               "这是默认的基础助手技能。\n\n" +
               "## 能力\n" +
               "- 通用对话和问答\n" +
               "- 数学计算\n" +
               "- 文本处理和翻译\n" +
               "- 基础信息查询\n\n" +
               "## 可用工具\n" +
               "无特定工具，使用 LLM 本身的能力处理请求。";
    }
}