package com.sjhy.plugin.mcp.tools;

import com.intellij.openapi.application.ReadAction;
import com.sjhy.plugin.dto.SettingsStorageDTO;
import com.sjhy.plugin.entity.Template;
import com.sjhy.plugin.entity.TemplateGroup;
import com.sjhy.plugin.mcp.AbstractMcpTool;
import com.sjhy.plugin.mcp.McpThreading;
import com.sjhy.plugin.service.SettingsStorageService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具：查询模板原文
 * <p>
 * 按分组与模板名返回模板代码原文（字段 content），供 agent 核对、理解或改写模板；
 * 不传 templates 时返回该分组全部模板。典型调用顺序：list_templates → get_template → preview_code。
 *
 * @author makejava
 * @version 1.0.0
 * @since 2024/01/01
 */
public class GetTemplateTool extends AbstractMcpTool {

    @Override
    public String getName() {
        return "get_template";
    }

    @Override
    public String getDescription() {
        return "查询指定模板分组内的模板原文（Velocity 模板源码）。"
                + "不传 templates 时返回该分组全部模板。"
                + "模板中的 $!{xxx} / $!{xxx.vm} 不是 Velocity 变量，而是全局配置项名，其展开内容用 get_global_config 查询。"
                + "分组或模板不存在、参数校验失败或执行出错时返回 isError 响应，text 为 {\"error\": 原因（含该分组可用模板清单）}。";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("templateGroup", stringProp("模板分组名（必填，可用 list_templates 查询；不同分组同名模板内容不同）"));
        properties.put("templates", stringArrayProp("模板名列表（可选，缺省返回该分组全部模板，可用 list_templates 查询）"));
        return schema(properties, list("templateGroup"));
    }

    @Override
    public Map<String, Object> getOutputSchema() {
        Map<String, Object> templateItem = objectProp(new LinkedHashMap<String, Object>() {{
            put("name", stringProp("模板名"));
            put("content", stringProp("模板原文（Velocity 模板源码，保留原换行与缩进）"));
        }}, list("name", "content"), null);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("group", stringProp("模板分组名"));
        properties.put("count", intProp("返回的模板数量"));
        properties.put("templates", arrayProp(templateItem, "模板原文列表"));
        return schema(properties, list("group", "count", "templates"));
    }

    @Override
    public Object call(Map<String, Object> args) {
        // 纯配置读取，包装 ReadAction 保证线程安全
        return McpThreading.read(() -> readTemplates(args));
    }

    /**
     * 读取模板原文
     *
     * @param args 工具入参
     * @return 分组名与模板原文列表
     */
    private Map<String, Object> readTemplates(Map<String, Object> args) {
        String groupName = GenerateSupport.requiredString(args, "templateGroup");
        List<String> templateNames = optionalStringList(args, "templates");

        SettingsStorageDTO storage = SettingsStorageService.getSettingsStorage();
        Map<String, TemplateGroup> groupMap = storage.getTemplateGroupMap();
        if (groupMap == null || !groupMap.containsKey(groupName)) {
            throw new IllegalArgumentException("模板分组不存在: " + groupName
                    + "，可用分组: " + (groupMap == null ? new ArrayList<>() : groupMap.keySet())
                    + "（可用 list_templates 查询）");
        }
        TemplateGroup group = groupMap.get(groupName);
        List<Template> groupTemplates = null == group || null == group.getElementList()
                ? new ArrayList<>() : group.getElementList();

        List<String> available = new ArrayList<>();
        for (Template template : groupTemplates) {
            available.add(template.getName());
        }

        // 未指定时返回整组，指定时按传入顺序过滤
        List<Template> targets = new ArrayList<>();
        if (templateNames.isEmpty()) {
            targets.addAll(groupTemplates);
        } else {
            for (String templateName : templateNames) {
                Template matched = null;
                for (Template template : groupTemplates) {
                    if (templateName.equals(template.getName())) {
                        matched = template;
                        break;
                    }
                }
                if (matched == null) {
                    throw new IllegalArgumentException("模板 " + templateName + " 不存在于分组 " + groupName
                            + "，该分组可用模板: " + available);
                }
                targets.add(matched);
            }
        }

        List<Map<String, Object>> templates = new ArrayList<>();
        for (Template template : targets) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", template.getName());
            item.put("content", template.getCode() == null ? "" : template.getCode());
            templates.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("group", groupName);
        result.put("count", templates.size());
        result.put("templates", templates);
        return result;
    }

    /**
     * 可选字符串列表参数（兼容单值；缺省/空列表均视为未指定，不校验必填）
     *
     * @param args 参数
     * @param name 参数名
     * @return 列表（未指定时为空列表）
     */
    static List<String> optionalStringList(Map<String, Object> args, String name) {
        Object value = args.get(name);
        List<String> result = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item != null && !String.valueOf(item).isEmpty()) {
                    result.add(String.valueOf(item));
                }
            }
        } else if (value instanceof String && !((String) value).isEmpty()) {
            result.add((String) value);
        }
        return result;
    }
}
