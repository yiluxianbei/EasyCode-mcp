package com.sjhy.plugin.mcp;

import com.sjhy.plugin.tool.JSON;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 协议处理器（JSON-RPC 2.0 分发）
 * <p>
 * 协议基线：仅支持 2025-11-25 单版本（initialize 握手时代最新版），不做旧版本兼容。
 * 支持方法：initialize / notifications/initialized / ping / tools/list / tools/call。
 *
 * @author makejava
 * @version 1.0.0
 * @since 2024/01/01
 */
public class McpProtocolHandler {

    /**
     * 支持的协议版本（唯一）
     */
    public static final String PROTOCOL_VERSION = "2025-11-25";

    /**
     * 服务端名称
     */
    public static final String SERVER_NAME = "easycode";

    /**
     * 服务端版本
     */
    public static final String SERVER_VERSION = "1.0.0";

    /**
     * JSON-RPC 错误码
     */
    public static final int CODE_PARSE_ERROR = -32700;
    public static final int CODE_INVALID_REQUEST = -32600;
    public static final int CODE_METHOD_NOT_FOUND = -32601;
    public static final int CODE_INVALID_PARAMS = -32602;
    public static final int CODE_INTERNAL_ERROR = -32603;

    /**
     * 工具注册表
     */
    private final McpToolRegistry registry;

    public McpProtocolHandler(McpToolRegistry registry) {
        this.registry = registry;
    }

    /**
     * 处理一条 JSON-RPC 消息
     *
     * @param message 已解析的消息对象
     * @return 响应 Map（jsonrpc 结构）；通知类消息返回 null（调用方应回 HTTP 202）
     */
    public Map<String, Object> handle(Map<String, Object> message) {
        if (message == null || !"2.0".equals(message.get("jsonrpc")) || message.get("method") == null) {
            return errorResponse(idOf(message), CODE_INVALID_REQUEST, "Invalid Request");
        }
        String method = String.valueOf(message.get("method"));
        Object id = message.get("id");
        boolean notification = (id == null);
        Map<String, Object> params = paramsOf(message);
        try {
            switch (method) {
                case "notifications/initialized":
                    // 通知，接受即可
                    return null;
                case "initialize":
                    return notification ? null : response(id, initializeResult());
                case "ping":
                    return notification ? null : response(id, new LinkedHashMap<>());
                case "tools/list":
                    return notification ? null : response(id, toolsListResult());
                case "tools/call":
                    if (notification) {
                        return null;
                    }
                    return response(id, toolsCallResult(params));
                default:
                    return notification ? null : errorResponse(id, CODE_METHOD_NOT_FOUND, "Method not found: " + method);
            }
        } catch (IllegalArgumentException e) {
            // 参数校验失败
            return notification ? null : errorResponse(id, CODE_INVALID_PARAMS, e.getMessage());
        } catch (Exception e) {
            return notification ? null : errorResponse(id, CODE_INTERNAL_ERROR, "Internal error: " + e.getMessage());
        }
    }

    /**
     * 构建 initialize 响应结果
     * <p>
     * 无论客户端请求什么版本，一律返回 2025-11-25（不支持更旧版本握手）。
     *
     * @return initialize result
     */
    private Map<String, Object> initializeResult() {
        Map<String, Object> toolsCapability = new LinkedHashMap<>();
        toolsCapability.put("listChanged", false);
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools", toolsCapability);

        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", PROTOCOL_VERSION);
        result.put("capabilities", capabilities);
        result.put("serverInfo", serverInfo);
        result.put("instructions", "EasyCode code generation service. Discover database tables and templates, preview rendered code, then generate code files.");
        return result;
    }

    /**
     * 构建 tools/list 响应结果
     *
     * @return tools/list result
     */
    private Map<String, Object> toolsListResult() {
        List<Map<String, Object>> toolList = new ArrayList<>();
        for (McpTool tool : registry.getTools()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", tool.getName());
            item.put("description", tool.getDescription());
            item.put("inputSchema", tool.getInputSchema());
            // 声明了出参结构的工具附带 outputSchema（声明后成功响应必带 structuredContent，本协议层已保证）
            Map<String, Object> outputSchema = tool.getOutputSchema();
            if (outputSchema != null) {
                item.put("outputSchema", outputSchema);
            }
            toolList.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tools", toolList);
        return result;
    }

    /**
     * 处理 tools/call
     *
     * @param params 请求参数（name + arguments）
     * @return tools/call result（content/isError，结构化结果附 structuredContent）
     */
    private Map<String, Object> toolsCallResult(Map<String, Object> params) {
        Object nameObj = params.get("name");
        if (!(nameObj instanceof String) || ((String) nameObj).isEmpty()) {
            throw new IllegalArgumentException("tools/call requires a string 'name' of the tool");
        }
        McpTool tool = registry.getTool((String) nameObj);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown tool: " + nameObj);
        }
        Object argumentsObj = params.get("arguments");
        Map<String, Object> args = argumentsObj instanceof Map ? castMap(argumentsObj) : new HashMap<>();

        Object result;
        boolean isError;
        try {
            result = tool.call(args);
            isError = false;
        } catch (Exception e) {
            // 工具执行失败按 MCP 规范放入 result.isError，而非 JSON-RPC error
            String msg = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
            Map<String, Object> errorResult = new LinkedHashMap<>();
            errorResult.put("error", msg);
            result = errorResult;
            isError = true;
        }

        // text content（结构化结果的 JSON 文本，保证所有客户端可读）
        String text = JSON.toJson(result == null ? "null" : result);
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "text");
        content.put("text", text);

        Map<String, Object> callResult = new LinkedHashMap<>();
        callResult.put("content", new ArrayList<Map<String, Object>>(singletonList(content)));
        // structuredContent：Map 直接用，非 Map 包一层，保证为 object 类型
        if (!isError && result != null) {
            callResult.put("structuredContent", result instanceof Map ? result : singletonMap("result", result));
        }
        callResult.put("isError", isError);
        return callResult;
    }

    /**
     * 构建成功响应
     *
     * @param id     请求 id
     * @param result 结果
     * @return 响应 Map
     */
    private Map<String, Object> response(Object id, Map<String, Object> result) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id);
        resp.put("result", result);
        return resp;
    }

    /**
     * 构建错误响应
     *
     * @param id      请求 id（可能为 null）
     * @param code    错误码
     * @param message 错误信息
     * @return 响应 Map
     */
    private Map<String, Object> errorResponse(Object id, int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message == null ? "" : message);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id);
        resp.put("error", error);
        return resp;
    }

    /**
     * 提取请求 id（用于结构非法时的错误响应）
     *
     * @param message 消息
     * @return id 或 null
     */
    private Object idOf(Map<String, Object> message) {
        return message == null ? null : message.get("id");
    }

    /**
     * 提取 params
     *
     * @param message 消息
     * @return params Map（缺省空 Map）
     */
    private Map<String, Object> paramsOf(Map<String, Object> message) {
        Object params = message.get("params");
        return params instanceof Map ? castMap(params) : new HashMap<>();
    }

    /**
     * 无警告的 Map 强转
     *
     * @param obj 原始对象
     * @return Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object obj) {
        return (Map<String, Object>) obj;
    }

    /**
     * 单元素列表
     *
     * @param value 值
     * @param <T>   类型
     * @return 列表
     */
    private <T> List<T> singletonList(T value) {
        List<T> list = new ArrayList<>(1);
        list.add(value);
        return list;
    }

    /**
     * 单元素 Map
     *
     * @param key   键
     * @param value 值
     * @param <V>   值类型
     * @return Map
     */
    private <V> Map<String, V> singletonMap(String key, V value) {
        Map<String, V> map = new LinkedHashMap<>(1);
        map.put(key, value);
        return map;
    }
}
