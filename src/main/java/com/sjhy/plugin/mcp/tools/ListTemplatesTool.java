package com.sjhy.plugin.mcp.tools;

import com.intellij.openapi.application.ReadAction;
import com.sjhy.plugin.dto.SettingsStorageDTO;
import com.sjhy.plugin.entity.Template;
import com.sjhy.plugin.entity.TemplateGroup;
import com.sjhy.plugin.mcp.AbstractMcpTool;
import com.sjhy.plugin.mcp.McpThreading;
import com.sjhy.plugin.mcp.McpTool;
import com.sjhy.plugin.service.SettingsStorageService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具：列出模板分组与模板
 *
 * @author makejava
 * @version 1.0.0
 * @since 2024/01/01
 */
public class ListTemplatesTool extends AbstractMcpTool {

    @Override
    public String getName() {
        return "list_templates";
    }

    @Override
    public String getDescription() {
        return "列出所有模板分组及各组内的模板名，并标记当前分组。"
                + "preview_code / generate_code 的 templateGroup（必传）与 templates 参数据此选择；不同分组存在同名模板且内容不同，务必先确认分组。"
                + "需要模板原文时用本轮返回的分组名与模板名调用 get_template。"
                + "执行出错时返回 isError 响应，text 为 {\"error\": 原因}。";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return schema(null, null);
    }

    @Override
    public Map<String, Object> getOutputSchema() {
        Map<String, Object> groupItem = objectProp(new LinkedHashMap<String, Object>() {{
            put("name", stringProp("模板分组名"));
            put("current", booleanProp("是否为当前选中分组"));
            put("templates", arrayProp(stringProp("模板名"), "组内模板名列表"));
        }}, list("name", "current", "templates"), null);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("currentGroup", stringProp("当前选中的模板分组名"));
        properties.put("groups", arrayProp(groupItem, "模板分组列表"));
        return schema(properties, list("currentGroup", "groups"));
    }

    @Override
    public Object call(Map<String, Object> args) {
        // 纯配置读取，包装 ReadAction 保证线程安全
        return McpThreading.read(this::readTemplates);
    }

    /**
     * 读取模板分组信息
     *
     * @return 分组信息
     */
    private Map<String, Object> readTemplates() {
        SettingsStorageDTO storage = SettingsStorageService.getSettingsStorage();
        String currentGroup = storage.getCurrTemplateGroupName();

        List<Map<String, Object>> groups = new ArrayList<>();
        if (storage.getTemplateGroupMap() != null) {
            for (Map.Entry<String, TemplateGroup> entry : storage.getTemplateGroupMap().entrySet()) {
                Map<String, Object> group = new LinkedHashMap<>();
                group.put("name", entry.getKey());
                group.put("current", entry.getKey().equals(currentGroup));
                List<String> templateNames = new ArrayList<>();
                if (entry.getValue() != null && entry.getValue().getElementList() != null) {
                    for (Template template : entry.getValue().getElementList()) {
                        templateNames.add(template.getName());
                    }
                }
                group.put("templates", templateNames);
                groups.add(group);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentGroup", currentGroup);
        result.put("groups", groups);
        return result;
    }
}
