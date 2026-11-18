package com.sjhy.plugin.tool;

import java.util.Locale;

/**
 * 数据库系统 schema 判断工具（与 MCP 无关的通用工具，任何模块可依赖）
 * <p>
 * 供代码生成选表场景过滤系统对象使用，MySQL / PostgreSQL 均适用：
 * <ul>
 *     <li>PostgreSQL：pg_catalog、information_schema、pg_toast*、pg_temp*（含会话/Toast schema）</li>
 *     <li>MySQL：mysql、sys、performance_schema、information_schema（schema 即 database）</li>
 * </ul>
 *
 * @author makejava
 * @version 1.0.0
 * @since 2024/01/01
 */
public final class SystemSchemaUtils {

    /**
     * 工具类禁止实例化
     */
    private SystemSchemaUtils() {
        throw new UnsupportedOperationException();
    }

    /**
     * 判断 schema 是否为数据库系统 schema
     *
     * @param schema schema 名（可能为 null 或空串，均返回 false）
     * @return 是否系统 schema
     */
    public static boolean isSystemSchema(String schema) {
        if (schema == null || schema.isEmpty()) {
            return false;
        }
        String name = schema.toLowerCase(Locale.ROOT);
        // PostgreSQL 系统对象（pg_toast_temp* / pg_temp_* 等以 pg_ 开头的会话/Toast schema 也一并覆盖）
        if (name.equals("pg_catalog") || name.equals("information_schema")
                || name.startsWith("pg_toast") || name.startsWith("pg_temp")) {
            return true;
        }
        // MySQL 系统库
        return name.equals("mysql") || name.equals("sys") || name.equals("performance_schema");
    }
}
