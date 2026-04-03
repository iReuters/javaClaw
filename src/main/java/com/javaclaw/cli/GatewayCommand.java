package com.javaclaw.cli;

import com.javaclaw.agent.AgentLoop;
import com.javaclaw.bus.MessageBus;
import com.javaclaw.bus.OutboundMessage;
import com.javaclaw.channels.ChannelManager;
import com.javaclaw.config.Config;
import com.javaclaw.config.ConfigLoader;
import com.javaclaw.cron.CronServiceImpl;
import com.javaclaw.heartbeat.HeartbeatService;
import com.javaclaw.providers.ProviderFactory;
import com.javaclaw.session.SessionManager;
import picocli.CommandLine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * gateway 子命令：加载配置、创建 MessageBus/Provider/SessionManager/CronService/AgentLoop/Heartbeat/ChannelManager，
 * 使用 ExecutorService 并发运行 agent.run()、dispatchOutbound()、钉钉 channel.start()、cron.start()、heartbeat.start()。
 */
@CommandLine.Command(name = "gateway", description = "启动 Gateway（钉钉 + Agent）")
public class GatewayCommand implements Runnable {

    @Override
    public void run() {
        Config config = ConfigLoader.loadConfig();
        MessageBus bus = new MessageBus();
        SessionManager sessionManager = new SessionManager(ConfigLoader.getSessionsDir());
        CronServiceImpl cronService = new CronServiceImpl(ConfigLoader.getDataDir());
        AgentLoop agent = new AgentLoop(
                bus,
                ProviderFactory.fromConfig(config),
                config.getWorkspacePath(),
                config.getAgents().getModel(),
                config.getAgents().getMaxToolIterations(),
                config.getAgents().getTemperature(),
                config.getAgents().getMaxTokens(),
                config.getAgents().getMemoryWindow(),
                config.getTools().getWebSearchApiKey(),
                config.getTools().getExec(),
                cronService,
                config.getTools().isRestrictToWorkspace(),
                sessionManager,
                config.getTools().getMcpServers());
        cronService.setOnJob(job -> {
            String msg = job.getPayload() != null ? job.getPayload().getMessage() : "";
            String sk = "cron:" + job.getId();
            String ch = job.getPayload() != null ? job.getPayload().getChannel() : "cli";
            String cid = job.getPayload() != null ? job.getPayload().getChatId() : "direct";
            String response = agent.processDirect(msg, sk, ch, cid);
            if (job.getPayload() != null && job.getPayload().isDeliver()
                    && job.getPayload().getChannel() != null && job.getPayload().getChatId() != null) {
                bus.publishOutbound(new OutboundMessage(
                        job.getPayload().getChannel(), job.getPayload().getChatId(), response));
            }
        });
        HeartbeatService heartbeat = new HeartbeatService(agent::processDirect);
        ChannelManager channelManager = new ChannelManager(config, bus);

        ExecutorService executor = Executors.newCachedThreadPool();
        cronService.start();
        heartbeat.start();
        executor.submit(agent::run);
        channelManager.startAll();

        // 启动 HTTP 服务器，用于前端交互
        try {
            HttpServer httpServer = HttpServer.create(new java.net.InetSocketAddress(config.getGateway().getHost(), config.getGateway().getPort()), 0);
            httpServer.createContext("/api/chat", new ChatHandler(agent));
            httpServer.createContext("/web", new StaticFileHandler());
            httpServer.setExecutor(executor);
            httpServer.start();
            System.out.println("HTTP server started on " + config.getGateway().getHost() + ":" + config.getGateway().getPort());
            System.out.println("Frontend available at http://" + config.getGateway().getHost() + ":" + config.getGateway().getPort() + "/web/index.html");
        } catch (IOException e) {
            System.err.println("Failed to start HTTP server: " + e.getMessage());
        }

        System.out.println("javaClaw gateway started. Press Ctrl+C to stop.");
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        agent.stop();
        bus.stop();
        channelManager.stop();
        cronService.stop();
        heartbeat.stop();
        agent.closeMcp();
        executor.shutdown();
    }

    /**
     * 处理前端聊天请求的 HTTP 处理器
     */
    private static class ChatHandler implements HttpHandler {
        private final AgentLoop agent;
        private final ObjectMapper mapper = new ObjectMapper();

        public ChatHandler(AgentLoop agent) {
            this.agent = agent;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                // 读取请求体
                InputStream is = exchange.getRequestBody();
                StringBuilder sb = new StringBuilder();
                int b;
                while ((b = is.read()) != -1) {
                    sb.append((char) b);
                }
                String requestBody = sb.toString();
                is.close();

                try {
                    // 解析请求体
                    ChatRequest request = mapper.readValue(requestBody, ChatRequest.class);
                    String message = request.getMessage();
                    String sessionId = request.getSessionId() != null ? request.getSessionId() : "web:direct";

                    // 处理消息
                    String response = agent.processDirect(message, sessionId, "web", "direct");

                    // 构建响应
                    ChatResponse chatResponse = new ChatResponse();
                    chatResponse.setResponse(response);
                    chatResponse.setSessionId(sessionId);

                    // 发送响应
                    String responseBody = mapper.writeValueAsString(chatResponse);
                    exchange.sendResponseHeaders(200, responseBody.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(responseBody.getBytes(StandardCharsets.UTF_8));
                    os.close();
                } catch (Exception e) {
                    String errorResponse = mapper.writeValueAsString(new ErrorResponse("Error processing message: " + e.getMessage()));
                    exchange.sendResponseHeaders(500, errorResponse.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(errorResponse.getBytes(StandardCharsets.UTF_8));
                    os.close();
                }
            } else {
                exchange.sendResponseHeaders(405, 0);
                exchange.getResponseBody().close();
            }
        }
    }

    /**
     * 聊天请求类
     */
    private static class ChatRequest {
        private String message;
        private String sessionId;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }
    }

    /**
     * 聊天响应类
     */
    private static class ChatResponse {
        private String response;
        private String sessionId;

        public String getResponse() {
            return response;
        }

        public void setResponse(String response) {
            this.response = response;
        }

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }
    }

    /**
     * 错误响应类
     */
    private static class ErrorResponse {
        private String error;

        public ErrorResponse(String error) {
            this.error = error;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }

    /**
     * 处理静态文件请求的 HTTP 处理器
     */
    private static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            // 移除 /web 前缀
            String filePath = path.substring(4);
            if (filePath.isEmpty() || filePath.equals("/")) {
                filePath = "/index.html";
            }

            // 构建文件路径
            java.nio.file.Path file = java.nio.file.Paths.get(System.getProperty("user.dir"), "web", filePath);

            // 检查文件是否存在
            if (java.nio.file.Files.exists(file) && !java.nio.file.Files.isDirectory(file)) {
                // 读取文件内容
                byte[] fileContent = java.nio.file.Files.readAllBytes(file);

                // 设置响应头
                String contentType = getContentType(filePath);
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, fileContent.length);

                // 发送文件内容
                OutputStream os = exchange.getResponseBody();
                os.write(fileContent);
                os.close();
            } else {
                // 文件不存在
                String errorMessage = "File not found: " + path;
                exchange.sendResponseHeaders(404, errorMessage.length());
                OutputStream os = exchange.getResponseBody();
                os.write(errorMessage.getBytes(StandardCharsets.UTF_8));
                os.close();
            }
        }

        /**
         * 根据文件扩展名获取内容类型
         */
        private String getContentType(String filePath) {
            if (filePath.endsWith(".html")) {
                return "text/html; charset=utf-8";
            } else if (filePath.endsWith(".css")) {
                return "text/css";
            } else if (filePath.endsWith(".js")) {
                return "application/javascript";
            } else if (filePath.endsWith(".png")) {
                return "image/png";
            } else if (filePath.endsWith(".jpg") || filePath.endsWith(".jpeg")) {
                return "image/jpeg";
            } else if (filePath.endsWith(".gif")) {
                return "image/gif";
            } else {
                return "application/octet-stream";
            }
        }
    }
}
