package com.javaclaw.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javaclaw.dao.ToolDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态工具加载器：从数据库加载工具定义。
 * 支持 Bean 调用类型和静态模板类型。
 */
@Component
@Slf4j
public class DynamicToolLoader {

    @Autowired
    private ToolDao toolDao;

    @Autowired
    private ApplicationContext applicationContext;

    private final ObjectMapper jsonMapper = new ObjectMapper();

    // 动态加载的工具实例
    private final Map<String, DynamicTool> dynamicTools = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadAllTools();
    }

    /**
     * 从数据库加载所有工具
     */
    public void loadAllTools() {
        List<ToolDao.ToolRecord> records = toolDao.findAllEnabled();
        log.info("Loading {} tools from database", records.size());

        for (ToolDao.ToolRecord record : records) {
            try {
                DynamicTool tool = toolDao.parseToolFromJson(record.getToolJson());
                if (tool != null) {
                    dynamicTools.put(tool.getName(), tool);
                    log.info("Loaded tool: {} (type={})", tool.getName(), tool.getType());
                }
            } catch (Exception e) {
                log.error("Failed to load tool: {}", record.getToolKey(), e);
            }
        }
    }

    /**
     * 卸载指定工具
     */
    public void unloadTool(String toolName) {
        DynamicTool removed = dynamicTools.remove(toolName);
        if (removed != null) {
            log.info("Unloaded dynamic tool: {}", toolName);
        }
    }

    /**
     * 获取指定skill可用的工具名列表
     */
    public List<String> getToolsForSkill(String skillName) {
        List<String> tools = new ArrayList<>();
        tools.addAll(getDefaultTools());

        // 从数据库获取skill关联的工具
        ToolDao.ToolRecord toolRecord = toolDao.findByKey(skillName);
        if (toolRecord != null) {
            // skillName在agent_tools表中可能不存在，这里需要通过skill查找
        }

        // 如果skill有直接关联的工具，从agent_skills表查询
        return tools;
    }

    /**
     * 获取全局默认工具列表（暂时返回空的，实际从配置读取）
     */
    private List<String> getDefaultTools() {
        return new ArrayList<>();
    }

    /**
     * 获取skill关联的工具列表（从agent_skills表查询）
     */
    public List<String> getToolsForSkillFromDb(String skillId) {
        List<String> tools = new ArrayList<>();
        ToolDao.ToolRecord record = toolDao.findByKey(skillId);
        if (record != null) {
            // 直接从agent_tools表查不到skill的工具，需要用skillId查agent_skills
        }

        // 通过skillId查询agent_skills获取关联的工具
        // 这个逻辑需要SkillDao配合，暂时返回空
        return tools;
    }

    /**
     * 获取skill可用的工具定义
     */
    public List<Map<String, Object>> getToolDefinitionsForSkill(String skillName) {
        List<String> toolNames = getToolsForSkill(skillName);
        List<Map<String, Object>> definitions = new ArrayList<>();

        for (String name : toolNames) {
            DynamicTool tool = dynamicTools.get(name);
            if (tool != null) {
                definitions.add(tool.toSchema());
            }
        }

        return definitions;
    }

    /**
     * 获取所有动态工具定义
     */
    public List<Map<String, Object>> getAllDynamicToolDefinitions() {
        List<Map<String, Object>> definitions = new ArrayList<>();
        for (DynamicTool tool : dynamicTools.values()) {
            definitions.add(tool.toSchema());
        }
        return definitions;
    }

    /**
     * 获取所有动态工具名
     */
    public Set<String> getAllDynamicToolNames() {
        return new HashSet<>(dynamicTools.keySet());
    }

    /**
     * 获取动态工具实例
     */
    public DynamicTool getDynamicTool(String name) {
        return dynamicTools.get(name);
    }

    /**
     * 注册所有动态工具到registry（供外部调用）
     */
    public void registerAllToRegistry() {
        // 不再需要，工具直接从数据库加载
    }

    /**
     * 从registry移除动态工具
     */
    public void unregisterFromRegistry() {
        // 不再需要
    }

    /**
     * 执行动态工具
     */
    public String executeDynamicTool(String name, Map<String, Object> params) {
        DynamicTool tool = dynamicTools.get(name);
        if (tool == null) {
            return "[Error: dynamic tool not found: " + name + "]";
        }

        List<String> errors = tool.validateParams(params);
        if (!errors.isEmpty()) {
            return "[Error: invalid params: " + String.join("; ", errors) + "]";
        }

        try {
            // 如果是Bean调用，执行Spring Bean方法
            if (tool.hasValidBeanConfig()) {
                return executeBeanMethod(tool, params);
            }

            // 否则执行静态模板
            String response = tool.execute(params);
            return resolveResponseTemplate(response, params);
        } catch (Exception e) {
            log.error("Error executing dynamic tool", e);
            return "[Error: " + e.getMessage() + "]";
        }
    }

    /**
     * 执行Spring Bean方法
     */
    private String executeBeanMethod(DynamicTool tool, Map<String, Object> params) throws Exception {
        if (applicationContext == null) {
            throw new RuntimeException("ApplicationContext is null");
        }

        String beanName = tool.getBeanName();
        String methodName = tool.getMethod();

        log.info("executeBeanMethod: beanName={}, methodName={}, params={}", beanName, methodName, params);

        // 从ApplicationContext获取Bean
        Object bean = applicationContext.getBean(beanName);
        log.info("Got bean: {}", bean.getClass().getName());

        // 查找匹配的方法
        Class<?> beanClass = bean.getClass();
        Method method = findMatchingMethod(beanClass, methodName, params);

        if (method == null) {
            throw new RuntimeException("Method '" + methodName + "' not found in bean '" + beanName + "' with compatible parameters");
        }
        log.info("Found method: {} with {} params", method.getName(), method.getParameterTypes().length);

        // 准备方法参数
        Object[] args = prepareMethodArgs(method, params);
        log.info("Prepared args length: {}", args.length);

        // 调用方法
        Object result = method.invoke(bean, args);
        log.info("Method result: {}", result);

        // 处理返回值
        if (result == null) {
            return "OK";
        }

        if (result instanceof String) {
            return (String) result;
        }

        return jsonMapper.writeValueAsString(result);
    }

    /**
     * 查找匹配的方法（根据参数名或参数类型）
     */
    private Method findMatchingMethod(Class<?> beanClass, String methodName, Map<String, Object> params) {
        for (Method method : beanClass.getMethods()) {
            if (method.getName().equals(methodName)) {
                if (isMethodCompatible(method, params)) {
                    return method;
                }
            }
        }
        return null;
    }

    /**
     * 检查方法是否与参数兼容
     */
    private boolean isMethodCompatible(Method method, Map<String, Object> params) {
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length == 0) {
            return params == null || params.isEmpty();
        }
        if (params == null) {
            return paramTypes.length == 0;
        }

        // 单参数方法：检查参数类型是否兼容
        if (paramTypes.length == 1) {
            Class<?> paramType = paramTypes[0];
            for (Object value : params.values()) {
                if (value == null || isAssignable(paramType, value.getClass())) {
                    return true;
                }
            }
            return false;
        }

        // 多参数方法：检查类型兼容性
        if (!method.isVarArgs() && paramTypes.length != params.size()) {
            return false;
        }

        int i = 0;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (i >= paramTypes.length) {
                return false;
            }
            Class<?> paramType = paramTypes[i];
            Object value = entry.getValue();

            if (value == null) {
                i++;
                continue;
            }

            if (!isAssignable(paramType, value.getClass())) {
                return false;
            }
            i++;
        }
        return true;
    }

    /**
     * 检查类型是否可赋值
     */
    private boolean isAssignable(Class<?> target, Class<?> source) {
        if (target.isInstance(source)) {
            return true;
        }
        if (target == int.class && source == Integer.class) return true;
        if (target == long.class && source == Long.class) return true;
        if (target == double.class && source == Double.class) return true;
        if (target == float.class && source == Float.class) return true;
        if (target == boolean.class && source == Boolean.class) return true;
        if (target == String.class && source == String.class) return true;
        if (target == Object.class) return true;
        return false;
    }

    /**
     * 准备方法参数
     */
    private Object[] prepareMethodArgs(Method method, Map<String, Object> params) {
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length == 0) {
            return new Object[0];
        }

        Object[] args = new Object[paramTypes.length];

        // 尝试获取方法参数名（需要编译时 -parameters 标志）
        String[] paramNames = getMethodParamNames(method);

        if (paramNames != null && paramNames.length == paramTypes.length) {
            for (int i = 0; i < paramTypes.length; i++) {
                Object value = params.get(paramNames[i]);
                args[i] = convertValue(value, paramTypes[i]);
            }
        } else if (paramTypes.length == 1) {
            // 单参数方法：从params中找到第一个兼容的值
            for (Object value : params.values()) {
                if (isAssignable(paramTypes[0], value != null ? value.getClass() : Object.class)) {
                    args[0] = convertValue(value, paramTypes[0]);
                    return args;
                }
            }
            // 尝试获取特定参数名（如symbol, city, keyword等）
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String key = entry.getKey();
                if (!key.equals("channel") && !key.equals("chatId") && !key.equals("metadata")) {
                    args[0] = convertValue(entry.getValue(), paramTypes[0]);
                    return args;
                }
            }
        } else {
            int i = 0;
            for (Object value : params.values()) {
                if (i >= paramTypes.length) break;
                args[i] = convertValue(value, paramTypes[i]);
                i++;
            }
        }

        return args;
    }

    /**
     * 获取方法参数名
     */
    private String[] getMethodParamNames(Method method) {
        try {
            java.lang.reflect.Parameter[] params = method.getParameters();
            String[] names = new String[params.length];
            for (int i = 0; i < params.length; i++) {
                names[i] = params[i].getName();
            }
            return names;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 值类型转换
     */
    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }

        String strValue = value.toString();

        if (targetType == String.class) {
            return strValue;
        }
        if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(strValue);
        }
        if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(strValue);
        }
        if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(strValue);
        }
        if (targetType == float.class || targetType == Float.class) {
            return Float.parseFloat(strValue);
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(strValue);
        }
        return strValue;
    }

    /**
     * 解析response模板中的变量
     */
    private String resolveResponseTemplate(String template, Map<String, Object> params) {
        if (template == null) {
            return "OK";
        }

        String result = template;

        if (result.contains("%datetime%")) {
            result = result.replace("%datetime%", java.time.LocalDateTime.now().toString());
        }

        if (result.contains("%result%") && params.containsKey("expression")) {
            try {
                String expr = params.get("expression").toString();
                double calcResult = evaluateSimpleExpression(expr);
                result = result.replace("%result%", String.valueOf(calcResult));
            } catch (Exception e) {
                result = result.replace("%result%", "计算错误: " + e.getMessage());
            }
        }

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", String.valueOf(entry.getValue()));
        }

        return result;
    }

    /**
     * 简单表达式求值
     */
    private double evaluateSimpleExpression(String expr) {
        expr = expr.replaceAll("\\s+", "");
        if (!expr.matches("[0-9+\\-*/().]+")) {
            throw new IllegalArgumentException("Invalid expression");
        }
        try {
            javax.script.ScriptEngineManager m = new javax.script.ScriptEngineManager();
            javax.script.ScriptEngine engine = m.getEngineByName("JavaScript");
            return ((Number) engine.eval(expr)).doubleValue();
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot evaluate: " + expr);
        }
    }
}
