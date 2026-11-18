package com.sjhy.plugin.mcp.tools;

import com.intellij.database.model.DasColumn;
import com.sjhy.plugin.entity.ColumnInfo;
import com.sjhy.plugin.entity.TableInfo;
import com.sjhy.plugin.mcp.AbstractMcpTool;
import com.sjhy.plugin.mcp.McpThreading;
import com.sjhy.plugin.mcp.McpTool;
import com.sjhy.plugin.mcp.TableResolver;
import com.sjhy.plugin.service.TableInfoSettingsService;
import com.sjhy.plugin.tool.CompatibleUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP 工具：查询表结构
 *
 * @author makejava
 * @version 1.0.0
 * @since 2024/01/01
 */
public class TableDetailTool extends AbstractMcpTool {

    @Override
    public String getName() {
        return "get_table_detail";
    }

    @Override
    public String getDescription() {
        return "【仅用于代码生成场景】查询单张表的结构详情，用于确定生成目标表、编写或核对 EasyCode 模板与生成的代码。"
                + "列信息提供两套名称与类型："
                + "name / javaType / javaShortType 与模板变量 $column.name 及类型映射结果一致，写模板、核对生成代码时用这套；"
                + "dbColumnName / dbType 为数据库原始名称与类型，写 mapper XML 模板时用这套；"
                + "javaType 与 javaShortType 对照可检查当前类型映射规则。"
                + "查询数据、探索数据库结构等通用数据库操作，不要用本工具。"
                + "自定义附加列（custom=true）无原始数据库对象，其 dbColumnName / dbType 为空串；comment 可能为 null。"
                + "参数校验失败或执行出错时返回 isError 响应，text 为 {\"error\": 原因}。";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("datasource", stringProp("数据源名称（必填，可用 list_datasources 查询）"));
        properties.put("schema", stringProp("schema 名称（可选，同名表存在歧义时建议传入）"));
        properties.put("table", stringProp("表名（必填，注意大小写，可用 list_tables 查询）"));
        return schema(properties, list("datasource", "table"));
    }

    @Override
    public Map<String, Object> getOutputSchema() {
        Map<String, Object> columnItem = objectProp(new LinkedHashMap<String, Object>() {{
            put("name", stringProp("Java 驼峰列名，与模板变量 $column.name 一致"));
            put("dbColumnName", stringProp("数据库原始列名，自定义附加列无原始对象时为空串"));
            put("dbType", stringProp("数据库原始类型，自定义附加列无原始对象时为空串"));
            put("javaType", stringProp("按当前类型映射得到的 Java 全限定类型名"));
            put("javaShortType", stringProp("Java 短类型名"));
            put("comment", stringProp("列注释，可能为 null"));
            put("pk", booleanProp("是否主键"));
            put("custom", booleanProp("是否自定义附加列"));
        }}, list("name", "dbColumnName", "dbType", "javaType", "javaShortType", "pk", "custom"), null);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("datasource", stringProp("所属数据源名"));
        properties.put("schema", stringProp("所属 schema 名，可能为空串"));
        properties.put("tableName", stringProp("数据库原始表名"));
        properties.put("className", stringProp("转换后的 Java 类名（首字母大写）"));
        properties.put("comment", stringProp("表注释，可能为 null"));
        properties.put("columns", arrayProp(columnItem, "列详情列表"));
        return schema(properties, list("datasource", "schema", "tableName", "className", "columns"));
    }

    @Override
    public Object call(Map<String, Object> args) {
        String datasource = str(args.get("datasource"));
        String schema = str(args.get("schema"));
        String table = str(args.get("table"));
        return McpThreading.read(() -> {
            TableResolver.TableRef ref = TableResolver.resolveTable(datasource, schema, table);
            TableInfo tableInfo = TableInfoSettingsService.getInstance().getTableInfo(ref.getDbTable());
            return buildDetail(ref, tableInfo);
        });
    }

    /**
     * 构建表结构详情结果（直接返回 Map，与 outputSchema 声明一致；
     * 不要返回非 Map 对象，否则协议层会包成 {"result": ...} 导致与 outputSchema 不符）
     *
     * @param ref       表引用
     * @param tableInfo 表信息
     * @return 详情结果 Map
     */
    private Map<String, Object> buildDetail(TableResolver.TableRef ref, TableInfo tableInfo) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("datasource", ref.getDatasource());
        dto.put("schema", ref.getSchema());
        dto.put("tableName", ref.getDbTable().getName());
        dto.put("className", tableInfo.getName());
        dto.put("comment", tableInfo.getComment());

        Set<String> pkNames = new HashSet<>();
        if (tableInfo.getPkColumn() != null) {
            for (ColumnInfo columnInfo : tableInfo.getPkColumn()) {
                pkNames.add(columnInfo.getName());
            }
        }
        List<Map<String, Object>> columns = new ArrayList<>();
        if (tableInfo.getFullColumn() != null) {
            for (ColumnInfo columnInfo : tableInfo.getFullColumn()) {
                Map<String, Object> column = new LinkedHashMap<>();
                column.put("name", columnInfo.getName());
                column.put("dbColumnName", rawColumnName(columnInfo));
                column.put("dbType", dbType(columnInfo));
                column.put("javaType", columnInfo.getType());
                column.put("javaShortType", columnInfo.getShortType());
                column.put("comment", columnInfo.getComment());
                column.put("pk", pkNames.contains(columnInfo.getName()));
                column.put("custom", Boolean.TRUE.equals(columnInfo.getCustom()));
                columns.add(column);
            }
        }
        dto.put("columns", columns);
        return dto;
    }

    /**
     * 获取数据库原始列名（自定义附加列无原始对象，返回空串）
     *
     * @param columnInfo 列信息
     * @return 原始列名
     */
    private String rawColumnName(ColumnInfo columnInfo) {
        DasColumn dasColumn = columnInfo.getObj();
        if (dasColumn == null) {
            return "";
        }
        try {
            String name = dasColumn.getName();
            return name == null ? "" : name;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 获取列的数据库原始类型（自定义附加列无原始对象，返回空串）
     *
     * @param columnInfo 列信息
     * @return 数据库类型
     */
    private String dbType(ColumnInfo columnInfo) {
        DasColumn dasColumn = columnInfo.getObj();
        if (dasColumn == null) {
            return "";
        }
        try {
            return CompatibleUtils.getDataType(dasColumn).toString();
        } catch (Exception e) {
            return "";
        }
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
}
