package com.javaclaw.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javaclaw.entity.SkillRecord;
import com.javaclaw.mapper.SkillMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 技能加载器：从数据库加载技能定义。
 */
@Component
@Slf4j
public class SkillsLoader {

    private final SkillMapper skillMapper;

    private final Path workspace;
    private final Path builtinSkillsDir;

    private final ObjectMapper jsonMapper = new ObjectMapper();

    @Autowired
    public SkillsLoader(SkillMapper skillMapper) {
        this.skillMapper = skillMapper;
        this.workspace = Paths.get(".javaclawbot");
        this.builtinSkillsDir = null;
    }

    public SkillsLoader(SkillMapper skillMapper, Path workspace, Path builtinSkillsDir) {
        this.skillMapper = skillMapper;
        this.workspace = workspace;
        this.builtinSkillsDir = builtinSkillsDir;
    }

    /**
     * 解析tools JSON字段
     */
    private List<String> parseTools(String toolsJson) {
        if (toolsJson == null || toolsJson.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return jsonMapper.readValue(toolsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.error("Failed to parse tools JSON: {}", toolsJson, e);
            return new ArrayList<>();
        }
    }

    /**
     * 列出所有启用的技能
     */
    public List<Map<String, String>> listSkills(boolean filterUnavailable) {
        List<Map<String, String>> result = new ArrayList<>();

        List<SkillRecord> records = skillMapper.findAllEnabled();
        if (records == null) {
            log.warn("No skill records returned from database");
            return result;
        }
        for (SkillRecord record : records) {
            Map<String, String> skill = new HashMap<>();
            skill.put("name", record.getSkillId());
            skill.put("path", "database:" + record.getSkillId());
            skill.put("source", "database");
            result.add(skill);
        }

        return result;
    }

    /**
     * 根据名称加载技能内容
     */
    public Optional<String> loadSkill(String name) {
        if (name == null || name.isEmpty()) {
            return Optional.empty();
        }

        SkillRecord record = skillMapper.findById(name);
        if (record != null && record.getContent() != null) {
            return Optional.of(record.getContent());
        }

        return Optional.empty();
    }

    /**
     * 将多个技能内容拼接成一段文本
     */
    public String loadSkillsForContext(List<String> skillNames) {
        if (skillNames == null || skillNames.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String name : skillNames) {
            Optional<String> content = loadSkill(name);
            if (content.isPresent()) {
                sb.append("### ").append(name).append("\n\n").append(content.get()).append("\n\n");
            }
        }
        return sb.toString();
    }

    /**
     * 构建 skill 清单用于 prompt 注入
     * 格式：
     * 请根据用户意图选择最匹配的技能，若无匹配或意图不明显，使用 metadata：
     *
     * 1. metadata (始终可用)
     *    描述：基础助手能力，支持通用对话、问答、计算、翻译等
     *
     * 2. news-skill
     *    描述：获取最新新闻资讯...
     */
    public String buildSkillListForPrompt() {
        List<SkillRecord> records = skillMapper.findAllEnabled();
        if (records == null || records.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("请根据用户意图选择最匹配的技能，若无匹配或意图不明显，使用 metadata：\n\n");

        // metadata 固定在第一个位置
        sb.append("1. metadata (始终可用)\n");
        sb.append("   描述：基础助手能力，支持通用对话、问答、计算、翻译等\n\n");

        int idx = 2;
        for (SkillRecord record : records) {
            // 跳过 metadata，因为已手动添加
            if ("metadata".equals(record.getSkillId())) {
                continue;
            }
            sb.append(idx).append(". ").append(record.getSkillId()).append("\n");
            sb.append("   描述：").append(record.getDescription() != null ? record.getDescription() : "").append("\n\n");
            idx++;
        }

        return sb.toString();
    }

    /**
     * 生成技能摘要
     */
    public String buildSkillsSummary() {
        List<Map<String, String>> list = listSkills(false);
        if (list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Available skills: ");
        List<String> names = new ArrayList<>();
        for (Map<String, String> m : list) {
            names.add(m.get("name"));
        }
        sb.append(String.join(", ", names)).append("\n");
        return sb.toString();
    }

    /**
     * 返回需常驻加载的技能名列表
     */
    public List<String> getAlwaysSkills() {
        List<Map<String, String>> skills = listSkills(false);
        List<String> skillNames = new ArrayList<>();
        for (Map<String, String> skill : skills) {
            skillNames.add(skill.get("name"));
        }
        return skillNames;
    }

    /**
     * 返回技能元数据
     */
    public Optional<Map<String, Object>> getSkillMetadata(String name) {
        SkillRecord record = skillMapper.findById(name);
        if (record == null) {
            return Optional.empty();
        }

        Map<String, Object> meta = new HashMap<>();
        meta.put("name", record.getName());
        meta.put("description", record.getDescription());
        meta.put("enabled", record.isEnabled());
        meta.put("domain", record.getDomain());
        meta.put("maxIterations", record.getMaxIterations());

        // 解析tools字段
        List<String> tools = parseTools(record.getTools());
        meta.put("tools", tools);

        return Optional.of(meta);
    }

    /**
     * 获取指定skill声明的tools列表
     */
    public List<String> getSkillTools(String name) {
        Optional<Map<String, Object>> meta = getSkillMetadata(name);
        if (!meta.isPresent()) {
            return Collections.emptyList();
        }
        Object tools = meta.get().get("tools");
        if (tools instanceof List) {
            List<?> list = (List<?>) tools;
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        return Collections.emptyList();
    }
}
