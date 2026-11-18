package com.sjhy.plugin.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具接口
 * <p>
 * 每个 MCP 工具提供名称、描述、入参 JSON Schema 与执行逻辑。执行返回结构化结果（Map/List），
 * 由协议层包装为 tools/call 响应（text content + structuredContent）。
 *
 * @author makejava
 * @version 1.0.0
 * @since 2024/01/01
 */
public interface McpTool {

    /**
     * 工具名（MCP tools/list 与 tools/call 的 name）
     *
     * @return 工具名
     */
    String getName();

    /**
     * 工具描述（供 AI agent 理解工具用途）
     *
     * @return 工具描述
     */
    String getDescription();

    /**
     * 入参 JSON Schema（Map 形式，直接序列化为 JSON）
     *
     * @return JSON Schema
     */
    Map<String, Object> getInputSchema();

    /**
     * 出参 JSON Schema（Map 形式，直接序列化为 JSON）
     * <p>
     * 对应 MCP 规范 Tool 对象的 outputSchema 字段。返回 null 表示不声明（保持兼容），
     * 声明后 tools/call 成功响应必须携带符合该 schema 的 structuredContent（本协议层已保证），
     * isError 响应不得携带 structuredContent（本协议层已保证）。
     * <p>
     * 顶层必须为 type=object（MCP 规范要求 structuredContent 为 object）。
     *
     * @return JSON Schema，null 表示不声明出参结构
     */
    default Map<String, Object> getOutputSchema() {
        return null;
    }

    /**
     * 执行工具
     *
     * @param args 入参（不会为 null，缺省为空 Map）
     * @return 结构化结果（Map/List），由协议层包装为 text content + structuredContent
     * @throws Exception 执行失败，异常信息将作为 isError 响应返回给 agent
     */
    Object call(Map<String, Object> args) throws Exception;

    /**
     * 构建对象类型的 JSON Schema
     *
     * @param properties 属性定义
     * @param required   必填属性名列表（可为 null）
     * @return JSON Schema Map
     */
    static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        if (properties != null && !properties.isEmpty()) {
            schema.put("properties", properties);
        }
        if (required != null && !required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    /**
     * 构建字符串属性定义
     *
     * @param description 属性描述
     * @return 属性定义
     */
    static Map<String, Object> stringProp(String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "string");
        prop.put("description", description);
        return prop;
    }

    /**
     * 构建布尔属性定义
     *
     * @param description 属性描述
     * @return 属性定义
     */
    static Map<String, Object> booleanProp(String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "boolean");
        prop.put("description", description);
        return prop;
    }

    /**
     * 构建整数属性定义
     *
     * @param description 属性描述
     * @return 属性定义
     */
    static Map<String, Object> intProp(String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "integer");
        prop.put("description", description);
        return prop;
    }

    /**
     * 构建字符串枚举属性定义
     *
     * @param description 属性描述
     * @param values      枚举取值
     * @return 属性定义
     */
    static Map<String, Object> enumProp(String description, String... values) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "string");
        prop.put("enum", list(values));
        prop.put("description", description);
        return prop;
    }

    /**
     * 构建对象属性定义（用于嵌套结构）
     *
     * @param properties  属性定义
     * @param required    必填属性名列表（可为 null）
     * @param description 属性描述
     * @return 属性定义
     */
    static Map<String, Object> objectProp(Map<String, Object> properties, List<String> required, String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "object");
        if (description != null) {
            prop.put("description", description);
        }
        if (properties != null && !properties.isEmpty()) {
            prop.put("properties", properties);
        }
        if (required != null && !required.isEmpty()) {
            prop.put("required", required);
        }
        return prop;
    }

    /**
     * 构建数组属性定义
     *
     * @param items       元素定义
     * @param description 属性描述
     * @return 属性定义
     */
    static Map<String, Object> arrayProp(Map<String, Object> items, String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "array");
        prop.put("description", description);
        if (items != null) {
            prop.put("items", items);
        }
        return prop;
    }

    /**
     * 构建字符串数组属性定义
     *
     * @param description 属性描述
     * @return 属性定义
     */
    static Map<String, Object> stringArrayProp(String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "array");
        prop.put("description", description);
        Map<String, Object> items = new LinkedHashMap<>();
        items.put("type", "string");
        prop.put("items", items);
        return prop;
    }

    /**
     * 构建字符串列表快捷方法
     *
     * @param values 值列表
     * @return 字符串列表
     */
    static List<String> list(String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            result.add(value);
        }
        return result;
    }
}
