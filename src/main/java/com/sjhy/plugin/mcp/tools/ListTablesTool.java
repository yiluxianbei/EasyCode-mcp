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
 * MCP 工具：列出数据源下的表
 *
 * @author makejava
 * @version 1.0.0
 * @since 2024/01/01
 */
public class ListTablesTool extends AbstractMcpTool {

    @Override
    public String getName() {
        return "list_tables";
    }

    @Override
    public String getDescription() {
        return "【仅用于代码生成场景】列出可生成代码的数据库表，支持按数据源、schema 与表名关键字过滤，"
                + "供 preview_code / generate_code 选择 tables 参数使用（get_table_detail 的 table 参数也用这里返回的 name）。"
                + "浏览数据库、查询数据等通用数据库操作，不要用本工具。"
                + "comment 无注释或取不到时为空串；结果为空时会附带 hint 提示（多为数据源未完成内省或过滤条件过严）。"
                + "执行出错时返回 isError 响应，text 为 {\"error\": 原因}。";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("datasource", stringProp("数据源名称（可选，不传则查询全部数据源）"));
        properties.put("schema", stringProp("schema 名称过滤（可选）"));
        properties.put("keyword", stringProp("表名包含关键字过滤（可选，不区分大小写）"));
        return schema(properties, null);
    }

    @Override
    public Object call(Map<String, Object> args) {
        String datasource = str(args.get("datasource"));
        String schema = str(args.get("schema"));
        String keyword = str(args.get("keyword"));
        List<TableResolver.TableRef> refs = McpThreading.read(() -> TableResolver.listTables(datasource, schema, keyword));

        List<Map<String, Object>> tables = new ArrayList<>();
        for (TableResolver.TableRef ref : refs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("datasource", ref.getDatasource());
            item.put("schema", ref.getSchema());
            item.put("name", ref.getName());
            item.put("comment", tableComment(ref));
            tables.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", tables.size());
        result.put("tables", tables);
        if (tables.isEmpty()) {
            result.put("hint", "未找到任何表：请确认数据源已在 Database 面板展开/刷新完成内省，或调整 datasource/schema/keyword 过滤条件");
        }
        return result;
    }

    @Override
    public Map<String, Object> getOutputSchema() {
        Map<String, Object> tableItem = objectProp(new LinkedHashMap<String, Object>() {{
            put("datasource", stringProp("所属数据源名"));
            put("schema", stringProp("所属 schema 名"));
            put("name", stringProp("数据库表名"));
            put("comment", stringProp("表注释，无注释或取不到时为空串"));
        }}, list("datasource", "schema", "name", "comment"), null);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("count", intProp("过滤后命中的表数量"));
        properties.put("tables", arrayProp(tableItem, "表列表，可能为空数组"));
        properties.put("hint", stringProp("空结果提示文案（仅 tables 为空时存在）"));
        return schema(properties, list("count", "tables"));
    }

    /**
     * 转字符串（null 透传）
     *
     * @param value 值
     * @return 字符串
     */
    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 读取表注释（无注释或读取失败时返回空串，与 description 声明一致）
     *
     * @param ref 表引用
     * @return 表注释
     */
    private String tableComment(TableResolver.TableRef ref) {
        try {
            String comment = ref.getDbTable().getComment();
            return comment == null ? "" : comment;
        } catch (Exception e) {
            return "";
        }
    }
}
