package com.javaclaw.entity;

import lombok.Data;

/**
 * 工具记录实体类
 * 对应数据库表：agent_tools（智能体工具定义表）
 *
 * <p>该表存储智能体的工具定义，支持两种类型的工具：
 * <ul>
 *   <li><b>Bean调用类型</b>：通过Spring Bean方法执行工具，如 type=bean, beanName=xxx, method=xxx</li>
 *   <li><b>静态模板类型</b>：通过预定义的响应模板执行工具，如 response="计算结果：%result%"</li>
 * </ul>
 *
 * <p>工具以JSON格式存储完整定义，包括：名称、描述、参数规范、返回值模板等
 */
@Data
public class ToolRecord {

    /**
     * 工具标识键（主键）
     * <p>如：get_weather、calculate、get_stock_price</p>
     */
    private String toolKey;

    /**
     * 完整的工具定义（JSON格式）
     * <p>JSON结构示例：
     * <pre>
     * {
     *   "name": "get_weather",
     *   "description": "查询城市天气信息",
     *   "type": "bean",
     *   "beanName": "weatherService",
     *   "method": "getWeather",
     *   "parameters": {
     *     "type": "object",
     *     "properties": {
     *       "city": {"type": "string", "description": "城市名称"}
     *     },
     *     "required": ["city"]
     *   }
     * }
     * </pre>
     */
    private String toolJson;

    /**
     * 是否启用
     * <p>0-禁用，1-启用</p>
     */
    private boolean enabled;

    /**
     * 调用超时时间（毫秒）
     * <p>默认30000ms，当工具执行超过此时间将自动中断</p>
     */
    private int timeoutMs;

    /**
     * 最大重试次数
     * <p>当工具调用失败时的最大重试次数，默认3次</p>
     */
    private int maxRetries;

    /**
     * 创建人
     * <p>记录创建该工具的用户ID，system表示系统内置工具</p>
     */
    private String userId;
}
