package com.javaclaw.dao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class SkillDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper jsonMapper = new ObjectMapper();

    public static class SkillRecord {
        private String skillId;
        private String name;
        private String description;
        private String content;
        private String tools;
        private boolean enabled;
        private String domain;
        private String examples;
        private int maxIterations;
        private String userId;

        public String getSkillId() { return skillId; }
        public void setSkillId(String skillId) { this.skillId = skillId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getTools() { return tools; }
        public void setTools(String tools) { this.tools = tools; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getDomain() { return domain; }
        public void setDomain(String domain) { this.domain = domain; }
        public String getExamples() { return examples; }
        public void setExamples(String examples) { this.examples = examples; }
        public int getMaxIterations() { return maxIterations; }
        public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
    }

    public static class SkillRowMapper implements RowMapper<SkillRecord> {
        @Override
        public SkillRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            SkillRecord record = new SkillRecord();
            record.setSkillId(rs.getString("skill_id"));
            record.setName(rs.getString("name"));
            record.setDescription(rs.getString("description"));
            record.setContent(rs.getString("content"));
            record.setTools(rs.getString("tools"));
            record.setEnabled(rs.getBoolean("enabled"));
            record.setDomain(rs.getString("domain"));
            record.setExamples(rs.getString("examples"));
            record.setMaxIterations(rs.getInt("max_iterations"));
            record.setUserId(rs.getString("user_id"));
            return record;
        }
    }

    /** 查询所有启用的技能 */
    public List<SkillRecord> findAllEnabled() {
        try {
            String sql = "SELECT skill_id, name, description, content, tools, enabled, domain, examples, max_iterations, user_id FROM agent_skills WHERE enabled = 1";
            return jdbcTemplate.query(sql, new SkillRowMapper());
        } catch (Exception e) {
            log.error("Failed to query enabled skills", e);
            return new ArrayList<>();
        }
    }

    /** 根据skillId查询 */
    public SkillRecord findById(String skillId) {
        String sql = "SELECT skill_id, name, description, content, tools, enabled, domain, examples, max_iterations, user_id FROM agent_skills WHERE skill_id = ?";
        List<SkillRecord> results = jdbcTemplate.query(sql, new Object[]{skillId}, new SkillRowMapper());
        return results.isEmpty() ? null : results.get(0);
    }

    /** 根据skillId列表查询 */
    public List<SkillRecord> findByIds(List<String> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return new ArrayList<>();
        }
        StringBuilder sql = new StringBuilder("SELECT skill_id, name, description, content, tools, enabled, domain, examples, max_iterations, user_id FROM agent_skills WHERE enabled = 1 AND skill_id IN (");
        for (int i = 0; i < skillIds.size(); i++) {
            sql.append(i > 0 ? ",?" : "?");
        }
        sql.append(")");
        return jdbcTemplate.query(sql.toString(), skillIds.toArray(), new SkillRowMapper());
    }

    /** 解析tools字段为List<String> */
    public List<String> parseTools(String toolsJson) {
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

    /** 插入或更新技能 */
    public void saveOrUpdate(SkillRecord record) {
        String sql = "INSERT INTO agent_skills (skill_id, name, description, content, tools, enabled, domain, examples, max_iterations, user_id) " +
                     "VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE name=VALUES(name), description=VALUES(description), content=VALUES(content), tools=VALUES(tools), upd_time=NOW()";
        jdbcTemplate.update(sql,
            record.getSkillId(), record.getName(), record.getDescription(), record.getContent(),
            record.getTools(), record.getDomain(), record.getExamples(),
            record.getMaxIterations() > 0 ? record.getMaxIterations() : 5,
            record.getUserId() != null ? record.getUserId() : "system");
    }

    /** 删除技能 */
    public void delete(String skillId) {
        String sql = "DELETE FROM agent_skills WHERE skill_id = ?";
        jdbcTemplate.update(sql, skillId);
    }

    /** 启用/禁用技能 */
    public void setEnabled(String skillId, boolean enabled) {
        String sql = "UPDATE agent_skills SET enabled = ? WHERE skill_id = ?";
        jdbcTemplate.update(sql, enabled ? 1 : 0, skillId);
    }
}