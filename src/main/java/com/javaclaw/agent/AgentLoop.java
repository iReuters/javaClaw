package com.javaclaw.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javaclaw.bus.InboundMessage;
import com.javaclaw.bus.OutboundMessage;
import com.javaclaw.config.ExecToolConfig;
import com.javaclaw.config.MCPServerConfig;
import com.javaclaw.context.SkillContext;
import com.javaclaw.cron.CronService;
import com.javaclaw.mapper.SkillMapper;
import com.javaclaw.providers.LLMProvider;
import com.javaclaw.providers.ToolCallRequest;
import com.javaclaw.session.Session;
import com.javaclaw.session.SessionManager;
import com.javaclaw.agent.tools.ToolRegistry;
import com.javaclaw.agent.tools.DynamicTool;
import com.javaclaw.agent.tools.DynamicToolLoader;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Agent 主循环：处理消息、建会话/上下文、调 LLM、执行 tool_call、写回复/会话。
 */
public class AgentLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String REFLECT_USER_MSG = "Reflect on the results and decide next steps.";

    private final LLMProvider provider;
    private final Path workspace;
    private final String model;
    private final int maxIterations;
    private final double temperature;
    private final int maxTokens;
    private final int memoryWindow;
    private final ExecToolConfig execConfig;
    private final boolean restrictToWorkspace;
    private final SessionManager sessionManager;
    private final ToolRegistry toolRegistry;
    private final DynamicToolLoader dynamicToolLoader;
    private final ContextBuilder contextBuilder;
    private final SkillsLoader skillsLoader;
    private final SkillService skillService;

    private CronService cronService;
    private Map<String, MCPServerConfig> mcpServers;

    @Autowired
    public AgentLoop(LLMProvider provider,
                    Path workspace,
                    String model,
                    int maxIterations,
                    double temperature,
                    int maxTokens,
                    int memoryWindow,
                    String braveApiKey,
                    ExecToolConfig execConfig,
                    CronService cronService,
                    boolean restrictToWorkspace,
                    SessionManager sessionManager,
                    Map<String, MCPServerConfig> mcpServers,
                    ToolRegistry toolRegistry,
                    DynamicToolLoader dynamicToolLoader,
                    SkillMapper skillMapper,
                    SkillService skillService) {
        this.provider = provider;
        this.workspace = workspace != null ? workspace : java.nio.file.Paths.get(".", ".javaclawbot");
        this.model = model != null && !model.isEmpty() ? model : provider.getDefaultModel();
        this.maxIterations = maxIterations > 0 ? maxIterations : 10;
        this.temperature = temperature >= 0 ? temperature : 0.7;
        this.maxTokens = maxTokens > 0 ? maxTokens : 4096;
        this.memoryWindow = memoryWindow > 0 ? memoryWindow : 20;
        this.execConfig = execConfig;
        this.restrictToWorkspace = restrictToWorkspace;
        this.sessionManager = sessionManager;
        this.cronService = cronService;
        this.mcpServers = mcpServers != null ? mcpServers : Collections.emptyMap();
        this.skillsLoader = new SkillsLoader(skillMapper, this.workspace, null);
        this.skillService = skillService;
        this.contextBuilder = new ContextBuilder(this.workspace, skillsLoader, skillService);
        this.toolRegistry = toolRegistry;
        this.dynamicToolLoader = dynamicToolLoader;
    }

    /** 处理单条入站消息：session、命令、buildMessages、runAgentLoop、写 session、返回 OutboundMessage */
    public OutboundMessage processMessage(InboundMessage msg) {
        return processMessage(msg, null, null);
    }

    public OutboundMessage processMessage(InboundMessage msg, String sessionKeyOverride) {
        return processMessage(msg, sessionKeyOverride, null);
    }

    /**
     * 同上；streamConsumer 非 null 时，将 LLM 的 content 增量回调（流式输出）。仅最后一轮纯文本回复会流式。
     */
    public OutboundMessage processMessage(InboundMessage msg, String sessionKeyOverride, Consumer<String> streamConsumer) {
        return processMessage(msg, sessionKeyOverride, streamConsumer, null);
    }

    /**
     * 带步骤回调的 processMessage，用于 SSE 流式输出步骤
     * @param streamConsumer 内容块回调（已废弃，仅保留兼容性）
     * @param stepCallback 步骤回调，每完成一个步骤触发
     */
    public OutboundMessage processMessage(InboundMessage msg, String sessionKeyOverride, Consumer<String> streamConsumer, Consumer<AgentStep> stepCallback) {
        String sessionKey = sessionKeyOverride != null ? sessionKeyOverride : msg.getSessionKey();
        String channel = msg.getChannel();
        String chatId = msg.getChatId();
        String content = msg.getContent() != null ? msg.getContent() : "";

        if ("system".equals(channel)) {
            return processSystemMessage(msg);
        }

        Session session = sessionManager.getOrCreate(sessionKey);

        if (content.trim().equals("/new")) {
            session.clear();
            sessionManager.save(session);
            OutboundMessage out = new OutboundMessage(channel, chatId, "New session started.");
            out.setMetadata(msg.getMetadata() != null ? msg.getMetadata() : java.util.Collections.emptyMap());
            return out;
        }
        if (content.trim().equals("/help")) {
            OutboundMessage out = new OutboundMessage(channel, chatId,
                    "Commands: /new (clear session), /help (this message).");
            out.setMetadata(msg.getMetadata() != null ? msg.getMetadata() : java.util.Collections.emptyMap());
            return out;
        }

        Map<String, Object> requestContext = new HashMap<>();
        requestContext.put("channel", channel);
        requestContext.put("chatId", chatId);
        requestContext.put("metadata", msg.getMetadata() != null ? msg.getMetadata() : Collections.emptyMap());
        List<Map<String, Object>> history = session.getHistory(memoryWindow);
        List<String> skillNames = skillsLoader.getAlwaysSkills();
        List<Map<String, Object>> initialMessages = contextBuilder.buildMessages(
                history, content, skillNames, msg.getMedia(), channel, chatId);
        // 传入 skillName 用于工具过滤（取第一个 skill）
        String activeSkill = (skillNames != null && !skillNames.isEmpty()) ? skillNames.get(0) : null;
        RunResult result = runAgentLoop(initialMessages, streamConsumer, requestContext, activeSkill, stepCallback);
        session.addMessage("user", content, null);
        session.addMessage("assistant", result.getContent(), null);
        sessionManager.save(session);
        OutboundMessage out = new OutboundMessage(channel, chatId, result.getContent());
        out.setMetadata(msg.getMetadata() != null ? msg.getMetadata() : java.util.Collections.emptyMap());
        return out;
    }

    private OutboundMessage processSystemMessage(InboundMessage msg) {
        return processMessage(msg, msg.getSessionKey());
    }

    /** 多轮 chat + tool 直到无 tool_calls；返回最终回复与使用过的工具名。streamConsumer 非 null 时对最后一轮纯文本回复做流式回调。requestContext 可为 null，供 message 等工具使用。 */
    public RunResult runAgentLoop(List<Map<String, Object>> initialMessages) {
        return runAgentLoop(initialMessages, null, null);
    }

    public RunResult runAgentLoop(List<Map<String, Object>> initialMessages, Consumer<String> streamConsumer) {
        return runAgentLoop(initialMessages, streamConsumer, null);
    }

    public RunResult runAgentLoop(List<Map<String, Object>> initialMessages, Consumer<String> streamConsumer, Map<String, Object> requestContext) {
        return runAgentLoop(initialMessages, streamConsumer, requestContext, null);
    }

    /**
     * 带 skill 名称的 runAgentLoop，用于过滤工具定义
     */
    public RunResult runAgentLoop(List<Map<String, Object>> initialMessages, Consumer<String> streamConsumer, Map<String, Object> requestContext, String skillName) {
        return runAgentLoop(initialMessages, streamConsumer, requestContext, skillName, null);
    }

    /**
     * 带 skill 名称和步骤回调的 runAgentLoop
     * @param stepCallback 每完成一个步骤回调一次，用于 SSE 流式输出步骤
     */
    public RunResult runAgentLoop(List<Map<String, Object>> initialMessages, Consumer<String> streamConsumer, Map<String, Object> requestContext, String skillName, Consumer<AgentStep> stepCallback) {
        List<Map<String, Object>> messages = new ArrayList<>(initialMessages);
        List<String> toolsUsed = new ArrayList<>();
        List<AgentStep> steps = new ArrayList<>();
        int iter = 0;

        // 加载所有工具定义（Bean 工具 + 动态工具 + SkillTool）
        List<Map<String, Object>> toolDefs = new ArrayList<>(toolRegistry.getDefinitions());
        if (dynamicToolLoader != null) {
            toolDefs.addAll(dynamicToolLoader.getAllDynamicToolDefinitions());
        }

        // SkillTool 已经在 ToolRegistry 中注册，会被自动包含

        // 设置当前 skill 到 ThreadLocal，供 AOP 切面获取（默认 metadata）
        String currentSkill = skillName != null ? skillName : "metadata";
        SkillContext.setSkill(currentSkill);

        try {
            while (iter < maxIterations) {
                iter++;
                AgentStep step = new AgentStep(iter, "tool_call");
                com.javaclaw.providers.LLMResponse response = provider.chat(
                        messages,
                        toolDefs,
                        model,
                        maxTokens,
                        temperature,
                        streamConsumer);

                // 设置思考内容
                step.setReasoning(response.getReasoningContent());

                if (response.hasToolCalls()) {
                    List<Map<String, Object>> toolCallsForMessage = new ArrayList<>();
                    for (ToolCallRequest tc : response.getToolCalls()) {
                        Map<String, Object> fn = new HashMap<>();
                        fn.put("id", tc.getId());
                        fn.put("type", "function");
                        Map<String, Object> f = new HashMap<>();
                        f.put("name", tc.getName());
                        try {
                            f.put("arguments", MAPPER.writeValueAsString(tc.getArguments()));
                        } catch (Exception e) {
                            f.put("arguments", "{}");
                        }
                        fn.put("function", f);
                        toolCallsForMessage.add(fn);

                        // 添加工具调用到步骤
                        step.addToolCall(tc.getId(), tc.getName(), tc.getArguments() != null ? tc.getArguments().toString() : "{}");
                    }

                    // 设置回复内容（可能是空或简短总结）
                    step.setContent(response.getContent());
                    step.setType("tool_call");

                    contextBuilder.addAssistantMessage(messages,
                            response.getContent(),
                            toolCallsForMessage,
                            response.getReasoningContent());

                    // 执行工具调用并记录结果
                    for (ToolCallRequest tc : response.getToolCalls()) {
                        Map<String, Object> params = new HashMap<>(tc.getArguments() != null ? tc.getArguments() : Collections.<String, Object>emptyMap());
                        if (requestContext != null) {
                            params.putAll(requestContext);
                        }
                        String result = executeTool(tc.getName(), params);
                        toolsUsed.add(tc.getName());
                        step.addToolResult(tc.getName(), result);
                        contextBuilder.addToolResult(messages, tc.getId(), tc.getName(), result);
                    }

                    // 回调步骤
                    if (stepCallback != null) {
                        stepCallback.accept(step);
                    }
                    steps.add(step);

                    Map<String, Object> userReflect = new HashMap<>();
                    userReflect.put("role", "user");
                    userReflect.put("content", REFLECT_USER_MSG);
                    messages.add(userReflect);
                } else {
                    // 最终回复步骤
                    step.setType("final");
                    step.setContent(response.getContent() != null ? response.getContent() : "");
                    step.setReasoning(response.getReasoningContent());

                    if (stepCallback != null) {
                        stepCallback.accept(step);
                    }
                    steps.add(step);

                    return new RunResult(response.getContent(), toolsUsed, steps);
                }
            }

            // 达到最大迭代次数
            AgentStep maxIterStep = new AgentStep(iter, "max_iterations");
            maxIterStep.setContent("[Max tool iterations reached]");
            if (stepCallback != null) {
                stepCallback.accept(maxIterStep);
            }
            steps.add(maxIterStep);

            return new RunResult("[Max tool iterations reached]", toolsUsed, steps);
        } finally {
            SkillContext.clear();
        }
    }

    /**
     * 从 LLM 回复中解析 skillId
     * 期望格式：USE_SKILL: <skill_id>
     */
    private String parseSkillIdFromResponse(String content) {
        if (content == null || content.isEmpty()) {
            return "metadata";
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("USE_SKILL:\\s*(\\S+)");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        log.warn("Failed to parse skillId from response: {}", content);
        return "metadata";
    }

    /**
     * 用 skill 内容更新 system message
     */
    private void updateSystemMessageWithSkill(List<Map<String, Object>> messages, String skillId, String skillContent) {
        if (messages.isEmpty()) return;
        Map<String, Object> systemMsg = messages.get(0);
        String existingContent = (String) systemMsg.getOrDefault("content", "");
        String updatedContent = existingContent + "\n\n=== Selected Skill: " + skillId + " ===\n\n" + skillContent;
        systemMsg.put("content", updatedContent);
    }

    /** 执行工具（动态工具优先，Bean 工具兜底） */
    private String executeTool(String name, Map<String, Object> params) {
        log.info("executeTool called: name={}, params={}", name, params);

        // 优先尝试动态工具
        if (dynamicToolLoader != null && dynamicToolLoader.getDynamicTool(name) != null) {
            log.info("  -> Executing via dynamicToolLoader");
            return dynamicToolLoader.executeDynamicTool(name, params);
        }
        // 再尝试 Bean 工具
        if (toolRegistry.has(name)) {
            log.info("  -> Executing via toolRegistry");
            return toolRegistry.execute(name, params);
        }
        log.warn("  -> Tool not found: {}", name);
        return "[Error: tool not found: " + name + "]";
    }

    /** 直接处理一条用户消息（不经过总线），返回 Agent 回复文本。用于 CLI、Cron、Heartbeat。 */
    public String processDirect(String content, String sessionKey, String channel, String chatId) {
        return processDirect(content, sessionKey, channel, chatId, null);
    }

    /** 带流式回调：streamConsumer 非 null 时，回复内容会增量回调，控制台可边收边打。 */
    public String processDirect(String content, String sessionKey, String channel, String chatId, Consumer<String> streamConsumer) {
        InboundMessage msg = new InboundMessage(channel, "user", chatId, content);
        OutboundMessage out = processMessage(msg, sessionKey, streamConsumer);
        return out != null ? out.getContent() : "";
    }

    /** 重载：默认 sessionKey=cli:direct, channel=cli, chatId=direct */
    public String processDirect(String content) {
        return processDirect(content, "cli:direct", "cli", "direct");
    }

    /** 重载：带流式回调，用于 CLI 控制台逐字输出 */
    public String processDirect(String content, Consumer<String> streamConsumer) {
        return processDirect(content, "cli:direct", "cli", "direct", streamConsumer);
    }

    /** 关闭 MCP 连接（占位） */
    public void closeMcp() {
        // no-op for now
    }

    /** 懒连接 MCP（占位） */
    public void connectMcp() {
        // no-op for now
    }



    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }
}
