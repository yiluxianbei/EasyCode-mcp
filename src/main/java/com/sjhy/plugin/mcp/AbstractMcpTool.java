package com.sjhy.plugin.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具抽象基类
 * <p>
 * 提供 JSON Schema 构建的静态辅助方法（类的静态方法可被子类以简单名直接调用，
 * 接口静态方法则不行）。
 *
 * @author makejava
 * @version 1.0.0
 * @since 2024/01/01
 */
public abstract class AbstractMcpTool implements McpTool {

    /**
     * 构建对象类型的 JSON Schema
     *
     * @param properties 属性定义
     * @param required   必填属性名列表（可为 null）
     * @return JSON Schema Map
     */
    protected static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        return McpTool.schema(properties, required);
    }

    /**
     * 构建字符串属性定义
     *
     * @param description 属性描述
     * @return 属性定义
     */
    protected static Map<String, Object> stringProp(String description) {
        return McpTool.stringProp(description);
    }

    /**
     * 构建布尔属性定义
     *
     * @param description 属性描述
     * @return 属性定义
     */
    protected static Map<String, Object> booleanProp(String description) {
        return McpTool.booleanProp(description);
    }

    /**
     * 构建整数属性定义
     *
     * @param description 属性描述
     * @return 属性定义
     */
    protected static Map<String, Object> intProp(String description) {
        return McpTool.intProp(description);
    }

    /**
     * 构建字符串枚举属性定义
     *
     * @param description 属性描述
     * @param values      枚举取值
     * @return 属性定义
     */
    protected static Map<String, Object> enumProp(String description, String... values) {
        return McpTool.enumProp(description, values);
    }

    /**
     * 构建对象属性定义（用于嵌套结构）
     *
     * @param properties  属性定义
     * @param required    必填属性名列表（可为 null）
     * @param description 属性描述（可为 null）
     * @return 属性定义
     */
    protected static Map<String, Object> objectProp(Map<String, Object> properties, List<String> required, String description) {
        return McpTool.objectProp(properties, required, description);
    }

    /**
     * 构建数组属性定义
     *
     * @param items       元素定义
     * @param description 属性描述
     * @return 属性定义
     */
    protected static Map<String, Object> arrayProp(Map<String, Object> items, String description) {
        return McpTool.arrayProp(items, description);
    }

    /**
     * 构建字符串数组属性定义
     *
     * @param description 属性描述
     * @return 属性定义
     */
    protected static Map<String, Object> stringArrayProp(String description) {
        return McpTool.stringArrayProp(description);
    }

    /**
     * 构建字符串列表
     *
     * @param values 值列表
     * @return 字符串列表
     */
    protected static List<String> list(String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            result.add(value);
        }
        return result;
    }

    /**
     * 构建有序 Map
     *
     * @return 空 LinkedHashMap
     */
    protected static Map<String, Object> map() {
        return new LinkedHashMap<>();
    }
}
