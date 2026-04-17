package com.javaclaw.aspect;

import com.javaclaw.context.SkillContext;
import com.javaclaw.service.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Aspect
@Component
@Slf4j
public class SecurityMonitorAspect {

    @Autowired
    private AuditLogService auditLogService;

    private static final String DEFAULT_LLM_MODEL = "deepseek-chat";
    private static final String DEFAULT_MEMORY_ID = "unknown";

    @Pointcut("execution(* com.javaclaw.agent.tools.ToolRegistry.execute(..))")
    public void toolExecutePointcut() {}

    @Pointcut("execution(* com.javaclaw.agent.tools.DynamicToolLoader.executeDynamicTool(..))")
    public void dynamicToolExecutePointcut() {}

    @Around("toolExecutePointcut() || dynamicToolExecutePointcut()")
    public Object aroundToolExecute(ProceedingJoinPoint joinPoint) throws Throwable {
        String toolName = extractToolName(joinPoint);
        long startTime = System.currentTimeMillis();
        String memoryId = extractMemoryId(joinPoint);
        String skillName = SkillContext.getSkill();  // 从 ThreadLocal 获取当前 skill
        String resultValue = "";
        boolean success = true;
        String errorMsg = null;
        Object returnValue = null;

        try {
            returnValue = joinPoint.proceed();
            resultValue = returnValue != null ? returnValue.toString() : "";
        } catch (Throwable t) {
            success = false;
            errorMsg = t.getMessage();
            resultValue = "[Error: " + t.getMessage() + "]";
            throw t;
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;
            Map<String, Object> params = extractParams(joinPoint);

            auditLogService.logToolCall(
                    memoryId,
                    toolName,
                    skillName,  // 传入当前 skill 名称，可能为空
                    durationMs,
                    DEFAULT_LLM_MODEL,
                    0,
                    params,
                    resultValue,
                    success,
                    errorMsg
            );
        }

        return returnValue;
    }

    private String extractToolName(ProceedingJoinPoint joinPoint) {
        String signature = joinPoint.getSignature().toShortString();
        if (signature.contains("execute")) {
            Object[] args = joinPoint.getArgs();
            if (args.length > 0 && args[0] instanceof String) {
                return (String) args[0];
            }
        }
        return "unknown";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractParams(ProceedingJoinPoint joinPoint) {
        Map<String, Object> params = new HashMap<>();
        Object[] args = joinPoint.getArgs();
        if (args.length > 1 && args[1] instanceof Map) {
            params.putAll((Map<String, Object>) args[1]);
        }
        params.remove("channel");
        params.remove("chatId");
        params.remove("metadata");
        return params;
    }

    private String extractMemoryId(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length > 1 && args[1] instanceof Map) {
            Map<String, Object> params = (Map<String, Object>) args[1];
            Object memoryId = params.get("sessionKey");
            if (memoryId != null) {
                return memoryId.toString();
            }
        }
        return DEFAULT_MEMORY_ID;
    }
}