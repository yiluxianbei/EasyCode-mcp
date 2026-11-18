package com.sjhy.plugin.tool;

import com.sjhy.plugin.entity.GlobalConfig;
import com.sjhy.plugin.entity.Template;
import com.sjhy.plugin.entity.TemplateGroup;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * 模板工具，主要用于对模板进行预处理
 *
 * @author makejava
 * @version 1.0.0
 * @since 2018/09/01 15:07
 */
public final class TemplateUtils {
    /**
     * 不允许创建实例对象
     */
    private TemplateUtils() {
        throw new UnsupportedOperationException();
    }

    /**
     * 向模板中注入全局变量
     *
     * @param template      模板
     * @param globalConfigs 全局变量
     * @return 处理好的模板
     */
    public static String addGlobalConfig(String template, Collection<GlobalConfig> globalConfigs) {
        if (CollectionUtil.isEmpty(globalConfigs)) {
            return template;
        }
        for (GlobalConfig globalConfig : globalConfigs) {
            String name = globalConfig.getName();
            // 正则被替换字符转义处理
            String value = globalConfig.getValue().replace("\\", "\\\\").replace("$", "\\$");

            // 将不带{}的变量加上{}
            template = template.replaceAll("\\$!?" + name + "(\\W)", "\\$!{" + name + "}$1");
            // 统一替换
            template = template.replaceAll("\\$!?\\{" + name + "}", value);
        }
        return template;
    }

    /**
     * 向模板中注入全局变量
     *
     * @param template      模板对象
     * @param globalConfigs 全局变量
     */
    public static void addGlobalConfig(Template template, Collection<GlobalConfig> globalConfigs) {
        if (template == null || StringUtils.isEmpty(template.getCode())) {
            return;
        }
        // 模板后面添加换行符号，防止在模板末尾添加全局变量导致无法匹配问题
        template.setCode(addGlobalConfig(template.getCode() + "\n", globalConfigs));
    }

    /**
     * 向模板中注入全局变量
     *
     * @param templates     多个模板
     * @param globalConfigs 全局变量
     */
    public static void addGlobalConfig(Collection<Template> templates, Collection<GlobalConfig> globalConfigs) {
        if (CollectionUtil.isEmpty(templates)) {
            return;
        }
        templates.forEach(template -> addGlobalConfig(template, globalConfigs));
    }

    /**
     * 向模板中注入全局变量
     *
     * @param templates 多个模板
     */
    public static void addGlobalConfig(Collection<Template> templates) {
        addGlobalConfig(templates, CurrGroupUtils.getCurrGlobalConfigGroup().getElementList());
    }

    /**
     * 向模板中注入全局变量
     *
     * @param template 单个模板
     */
    public static void addGlobalConfig(Template template) {
        if (template != null) {
            addGlobalConfig(Collections.singleton(template));
        }
    }

    /**
     * 在模板组内按名称查找模板
     *
     * @param templateGroup 模板组（可为 null）
     * @param templateName  模板名称
     * @return 首个名称匹配的模板，未找到返回 null
     */
    public static Template findTemplate(TemplateGroup templateGroup, String templateName) {
        if (templateGroup == null || StringUtils.isEmpty(templateName) || CollectionUtil.isEmpty(templateGroup.getElementList())) {
            return null;
        }
        for (Template template : templateGroup.getElementList()) {
            if (template != null && Objects.equals(template.getName(), templateName)) {
                return template;
            }
        }
        return null;
    }

    /**
     * 拼接模板组内可用模板名清单
     *
     * @param templateGroup 模板组（可为 null）
     * @return 模板名清单（逗号分隔，无可用模板时为空串）
     */
    public static String getTemplateNames(TemplateGroup templateGroup) {
        if (templateGroup == null || CollectionUtil.isEmpty(templateGroup.getElementList())) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (Template template : templateGroup.getElementList()) {
            if (template != null && !StringUtils.isEmpty(template.getName())) {
                joiner.add(template.getName());
            }
        }
        return joiner.toString();
    }

    /**
     * 拼接嵌套模板未找到的告警文案
     *
     * @param groupName    本次生成所用模板分组名
     * @param templateGroup 模板组对象（可为 null 表示分组不存在）
     * @param templateName 未找到的模板名
     * @param tableName    当前表名（可为空）
     * @return 告警文案
     */
    public static String getMissingTemplateMessage(String groupName, TemplateGroup templateGroup, String templateName, String tableName) {
        StringBuilder builder = new StringBuilder("嵌套模板未生成：");
        if (StringUtils.isEmpty(groupName)) {
            builder.append("未确定模板分组");
        } else if (templateGroup == null) {
            builder.append("模板分组 ").append(groupName).append(" 不存在");
        } else {
            builder.append("模板分组 ").append(groupName).append(" 中找不到模板 ").append(templateName);
        }
        if (!StringUtils.isEmpty(tableName)) {
            builder.append("（表 ").append(tableName).append("）");
        }
        String templateNames = getTemplateNames(templateGroup);
        if (StringUtils.isEmpty(templateNames)) {
            builder.append("，该分组无可用模板");
        } else {
            builder.append("，该分组可用模板: ").append(templateNames);
        }
        return builder.toString();
    }
}
