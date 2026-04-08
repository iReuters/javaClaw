package com.javaclaw.controller;

import com.javaclaw.agent.AgentLoop;
import com.javaclaw.bus.InboundMessage;
import com.javaclaw.bus.OutboundMessage;
import com.javaclaw.config.Config;
import com.javaclaw.providers.LLMProvider;
import com.javaclaw.session.SessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")  // 允许所有来源
public class ChatController {
    private final AgentLoop agentLoop;
    private final ExecutorService executorService;

    @Autowired
    public ChatController(AgentLoop agentLoop) {
        this.agentLoop = agentLoop;
        this.executorService = Executors.newFixedThreadPool(10);
    }

    @PostMapping("/chat")
    public OutboundMessage chat(@RequestBody ChatRequest request) {
        // 创建入站消息
        InboundMessage message = new InboundMessage("web", "user", "direct", request.getMessage());
        
        // 处理消息
        return agentLoop.processMessage(message, request.getSessionKey() != null ? request.getSessionKey() : "web:direct", null);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter();
        
        executorService.execute(() -> {
            try {
                // 创建入站消息
                InboundMessage message = new InboundMessage("web", "user", "direct", request.getMessage());
                
                // 处理消息，使用流式响应
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
                
                // 发送完成事件
                emitter.send(SseEmitter.event().data("[DONE]"));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        
        return emitter;
    }

    // 聊天请求模型
    public static class ChatRequest {
        private String message;
        private String sessionKey;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getSessionKey() {
            return sessionKey;
        }

        public void setSessionKey(String sessionKey) {
            this.sessionKey = sessionKey;
        }
    }
}
