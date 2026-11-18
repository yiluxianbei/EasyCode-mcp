package com.sjhy.plugin.mcp;

import com.intellij.database.model.DasTable;
import com.intellij.database.psi.DbDataSource;
import com.intellij.database.psi.DbTable;
import com.intellij.database.util.DasUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.containers.JBIterable;
import com.sjhy.plugin.tool.SystemSchemaUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据源/表解析器
 * <p>
 * 使用 DatabaseTools 官方枚举 API（{@link DasUtil#getTables(DasDataSource)}），
 * 跨 IDE 版本稳定，基于已内省的模型（数据源在 Database 面板连接过一次即可，无需每次展开）。
 * 全部方法须在 ReadAction 中调用（由工具层统一包装）。
 *
 * @author makejava
 * @version 1.0.0
 * @since 2024/01/01
 */
public final class TableResolver {

    private TableResolver() {
        throw new UnsupportedOperationException();
    }

    /**
     * 表引用（数据源/schema/表名 + 原始 DbTable）
     */
    public static class TableRef {
        private final String datasource;
        private final String schema;
        private final String name;
        private final DbTable dbTable;

        TableRef(String datasource, String schema, String name, DbTable dbTable) {
            this.datasource = datasource;
            this.schema = schema;
            this.name = name;
            this.dbTable = dbTable;
        }

        public String getDatasource() {
            return datasource;
        }

        public String getSchema() {
            return schema;
        }

        public String getName() {
            return name;
        }

        public DbTable getDbTable() {
            return dbTable;
        }
    }

    /**
     * 获取唯一打开的项目（过滤 Default 项目）
     *
     * @return 项目对象
     */
    public static Project requireProject() {
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        List<Project> realProjects = new ArrayList<>();
        for (Project project : projects) {
            if (!project.isDefault()) {
                realProjects.add(project);
            }
        }
        if (realProjects.isEmpty()) {
            throw new IllegalStateException("没有已打开的项目，请先在 IDE 中打开项目");
        }
        if (realProjects.size() > 1) {
            throw new IllegalStateException("检测到多个已打开的项目（" + realProjects.size() + " 个），请只保留一个项目后重试");
        }
        return realProjects.get(0);
    }

    /**
     * 列出项目数据源（name、dbms）
     *
     * @return 数据源信息列表
     */
    public static List<Map<String, Object>> listDatasources() {
        Project project = requireProject();
        List<Map<String, Object>> result = new ArrayList<>();
        for (DbDataSource dataSource : DbPsiFacadeHolder.getDataSources(project)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", dataSource.getName());
            item.put("dbms", dbmsName(dataSource));
            result.add(item);
        }
        return result;
    }

    /**
     * 枚举数据源下的表
     *
     * @param datasourceName 数据源名（null 表示全部数据源）
     * @param schemaName     schema 名过滤（null/空 表示不过滤）
     * @param keyword        表名包含关键字过滤（null/空 表示不过滤）
     * @return 表引用列表
     */
    public static List<TableRef> listTables(String datasourceName, String schemaName, String keyword) {
        List<TableRef> result = new ArrayList<>();
        for (DbDataSource dataSource : datasources(datasourceName)) {
            for (TableRef tableRef : tablesOf(dataSource)) {
                if (SystemSchemaUtils.isSystemSchema(tableRef.getSchema())) {
                    // 过滤数据库系统 schema（MySQL/PostgreSQL），代码生成场景无意义
                    continue;
                }
                if (schemaName != null && !schemaName.isEmpty()
                        && (tableRef.getSchema() == null || !schemaName.equalsIgnoreCase(tableRef.getSchema()))) {
                    continue;
                }
                if (keyword != null && !keyword.isEmpty()
                        && (tableRef.getName() == null || !tableRef.getName().toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)))) {
                    continue;
                }
                result.add(tableRef);
            }
        }
        return result;
    }

    /**
     * 按名称解析单张表
     *
     * @param datasourceName 数据源名（必填）
     * @param schemaName     schema 名（可为 null）
     * @param tableName      表名（必填）
     * @return 表引用
     */
    public static TableRef resolveTable(String datasourceName, String schemaName, String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            throw new IllegalArgumentException("table is required");
        }
        List<DbDataSource> matched = datasources(datasourceName);
        if (matched.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (DbDataSource dataSource : datasources(null)) {
                names.add(dataSource.getName());
            }
            throw new IllegalArgumentException("数据源不存在: " + datasourceName + "，可用数据源: " + names);
        }
        boolean hasTables = false;
        for (DbDataSource dataSource : matched) {
            List<TableRef> tables = tablesOf(dataSource);
            if (!tables.isEmpty()) {
                hasTables = true;
            }
            List<TableRef> schemaMatched = new ArrayList<>();
            for (TableRef tableRef : tables) {
                if (schemaName != null && !schemaName.isEmpty()
                        && (tableRef.getSchema() == null || !schemaName.equalsIgnoreCase(tableRef.getSchema()))) {
                    continue;
                }
                if (tableName.equals(tableRef.getName())) {
                    return tableRef;
                }
                if (tableName.equalsIgnoreCase(tableRef.getName())) {
                    schemaMatched.add(tableRef);
                }
            }
            if (!schemaMatched.isEmpty()) {
                return schemaMatched.get(0);
            }
        }
        if (!hasTables) {
            throw new IllegalArgumentException("数据源 " + matched.get(0).getName()
                    + " 下未发现任何表，请先在 Database 面板连接/刷新该数据源完成内省后重试");
        }
        throw new IllegalArgumentException("表不存在: " + (schemaName == null ? "" : schemaName + ".") + tableName
                + "（注意表名区分大小写，可先用 list_tables 查询）");
    }

    /**
     * 按名称匹配数据源（精确优先，其次忽略大小写）
     *
     * @param datasourceName 数据源名（null 表示全部）
     * @return 匹配的数据源列表
     */
    private static List<DbDataSource> datasources(String datasourceName) {
        Project project = requireProject();
        List<DbDataSource> all = new ArrayList<>(DbPsiFacadeHolder.getDataSources(project));
        if (datasourceName == null || datasourceName.isEmpty()) {
            return all;
        }
        List<DbDataSource> exact = new ArrayList<>();
        List<DbDataSource> ignoreCase = new ArrayList<>();
        for (DbDataSource dataSource : all) {
            String name = dataSource.getName();
            if (datasourceName.equals(name)) {
                exact.add(dataSource);
            } else if (datasourceName.equalsIgnoreCase(name)) {
                ignoreCase.add(dataSource);
            }
        }
        return exact.isEmpty() ? ignoreCase : exact;
    }

    /**
     * 枚举单个数据源下的全部表（DasUtil 官方 API，基于已内省模型）
     *
     * @param dataSource 数据源
     * @return 表引用列表
     */
    private static List<TableRef> tablesOf(DbDataSource dataSource) {
        List<TableRef> out = new ArrayList<>();
        JBIterable<? extends DasTable> tables = DasUtil.getTables(dataSource);
        for (DasTable dasTable : tables) {
            DbTable dbTable = toDbTable(dasTable, dataSource);
            if (dbTable == null) {
                continue;
            }
            String schema;
            try {
                schema = DasUtil.getSchema(dasTable);
            } catch (Exception e) {
                schema = "";
            }
            out.add(new TableRef(dataSource.getName(), schema == null ? "" : schema, dbTable.getName(), dbTable));
        }
        return out;
    }

    /**
     * DasTable → DbTable PSI（模型对象与 PSI 元素互换）
     *
     * @param dasTable   模型表对象
     * @param dataSource 所属数据源
     * @return DbTable PSI，无法转换返回 null
     */
    private static DbTable toDbTable(DasTable dasTable, DbDataSource dataSource) {
        if (dasTable instanceof DbTable) {
            return (DbTable) dasTable;
        }
        try {
            com.intellij.database.psi.DbElement element = dataSource.findElement(dasTable);
            if (element instanceof DbTable) {
                return (DbTable) element;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 反射获取数据源 dbms 显示名（跨版本兜底，取不到返回空串）
     *
     * @param dataSource 数据源
     * @return dbms 显示名
     */
    private static String dbmsName(DbDataSource dataSource) {
        try {
            Object dbms = dataSource.getClass().getMethod("getDbms").invoke(dataSource);
            try {
                return String.valueOf(dbms.getClass().getMethod("getDisplayName").invoke(dbms));
            } catch (Exception e) {
                return String.valueOf(dbms);
            }
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * DbPsiFacade 访问持有者（隔离静态依赖，便于统一 import）
     */
    private static class DbPsiFacadeHolder {
        private static java.util.Collection<DbDataSource> getDataSources(Project project) {
            return com.intellij.database.psi.DbPsiFacade.getInstance(project).getDataSources();
        }
    }
}
