package com.javaclaw.agent;

import com.javaclaw.entity.SkillRecord;
import com.javaclaw.mapper.SkillMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skill 服务：启动时加载所有 skill 到缓存
 */
@Service
@Slf4j
public class SkillService {

    @Autowired
    private SkillMapper skillMapper;

    /** skillId -> SkillRecord 缓存 */
    private final Map<String, SkillRecord> skillCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadAllSkills();
    }

    /**
     * 重新加载所有 skill 到缓存
     */
    public void loadAllSkills() {
        List<SkillRecord> records = skillMapper.findAllEnabled();
        skillCache.clear();
        if (records != null) {
            for (SkillRecord record : records) {
                skillCache.put(record.getSkillId(), record);
            }
        }
        log.info("Loaded {} skills into cache", skillCache.size());
    }

    /**
     * 获取所有 skill 的清单（用于 prompt 注入）
     */
    public String buildSkillListForPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据用户意图选择最匹配的技能，若无匹配或意图不明显，使用 metadata：\n\n");

        // metadata 固定在第一个位置
        sb.append("1. metadata (始终可用)\n");
        sb.append("   描述：基础助手能力，支持通用对话、问答、计算、翻译等\n\n");

        int idx = 2;
        for (SkillRecord record : skillCache.values()) {
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
     * 根据 skillId 获取完整 content
     */
    public Optional<String> getSkillContent(String skillId) {
        SkillRecord record = skillCache.get(skillId);
        if (record != null && record.getContent() != null) {
            return Optional.of(record.getContent());
        }
        return Optional.empty();
    }

    /**
     * 根据 skillId 获取 SkillRecord
     */
    public Optional<SkillRecord> getSkill(String skillId) {
        return Optional.ofNullable(skillCache.get(skillId));
    }

    /**
     * 获取所有 skillId
     */
    public Set<String> getAllSkillIds() {
        return new HashSet<>(skillCache.keySet());
    }
}