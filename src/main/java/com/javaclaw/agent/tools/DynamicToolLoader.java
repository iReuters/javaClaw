package com.javaclaw.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 动态工具加载器：监控工具配置目录，实时加载/卸载/更新工具。
 * 工具通过JSON文件配置，放在配置的目录中。
 * 不直接依赖 ToolRegistry，通过 ApplicationContext 按需获取。
 */
@Component
@Slf4j
public class DynamicToolLoader {

    private static final String SKILL_TOOLS_CONFIG = "skill-tools.yml";
    private static final String DYNAMIC_TOOLS_DIR = "tools";

    private final Path workspace;
    private final ObjectMapper jsonMapper;

    // 工具定义目录
    private Path toolsDir;

    // 技能工具配置缓存
    private volatile Map<String, List<String>> skillToolsConfig = new HashMap<>();
    private volatile List<String> defaultTools = new ArrayList<>();

    // 动态加载的工具实例
    private final Map<String, DynamicTool> dynamicTools = new ConcurrentHashMap<>();

    // 监听服务
    private ExecutorService watcherExecutor;
    private volatile boolean watching = false;

    @Autowired
    private ApplicationContext applicationContext;

    public DynamicToolLoader() {
        this.workspace = Paths.get(".javaclawbot");
        this.jsonMapper = new ObjectMapper();
    }

    public DynamicToolLoader(Path workspace) {
        this.workspace = workspace;
        this.jsonMapper = new ObjectMapper();
    }

    @PostConstruct
    public void init() {
        this.toolsDir = workspace.resolve(DYNAMIC_TOOLS_DIR);
        loadSkillToolsConfig();
        loadAllDynamicTools();
        registerAllToRegistry();
        startWatching();
    }

    /** 按需获取 ToolRegistry */
    private ToolRegistry getToolRegistry() {
        try {
            return applicationContext.getBean(ToolRegistry.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 加载技能-工具映射配置
     */
    public void loadSkillToolsConfig() {
        try {
            Path configPath = workspace.resolve(SKILL_TOOLS_CONFIG);
            if (Files.exists(configPath)) {
                String content = readFileContent(configPath);
                SkillToolConfig config = parseSkillToolConfig(content);
                if (config != null) {
                    if (config.getSkills() != null) {
                        this.skillToolsConfig = new HashMap<>(config.getSkills());
                    }
                    if (config.getDefaultTools() != null) {
                        this.defaultTools = new ArrayList<>(config.getDefaultTools());
                    }
                    log.info("Loaded skill-tools config: {} skills, {} default tools",
                            skillToolsConfig.size(), defaultTools.size());
                }
            } else {
                log.info("No skill-tools.yml found, using defaults");
            }
        } catch (Exception e) {
            log.error("Failed to load skill-tools config", e);
        }
    }

    /**
     * 解析YAML配置（简化实现）
     */
    private SkillToolConfig parseSkillToolConfig(String content) {
        SkillToolConfig config = new SkillToolConfig();
        Map<String, List<String>> skillsMap = new HashMap<>();
        List<String> defaults = new ArrayList<>();

        String[] lines = content.split("\n");
        boolean inSkills = false;
        boolean inDefaults = false;
        String currentSkill = null;
        List<String> currentTools = null;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("default:")) {
                inDefaults = true;
                inSkills = false;
            } else if (trimmed.startsWith("skills:")) {
                inSkills = true;
                inDefaults = false;
            } else if (inDefaults && trimmed.startsWith("-")) {
                String toolName = trimmed.substring(1).trim();
                if (!toolName.isEmpty()) {
                    defaults.add(toolName);
                }
            } else if (trimmed.isEmpty() && currentSkill != null && currentTools != null) {
                skillsMap.put(currentSkill, currentTools);
                currentSkill = null;
                currentTools = null;
            } else if (inSkills && trimmed.contains(":")) {
                int colonIdx = trimmed.indexOf(":");
                String key = trimmed.substring(0, colonIdx).trim();
                String val = trimmed.substring(colonIdx + 1).trim();
                if (val.isEmpty() || val.equals("[]") || val.equals("[]")) {
                    if (currentSkill != null && currentTools != null) {
                        skillsMap.put(currentSkill, currentTools);
                    }
                    currentSkill = key;
                    currentTools = new ArrayList<>();
                } else if (trimmed.startsWith("-")) {
                    String toolName = trimmed.substring(1).trim();
                    if (!toolName.isEmpty() && currentTools != null) {
                        currentTools.add(toolName);
                    }
                }
            } else if (inSkills && trimmed.startsWith("-") && currentTools != null) {
                String toolName = trimmed.substring(1).trim();
                if (!toolName.isEmpty()) {
                    currentTools.add(toolName);
                }
            }
        }

        if (currentSkill != null && currentTools != null) {
            skillsMap.put(currentSkill, currentTools);
        }

        config.setSkills(skillsMap);
        config.setDefaultTools(defaults);
        return config;
    }

    private String readFileContent(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 加载所有动态工具定义
     */
    public void loadAllDynamicTools() {
        if (!Files.exists(toolsDir)) {
            try {
                Files.createDirectories(toolsDir);
                createSampleTools();
            } catch (IOException e) {
                log.error("Failed to create tools directory", e);
            }
            return;
        }

        try {
            Files.list(toolsDir)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(this::loadToolFile);
        } catch (IOException e) {
            log.error("Failed to load dynamic tools", e);
        }
    }

    /**
     * 创建示例工具文件
     */
    private void createSampleTools() {
        try {
            String noParamTool = "{\n" +
                    "    \"name\": \"get_server_time\",\n" +
                    "    \"description\": \"获取当前服务器时间\",\n" +
                    "    \"parameters\": {\n" +
                    "        \"type\": \"object\",\n" +
                    "        \"properties\": {}\n" +
                    "    },\n" +
                    "    \"response\": \"当前服务器时间：%datetime%\"\n" +
                    "}";

            String withParamTool = "{\n" +
                    "    \"name\": \"calculate\",\n" +
                    "    \"description\": \"执行简单数学计算\",\n" +
                    "    \"parameters\": {\n" +
                    "        \"type\": \"object\",\n" +
                    "        \"properties\": {\n" +
                    "            \"expression\": {\n" +
                    "                \"type\": \"string\",\n" +
                    "                \"description\": \"数学表达式，例如：2+3*4\"\n" +
                    "            }\n" +
                    "        },\n" +
                    "        \"required\": [\"expression\"]\n" +
                    "    },\n" +
                    "    \"response\": \"计算结果：%result%\"\n" +
                    "}";

            writeFileContent(toolsDir.resolve("get_server_time.json"), noParamTool);
            writeFileContent(toolsDir.resolve("calculate.json"), withParamTool);
            log.info("Created sample tool files in {}", toolsDir);
        } catch (IOException e) {
            log.error("Failed to create sample tools", e);
        }
    }

    private void writeFileContent(Path path, String content) throws IOException {
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 从文件加载单个工具
     */
    private void loadToolFile(Path toolFile) {
        try {
            String content = readFileContent(toolFile);
            DynamicTool tool = jsonMapper.readValue(content, DynamicTool.class);

            // 单独提取 response 字段（字段名不一致）
            String responseTemplate = null;
            try {
                Map<?, ?> map = jsonMapper.readValue(content, Map.class);
                if (map.containsKey("response")) {
                    responseTemplate = map.get("response").toString();
                }
            } catch (Exception ignored) {}

            // 设置 responseTemplate
            tool.setResponseTemplate(responseTemplate);
            tool.setLastModified(Files.getLastModifiedTime(toolFile).toMillis());

            // 记录日志，方便调试
            log.info("Loaded dynamic tool: {} (type={}, beanName={}, method={})",
                    tool.getName(), tool.getType(), tool.getBeanName(), tool.getMethod());

            DynamicTool old = dynamicTools.put(tool.getName(), tool);
            if (old != null) {
                log.info("Updated dynamic tool: {}", tool.getName());
            }
        } catch (Exception e) {
            log.error("Failed to load tool from {}", toolFile, e);
        }
    }

    /**
     * 卸载指定工具
     */
    public void unloadTool(String toolName) {
        DynamicTool removed = dynamicTools.remove(toolName);
        if (removed != null) {
            log.info("Unloaded dynamic tool: {}", toolName);
            ToolRegistry registry = getToolRegistry();
            if (registry != null) {
                registry.unregister(toolName);
            }
        }
    }

    /**
     * 删除工具文件并卸载
     */
    public void deleteToolFile(String toolName) {
        Path toolFile = toolsDir.resolve(toolName + ".json");
        try {
            Files.deleteIfExists(toolFile);
            unloadTool(toolName);
            log.info("Deleted tool file: {}", toolFile);
        } catch (IOException e) {
            log.error("Failed to delete tool file: {}", toolFile, e);
        }
    }

    /**
     * 创建或更新工具文件（热更新）
     */
    public void saveToolFile(String toolName, String jsonContent) {
        Path toolFile = toolsDir.resolve(toolName + ".json");
        try {
            writeFileContent(toolFile, jsonContent);
            log.info("Saved tool file: {}", toolFile);
        } catch (IOException e) {
            log.error("Failed to save tool file: {}", toolFile, e);
        }
    }

    /**
     * 获取指定skill可用的工具名列表
     */
    public List<String> getToolsForSkill(String skillName) {
        List<String> tools = new ArrayList<>();
        tools.addAll(defaultTools);
        List<String> skillSpecific = skillToolsConfig.get(skillName);
        if (skillSpecific != null) {
            tools.addAll(skillSpecific);
        }
        return tools;
    }

    /**
     * 获取skill可用的工具定义（注册到registry的）
     */
    public List<Map<String, Object>> getToolDefinitionsForSkill(String skillName) {
        List<String> toolNames = getToolsForSkill(skillName);
        List<Map<String, Object>> definitions = new ArrayList<>();

        ToolRegistry registry = getToolRegistry();

        for (String name : toolNames) {
            if (registry != null) {
                Optional<Tool> tool = registry.get(name);
                if (tool.isPresent()) {
                    if (tool.get() instanceof BaseTool) {
                        definitions.add(((BaseTool) tool.get()).toSchema());
                    }
                }
            }

            DynamicTool dynamic = dynamicTools.get(name);
            if (dynamic != null) {
                definitions.add(dynamic.toSchema());
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
     * 注册所有动态工具到registry
     */
    public void registerAllToRegistry() {
        ToolRegistry registry = getToolRegistry();
        if (registry == null) {
            log.warn("ToolRegistry not available, skipping registration");
            return;
        }

        for (DynamicTool tool : dynamicTools.values()) {
            registry.register(tool);
        }
        log.info("Registered {} dynamic tools to registry", dynamicTools.size());
    }

    /**
     * 从registry移除动态工具
     */
    public void unregisterFromRegistry() {
        ToolRegistry registry = getToolRegistry();
        if (registry == null) {
            return;
        }

        for (String name : dynamicTools.keySet()) {
            registry.unregister(name);
        }
        log.info("Unregistered all dynamic tools from registry");
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
            return "[Error: " + e.getMessage() + "]";
        }
    }

    /**
     * 执行Spring Bean方法
     */
    private String executeBeanMethod(DynamicTool tool, Map<String, Object> params) throws Exception {
        String beanName = tool.getBeanName();
        String methodName = tool.getMethod();

        // 从ApplicationContext获取Bean
        Object bean = applicationContext.getBean(beanName);

        // 查找匹配的方法
        Class<?> beanClass = bean.getClass();
        Method method = findMatchingMethod(beanClass, methodName, params);

        if (method == null) {
            throw new RuntimeException("Method '" + methodName + "' not found in bean '" + beanName + "' with compatible parameters");
        }

        // 准备方法参数
        Object[] args = prepareMethodArgs(method, params);

        // 调用方法
        Object result = method.invoke(bean, args);

        // 处理返回值
        if (result == null) {
            return "OK";
        }

        // 如果返回的是字符串，直接返回
        if (result instanceof String) {
            return (String) result;
        }

        // 其他类型转为JSON
        return jsonMapper.writeValueAsString(result);
    }

    /**
     * 查找匹配的方法（根据参数名或参数类型）
     */
    private Method findMatchingMethod(Class<?> beanClass, String methodName, Map<String, Object> params) {
        // 先尝试精确匹配
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

        // 如果参数数量不匹配，排除varargs情况
        if (!method.isVarArgs() && paramTypes.length != params.size()) {
            return false;
        }

        // 检查每个参数类型
        int i = 0;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (i >= paramTypes.length) {
                return false;
            }
            Class<?> paramType = paramTypes[i];
            Object value = entry.getValue();

            if (value == null) {
                // null值可以赋值给任何引用类型
                i++;
                continue;
            }

            // 检查类型兼容性
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
        // 包装类型兼容
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

        // 如果获取到参数名，按名称匹配
        if (paramNames != null && paramNames.length == paramTypes.length) {
            for (int i = 0; i < paramTypes.length; i++) {
                Object value = params.get(paramNames[i]);
                args[i] = convertValue(value, paramTypes[i]);
            }
        } else {
            // 否则按 Map 迭代顺序（假设与参数顺序一致）
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
     * 获取方法参数名（需要编译时加 -parameters）
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
        return args;
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

        return value;
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
     * 简单表达式求值（安全）
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

    /**
     * 启动文件监听
     */
    private void startWatching() {
        if (!Files.exists(toolsDir)) {
            return;
        }

        watching = true;
        watcherExecutor = Executors.newSingleThreadExecutor();

        watcherExecutor.submit(() -> {
            try {
                WatchService watchService = FileSystems.getDefault().newWatchService();
                toolsDir.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);

                log.info("Started watching tools directory: {}", toolsDir);

                while (watching) {
                    WatchKey key = watchService.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            continue;
                        }

                        Path changed = (Path) event.context();
                        if (changed.toString().endsWith(".json")) {
                            if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                                String toolName = changed.toString().replace(".json", "");
                                unloadTool(toolName);
                            } else {
                                loadToolFile(toolsDir.resolve(changed));
                                DynamicTool tool = dynamicTools.get(
                                        changed.toString().replace(".json", ""));
                                ToolRegistry registry = getToolRegistry();
                                if (tool != null && registry != null) {
                                    registry.register(tool);
                                }
                            }
                        }
                    }
                    key.reset();
                }
            } catch (Exception e) {
                log.error("Tool file watcher error", e);
            }
        });
    }

    /**
     * 停止文件监听
     */
    public void stopWatching() {
        watching = false;
        if (watcherExecutor != null) {
            watcherExecutor.shutdownNow();
        }
    }

    /**
     * 获取工具目录路径
     */
    public Path getToolsDir() {
        return toolsDir;
    }
}