package com.sjhy.plugin.mcp;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.sjhy.plugin.service.SettingsStorageService;
import com.sjhy.plugin.tool.JSON;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * EasyCode MCP 服务（应用级服务）
 * <p>
 * 内嵌 JDK 自带 HttpServer，绑定 127.0.0.1:28731，单端点 POST /mcp 提供 MCP 协议服务
 * （Streamable HTTP，始终以 application/json 单对象响应）。
 * <p>
 * 安全：仅绑定回环地址；校验 Origin 头（存在且非 localhost 系则 403，防 DNS rebinding）。
 * 端口被占用（多 IDE 实例）时记日志并保持惰性，不影响 IDE 启动。
 *
 * @author makejava
 * @version 1.0.0
 * @since 2024/01/01
 */
public class McpServerService implements Disposable {

    private static final Logger LOG = Logger.getInstance(McpServerService.class);

    /**
     * MCP 服务默认端口（可在设置中修改）
     */
    public static final int DEFAULT_PORT = 28731;

    /**
     * MCP 端点路径
     */
    public static final String PATH = "/mcp";

    /**
     * 当前监听端口（未启动时为默认值）
     */
    private int port = DEFAULT_PORT;

    /**
     * HTTP 服务实例（null 表示未启动，如端口被占用）
     */
    private HttpServer server;

    /**
     * 请求处理线程池
     */
    private ExecutorService executor;

    /**
     * 协议处理器
     */
    private McpProtocolHandler protocolHandler;

    /**
     * 构造即启动（应用级服务由 McpStartupActivity 触发实例化）
     */
    public McpServerService() {
        start();
    }

    /**
     * 启动 HTTP 服务（幂等；受设置开关控制）
     */
    private synchronized void start() {
        if (server != null) {
            return;
        }
        if (!isMcpEnabled()) {
            LOG.info("EasyCode MCP server disabled by settings, not started.");
            return;
        }
        try {
            this.port = resolvePort();
            protocolHandler = new McpProtocolHandler(new McpToolRegistry());
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            executor = Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "easycode-mcp");
                thread.setDaemon(true);
                return thread;
            });
            server.setExecutor(executor);
            server.createContext(PATH, this::handleExchange);
            server.start();
            LOG.info("EasyCode MCP server started at http://127.0.0.1:" + port + PATH);
        } catch (BindException e) {
            // 端口被占用（多 IDE 实例），降级为不启动，不影响 IDE
            LOG.warn("EasyCode MCP server port " + port + " is occupied (another IDE instance may own it), server not started.");
            server = null;
            closeExecutor();
        } catch (IOException e) {
            LOG.error("Failed to start EasyCode MCP server", e);
            server = null;
            closeExecutor();
        }
    }

    /**
     * 重启服务（设置变更后调用：应用开关与端口）
     */
    public synchronized void restart() {
        stopInternal();
        start();
    }

    /**
     * MCP 服务是否启用（旧配置无此字段时视为启用）
     *
     * @return 是否启用
     */
    public static boolean isMcpEnabled() {
        Boolean enabled = SettingsStorageService.getSettingsStorage().getMcpEnabled();
        return enabled == null || enabled;
    }

    /**
     * 解析 MCP 服务端口（非法/缺省时回退默认端口）
     *
     * @return 端口
     */
    public static int resolvePort() {
        Integer configPort = SettingsStorageService.getSettingsStorage().getMcpPort();
        if (configPort == null || configPort < 1 || configPort > 65535) {
            return DEFAULT_PORT;
        }
        return configPort;
    }

    /**
     * 获取当前监听端口
     *
     * @return 端口
     */
    public synchronized int getPort() {
        return port;
    }

    /**
     * 处理一次 HTTP 交互
     *
     * @param exchange HTTP 交换对象
     */
    private void handleExchange(HttpExchange exchange) {
        try {
            // Origin 校验：存在且非 localhost 系则 403（防 DNS rebinding）
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            if (origin != null && !isLocalOrigin(origin)) {
                sendJson(exchange, 403, errorMap(null, McpProtocolHandler.CODE_INVALID_REQUEST, "Forbidden origin: " + origin));
                return;
            }
            String method = exchange.getRequestMethod();
            if ("POST".equals(method)) {
                handlePost(exchange);
            } else {
                // 不提供 GET（server→client SSE 流），统一 405
                sendJson(exchange, 405, errorMap(null, McpProtocolHandler.CODE_METHOD_NOT_FOUND, "Method " + method + " not allowed, use POST " + PATH));
            }
        } catch (Exception e) {
            LOG.error("EasyCode MCP server handle error", e);
            try {
                sendJson(exchange, 500, errorMap(null, McpProtocolHandler.CODE_INTERNAL_ERROR, "Internal error: " + e.getMessage()));
            } catch (Exception ignored) {
                // 连接已断开等场景，忽略
            }
        } finally {
            exchange.close();
        }
    }

    /**
     * 处理 POST 请求
     *
     * @param exchange HTTP 交换对象
     * @throws IOException IO 异常
     */
    private void handlePost(HttpExchange exchange) throws IOException {
        String body = readBody(exchange.getRequestBody());
        if (body.trim().isEmpty()) {
            sendJson(exchange, 400, errorMap(null, McpProtocolHandler.CODE_PARSE_ERROR, "Empty request body"));
            return;
        }
        Object parsed;
        try {
            parsed = JSON.getInstance().readValue(body, Object.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // JSON 解析失败（readValue 直接抛 Jackson 异常，非 IllegalArgumentException）
            sendJson(exchange, 400, errorMap(null, McpProtocolHandler.CODE_PARSE_ERROR, "Parse error: invalid JSON"));
            return;
        }
        // 不支持批量消息
        if (parsed instanceof List) {
            @SuppressWarnings("unchecked") List<Object> list = (List<Object>) parsed;
            sendJson(exchange, 400, errorMap(null, McpProtocolHandler.CODE_INVALID_REQUEST,
                    "Batch messages are not supported, got " + list.size() + " items"));
            return;
        }
        if (!(parsed instanceof Map)) {
            sendJson(exchange, 400, errorMap(null, McpProtocolHandler.CODE_INVALID_REQUEST, "Invalid Request"));
            return;
        }
        @SuppressWarnings("unchecked") Map<String, Object> message = (Map<String, Object>) parsed;
        Map<String, Object> response = protocolHandler.handle(message);
        if (response == null) {
            // 通知类消息：202 无 body
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(202, -1);
            return;
        }
        sendJson(exchange, 200, response);
    }

    /**
     * 发送 JSON 响应
     *
     * @param exchange HTTP 交换对象
     * @param status   HTTP 状态码
     * @param body     响应对象
     * @throws IOException IO 异常
     */
    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = JSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * 读取请求体
     *
     * @param inputStream 输入流
     * @return UTF-8 文本
     * @throws IOException IO 异常
     */
    private String readBody(InputStream inputStream) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] bufferBytes = new byte[4096];
        int length;
        while ((length = inputStream.read(bufferBytes)) != -1) {
            buffer.write(bufferBytes, 0, length);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * 判断 Origin 是否为本机来源
     *
     * @param origin Origin 头
     * @return 是否本机
     */
    private boolean isLocalOrigin(String origin) {
        return origin.startsWith("http://127.0.0.1")
                || origin.startsWith("http://localhost")
                || origin.startsWith("https://127.0.0.1")
                || origin.startsWith("https://localhost");
    }

    /**
     * 构建 JSON-RPC 错误响应 Map
     *
     * @param id      请求 id
     * @param code    错误码
     * @param message 错误信息
     * @return 响应 Map
     */
    private Map<String, Object> errorMap(Object id, int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id);
        resp.put("error", error);
        return resp;
    }

    /**
     * 关闭线程池
     */
    private void closeExecutor() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    /**
     * 服务是否已启动
     *
     * @return 是否启动
     */
    public synchronized boolean isStarted() {
        return server != null;
    }

    @Override
    public synchronized void dispose() {
        stopInternal();
        LOG.info("EasyCode MCP server stopped.");
    }

    /**
     * 停止服务并释放线程池
     */
    private synchronized void stopInternal() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        closeExecutor();
    }
}
