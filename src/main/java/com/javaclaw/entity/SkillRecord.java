package com.javaclaw.entity;

import lombok.Data;

import java.sql.Timestamp;

/**
 * 技能记录实体类
 * 对应数据库表：agent_skills（智能体技能表）
 *
 * <p>该表存储智能体的技能定义，技能是AI使用工具的能力模版。
 * 每个技能包含：
 * <ul>
 *   <li>技能名称和描述</li>
 *   <li>技能内容（提示词、模板、配置等）</li>
 *   <li>关联的工具列表</li>
 *   <li>技能领域/分类</li>
 *   <li>使用示例</li>
 * </ul>
 *
 * <p>技能通过system prompt传递给LLM，使其知道在什么场景下使用哪些工具
 */
@Data
public class SkillRecord {

    /**
     * 技能唯一标识（主键）
     * <p>如：tool-demo-skill、news-skill</p>
     */
    private String skillId;

    /**
     * 技能名称
     * <p>用于展示给用户的技能显示名称</p>
     */
    private String name;

    /**
     * 技能描述
     * <p>描述技能的功能和使用场景，供AI判断何时调用该技能</p>
     */
    private String description;

    /**
     * 技能内容
     * <p>可以是提示词、模板、配置等，支持Markdown格式。
     * 包含技能的使用说明、可用的工具列表、使用示例等信息。
     * 示例：
     * <pre>
     * # 工具演示 Skill
     *
     * 这个 skill 演示如何让 AI 通过 skill 调用各种工具。
     *
     * ## 可用工具
     * | 工具名 | 类型 | 说明 |
     * |--------|------|------|
     * | get_weather | Spring Bean | 查询城市天气 |
     *
     * ## 使用示例
     * - "北京今天天气怎么样？" → 调用 get_weather
     * </pre>
     */
    private String content;

    /**
     * 关联的工具列表（JSON数组格式）
     * <p>记录该技能可以使用哪些工具。
     * JSON数组示例：["get_weather", "get_server_time", "calculate"]</p>
     */
    private String tools;

    /**
     * 是否启用
     * <p>0-禁用，1-启用。只有启用的技能才会被加载和使用</p>
     */
    private boolean enabled;

    /**
     * 技能领域/分类
     * <p>用于对技能进行分类管理。
     * 如：demo（演示）、info（资讯）、finance（金融）等</p>
     */
    private String domain;

    /**
     * 使用示例（JSON数组格式）
     * <p>记录该技能的典型使用场景和示例。
     * JSON数组示例：
     * <pre>
     * [
     *   {"input": "北京天气怎么样？", "output": "调用get_weather"},
     *   {"input": "帮我算一下 (2+3)*4", "output": "调用calculate"}
     * ]
     * </pre>
     */
    private String examples;

    /**
     * 最大迭代次数
     * <p>用于复杂技能的多轮对话控制。
     * 表示该技能在一次请求中最多允许的工具调用次数，默认5次。
     * 达到此限制后即使还有工具调用需求也会终止执行</p>
     */
    private int maxIterations;

    /**
     * 创建人
     * <p>记录创建该技能的用户ID，system表示系统内置技能</p>
     */
    private String userId;

    /**
     * 创建时间
     * <p>记录该技能的创建时间戳</p>
     */
    private Timestamp creTime;

    /**
     * 更新时间
     * <p>记录该技能的最后修改时间戳</p>
     */
    private Timestamp updTime;
}
