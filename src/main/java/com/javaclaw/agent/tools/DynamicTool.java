package com.javaclaw.agent.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

/**
 * 动态工具：基于JSON配置定义的工具，支持多种执行类型。
 *
 * 配置示例（存为 .json 文件）：
 *
 * 1. 调用Spring Bean（type=bean）：
 * {
 *   "name": "get_weather",
 *   "description": "查询天气",
 *   "type": "bean",
 *   "beanName": "weatherService",
 *   "method": "getWeather",
 *   "parameters": {
 *     "type": "object",
 *     "properties": {
 *       "city": {"type": "string", "description": "城市名"}
 *     },
 *     "required": ["city"]
 *   }
 * }
 *
 * 2. 无参数工具：
 * {
 *   "name": "get_server_time",
 *   "description": "获取当前服务器时间",
 *   "parameters": {}
 * }
 *
 * 3. 带参数工具（静态模板）：
 * {
 *   "name": "query_stock",
 *   "description": "查询股票价格",
 *   "parameters": {
 *     "type": "object",
 *     "properties": {
 *       "symbol": {
 *         "type": "string",
 *         "description": "股票代码，例如：AAPL"
 *       }
 *     },
 *     "required": ["symbol"]
 *   },
 *   "response": "股票 %symbol% 的价格是 $100"
 * }
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DynamicTool extends BaseTool {

    private String name;
    private String description;
    private Map<String, Object> parameters;
    private String responseTemplate;

    // Bean调用类型配置
    private String type;       // bean, script, exec 或 null（默认静态模板）
    private String beanName;   // Spring Bean名称
    private String method;     // 方法名

    // 存储原始配置，用于热更新比对
    @Setter
    private volatile long lastModified;

    // 参数校验规则（瞬态字段，从 parameters 解析）
    private transient List<String> requiredParams;
    private transient Map<String, ParamSchema> paramSchemas;

    /** Jackson 反序列化用的默认构造函数 */
    public DynamicTool() {
        this.parameters = Collections.emptyMap();
        this.lastModified = System.currentTimeMillis();
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

    @JsonProperty("parameters")
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
        this.requiredParams = parseRequiredParams(parameters);
        this.paramSchemas = parseParamSchemas(parameters);
    }

    @JsonProperty("response")
    public void setResponseTemplate(String responseTemplate) {
        this.responseTemplate = responseTemplate;
    }

    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

    @JsonProperty("beanName")
    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    @JsonProperty("method")
    public void setMethod(String method) {
        this.method = method;
    }

    /** 判断是否为Bean调用类型 */
    public boolean isBeanCall() {
        return "bean".equals(type);
    }

    /** 判断是否有有效的Bean调用配置 */
    public boolean hasValidBeanConfig() {
        return isBeanCall() && beanName != null && !beanName.isEmpty() && method != null && !method.isEmpty();
    }

    public DynamicTool(String name, String description, Map<String, Object> parameters, String responseTemplate) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
        this.responseTemplate = responseTemplate;
        this.lastModified = System.currentTimeMillis();
        this.requiredParams = parseRequiredParams(parameters);
        this.paramSchemas = parseParamSchemas(parameters);
    }

    @Override
    public Map<String, Object> getParameters() {
        return parameters != null ? parameters : emptyParams();
    }

    @Override
    public String execute(Map<String, Object> params) {
        if (responseTemplate == null || responseTemplate.isEmpty()) {
            return "OK: " + name + " executed successfully";
        }
        return responseTemplate;
    }

    @Override
    public List<String> validateParams(Map<String, Object> params) {
        List<String> errors = new ArrayList<>();
        if (params == null) {
            params = Collections.emptyMap();
        }

        if (requiredParams != null) {
            for (String required : requiredParams) {
                if (!params.containsKey(required) || params.get(required) == null) {
                    errors.add("Missing required parameter: " + required);
                }
            }
        }

        if (paramSchemas != null) {
            for (Map.Entry<String, ParamSchema> entry : paramSchemas.entrySet()) {
                String paramName = entry.getKey();
                ParamSchema schema = entry.getValue();
                Object value = params.get(paramName);

                if (value != null) {
                    String actualType = value.getClass().getSimpleName().toLowerCase();
                    String expectedType = schema.type;

                    if ("string".equals(expectedType) && !(value instanceof String)) {
                        errors.add("Parameter '" + paramName + "' should be string, got: " + actualType);
                    } else if ("number".equals(expectedType) && !(value instanceof Number)) {
                        errors.add("Parameter '" + paramName + "' should be number, got: " + actualType);
                    } else if ("integer".equals(expectedType) && !(value instanceof Integer)) {
                        errors.add("Parameter '" + paramName + "' should be integer, got: " + actualType);
                    } else if ("boolean".equals(expectedType) && !(value instanceof Boolean)) {
                        errors.add("Parameter '" + paramName + "' should be boolean, got: " + actualType);
                    }
                }
            }
        }

        return errors;
    }

    private List<String> parseRequiredParams(Map<String, Object> params) {
        List<String> required = new ArrayList<>();
        if (params != null && params.containsKey("required")) {
            Object req = params.get("required");
            if (req instanceof List) {
                for (Object r : (List<?>) req) {
                    if (r != null) {
                        required.add(r.toString());
                    }
                }
            }
        }
        return required;
    }

    private Map<String, ParamSchema> parseParamSchemas(Map<String, Object> params) {
        Map<String, ParamSchema> schemas = new HashMap<>();
        if (params != null && params.containsKey("properties")) {
            Object props = params.get("properties");
            if (props instanceof Map) {
                Map<?, ?> properties = (Map<?, ?>) props;
                for (Map.Entry<?, ?> entry : properties.entrySet()) {
                    String pName = entry.getKey().toString();
                    if (entry.getValue() instanceof Map) {
                        Map<?, ?> schemaMap = (Map<?, ?>) entry.getValue();
                        String type = schemaMap.containsKey("type") ? schemaMap.get("type").toString() : "string";
                        schemas.put(pName, new ParamSchema(pName, type));
                    }
                }
            }
        }
        return schemas;
    }

    private Map<String, Object> emptyParams() {
        Map<String, Object> empty = new HashMap<>();
        empty.put("type", "object");
        empty.put("properties", new HashMap<>());
        return empty;
    }

    @Getter
    public static class ParamSchema {
        private final String name;
        private final String type;

        public ParamSchema(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }
}