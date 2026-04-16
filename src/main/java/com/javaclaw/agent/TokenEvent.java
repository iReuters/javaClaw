package com.javaclaw.agent;

/**
 * SSE Token 事件
 * <p>
 * 用于 SSE 流式传输时，逐 token 发送内容实现打字机效果
 */
public class TokenEvent {

    private String content;

    public TokenEvent() {}

    public TokenEvent(String content) {
        this.content = content;
    }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}