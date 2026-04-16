package com.javaclaw.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javaclaw.agent.tools.DynamicTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ToolDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper jsonMapper = new ObjectMapper();

    public static class ToolRowMapper implements RowMapper<ToolRecord> {
        @Override
        public ToolRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            ToolRecord record = new ToolRecord();
            record.setToolKey(rs.getString("tool_key"));
            record.setToolJson(rs.getString("tool_json"));
            record.setEnabled(rs.getBoolean("enabled"));
            record.setTimeoutMs(rs.getInt("timeout_ms"));
            record.setMaxRetries(rs.getInt("max_retries"));
            record.setUserId(rs.getString("user_id"));
            return record;
        }
    }

    public static class ToolRecord {
        private String toolKey;
        private String toolJson;
        private boolean enabled;
        private int timeoutMs;
        private int maxRetries;
        private String userId;

        public String getToolKey() { return toolKey; }
        public void setToolKey(String toolKey) { this.toolKey = toolKey; }
        public String getToolJson() { return toolJson; }
        public void setToolJson(String toolJson) { this.toolJson = toolJson; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
    }

    /** 查询所有启用的工具 */
    public List<ToolRecord> findAllEnabled() {
        try {
            String sql = "SELECT tool_key, tool_json, enabled, timeout_ms, max_retries, user_id FROM agent_tools WHERE enabled = 1";
            return jdbcTemplate.query(sql, new ToolRowMapper());
        } catch (Exception e) {
            log.error("Failed to query enabled tools", e);
            return new java.util.ArrayList<>();
        }
    }

    /** 根据工具key查询 */
    public ToolRecord findByKey(String toolKey) {
        String sql = "SELECT tool_key, tool_json, enabled, timeout_ms, max_retries, user_id FROM agent_tools WHERE tool_key = ?";
        List<ToolRecord> results = jdbcTemplate.query(sql, new Object[]{toolKey}, new ToolRowMapper());
        return results.isEmpty() ? null : results.get(0);
    }

    /** 根据工具key列表查询 */
    public List<ToolRecord> findByKeys(List<String> toolKeys) {
        if (toolKeys == null || toolKeys.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        StringBuilder sql = new StringBuilder("SELECT tool_key, tool_json, enabled, timeout_ms, max_retries, user_id FROM agent_tools WHERE enabled = 1 AND tool_key IN (");
        for (int i = 0; i < toolKeys.size(); i++) {
            sql.append(i > 0 ? ",?" : "?");
        }
        sql.append(")");
        return jdbcTemplate.query(sql.toString(), toolKeys.toArray(), new ToolRowMapper());
    }

    /** 将JSON解析为DynamicTool对象 */
    public DynamicTool parseToolFromJson(String toolJson) {
        try {
            return jsonMapper.readValue(toolJson, DynamicTool.class);
        } catch (Exception e) {
            log.error("Failed to parse tool JSON: {}", toolJson, e);
            return null;
        }
    }

    /** 插入或更新工具 */
    public void saveOrUpdate(String toolKey, String toolJson, String userId) {
        String sql = "INSERT INTO agent_tools (tool_key, tool_json, enabled, timeout_ms, max_retries, user_id) " +
                     "VALUES (?, ?, 1, 30000, 3, ?) " +
                     "ON DUPLICATE KEY UPDATE tool_json = VALUES(tool_json), upd_time = NOW()";
        jdbcTemplate.update(sql, toolKey, toolJson, userId);
    }

    /** 删除工具 */
    public void delete(String toolKey) {
        String sql = "DELETE FROM agent_tools WHERE tool_key = ?";
        jdbcTemplate.update(sql, toolKey);
    }

    /** 启用/禁用工具 */
    public void setEnabled(String toolKey, boolean enabled) {
        String sql = "UPDATE agent_tools SET enabled = ? WHERE tool_key = ?";
        jdbcTemplate.update(sql, enabled ? 1 : 0, toolKey);
    }
}