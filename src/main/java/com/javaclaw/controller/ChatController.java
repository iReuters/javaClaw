package com.javaclaw.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javaclaw.agent.AgentLoop;
import com.javaclaw.agent.AgentStep;
import com.javaclaw.agent.StepEvent;
import com.javaclaw.agent.TokenEvent;
import com.javaclaw.bus.InboundMessage;
import com.javaclaw.bus.OutboundMessage;
import com.javaclaw.session.SessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {
    private final AgentLoop agentLoop;
    private final ExecutorService executorService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ChatController(AgentLoop agentLoop) {
        this.agentLoop = agentLoop;
        this.executorService = Executors.newFixedThreadPool(10);
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping("/chat")
    public OutboundMessage chat(@RequestBody ChatRequest request) {
        InboundMessage message = new InboundMessage("web", "user", "direct", request.getMessage());
        return agentLoop.processMessage(message,
                request.getSessionKey() != null ? request.getSessionKey() : "web:direct",
                null);
    }

    /**
     * SSE 流式聊天 - 返回结构化步骤
     * <p>
     * 每次 LLM 回复作为一个步骤返回，包含：
     * - 步骤序号
     * - 思考内容
     * - 回复内容
     * - 工具调用及结果
     */
    @PostMapping(value = "/chat/stream/steps", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChatSteps(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        executorService.execute(() -> {
            try {
                InboundMessage message = new InboundMessage("web", "user", "direct", request.getMessage());
                String sessionKey = request.getSessionKey() != null ? request.getSessionKey() : "web:direct";

                // 步骤回调 - 每次 LLM 回复时触发
                // 流式回调 - 每个 token 发送
                agentLoop.processMessage(message, sessionKey, chunk -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("token")
                                .data(objectMapper.writeValueAsString(new TokenEvent(chunk))));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                }, step -> {
                    try {
                        // 发送步骤结束事件
                        emitter.send(SseEmitter.event()
                                .name("step")
                                .data(objectMapper.writeValueAsString(StepEvent.stepEnd(step.getStepNumber(), step))));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                });

                // 发送完成事件
                emitter.send(SseEmitter.event().name("done").data(""));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        executorService.execute(() -> {
            try {
                InboundMessage message = new InboundMessage("web", "user", "direct", request.getMessage());

                OutboundMessage response = agentLoop.processMessage(
                        message,
                        request.getSessionKey() != null ? request.getSessionKey() : "web:direct",
                        chunk -> {
                            try {
                                emitter.send(SseEmitter.event().data(chunk));
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        }
                );

                emitter.send(SseEmitter.event().data("[DONE]"));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    public static class ChatRequest {
        private String message;
        private String sessionKey;

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getSessionKey() { return sessionKey; }
        public void setSessionKey(String sessionKey) { this.sessionKey = sessionKey; }
    }
}
