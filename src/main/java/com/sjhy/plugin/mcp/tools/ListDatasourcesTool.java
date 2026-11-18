package com.sjhy.plugin.mcp.tools;

import com.sjhy.plugin.mcp.AbstractMcpTool;
import com.sjhy.plugin.mcp.McpThreading;
import com.sjhy.plugin.mcp.McpTool;
import com.sjhy.plugin.mcp.TableResolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具：列出项目数据源
 *
 * @author makejava
 * @version 1.0.0
 * @since 2024/01/01
 */
public class ListDatasourcesTool extends AbstractMcpTool {

    @Override
    public String getName() {
        return "list_datasources";
    }

    @Override
    public String getDescription() {
        return "【仅用于代码生成场景】列出当前 IDEA 项目中已配置的数据库数据源，"
                + "供 preview_code / generate_code 传递 datasource 参数使用（list_tables / get_table_detail 也复用这里返回的 name）。"
                + "查询数据、执行 SQL、探索数据库等通用数据库操作，不要用本工具。"
                + "dbms 取不到时为空串；datasources 为空时会附带 hint 提示先在 Database 面板添加数据源。"
                + "执行出错时返回 isError 响应，text 为 {\"error\": 原因}。";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return schema(null, null);
    }

    @Override
    public Map<String, Object> getOutputSchema() {
        Map<String, Object> datasourceItem = objectProp(new LinkedHashMap<String, Object>() {{
            put("name", stringProp("数据源名称"));
            put("dbms", stringProp("数据库类型显示名，取不到时为空串"));
        }}, list("name", "dbms"), null);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("datasources", arrayProp(datasourceItem, "数据源列表，可能为空数组"));
        properties.put("hint", stringProp("空结果提示文案（仅 datasources 为空时存在）"));
        return schema(properties, list("datasources"));
    }

    @Override
    public Object call(Map<String, Object> args) {
        List<Map<String, Object>> datasources = McpThreading.read(TableResolver::listDatasources);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasources", datasources);
        if (datasources.isEmpty()) {
            result.put("hint", "项目中没有已配置的数据源，请先在 Database 面板添加数据源");
        }
        return result;
    }
}
