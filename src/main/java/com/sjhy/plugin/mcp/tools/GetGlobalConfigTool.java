package com.sjhy.plugin.mcp.tools;

import com.intellij.openapi.application.ReadAction;
import com.sjhy.plugin.dto.SettingsStorageDTO;
import com.sjhy.plugin.entity.GlobalConfig;
import com.sjhy.plugin.entity.GlobalConfigGroup;
import com.sjhy.plugin.mcp.AbstractMcpTool;
import com.sjhy.plugin.mcp.McpThreading;
import com.sjhy.plugin.service.SettingsStorageService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具：查询全局配置（Global Config）内容
 * <p>
 * 模板中的 {@code $!{xxx.vm}} 这类写法是全局配置名，生成前由 TemplateUtils 按名替换为配置值；
 * 本工具返回配置项的实际内容（字段 value），供 agent 理解模板宏展开结果。
 * 不传 group 时读取当前全局配置组（即生成时实际注入的那一组，与模板分组无关）。
 *
 * @author makejava
 * @version 1.0.0
 * @since 2024/01/01
 */
public class GetGlobalConfigTool extends AbstractMcpTool {

    @Override
    public String getName() {
        return "get_global_config";
    }

    @Override
    public String getDescription() {
        return "查询全局配置（Global Config）内容：模板里的 $!{xxx} / $!{xxx.vm} 在生成前会被替换为同名配置项的 value，本工具返回配置项实际内容。"
                + "典型用法：get_template 看到 $!{define.vm} 后，用它查出 define.vm 展开成什么。"
                + "不传 group 时读取当前全局配置组——生成时注入的就是当前组，与模板分组无关；不传 configNames 时返回该组全部配置项。"
                + "分组或配置项不存在、参数校验失败或执行出错时返回 isError 响应，text 为 {\"error\": 原因（含可用分组或可用配置项清单）}。";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("group", stringProp("全局配置组名（可选，缺省当前组；不同组配置项内容不同）"));
        properties.put("configNames", stringArrayProp("配置项名列表（可选，缺省返回该组全部配置项，如 define.vm）"));
        return schema(properties, null);
    }

    @Override
    public Map<String, Object> getOutputSchema() {
        Map<String, Object> configItem = objectProp(new LinkedHashMap<String, Object>() {{
            put("name", stringProp("配置项名，与模板中 $!{name} 的 name 对应"));
            put("value", stringProp("配置项内容，生成时替换到模板中（保留原换行与缩进）"));
        }}, list("name", "value"), null);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("group", stringProp("实际读取的全局配置组名"));
        properties.put("current", booleanProp("是否为当前选中的全局配置组（生成时注入的就是当前组）"));
        properties.put("availableGroups", arrayProp(stringProp("全局配置组名"), "全部可用全局配置组名"));
        properties.put("count", intProp("返回的配置项数量"));
        properties.put("configs", arrayProp(configItem, "配置项内容列表"));
        return schema(properties, list("group", "current", "availableGroups", "count", "configs"));
    }

    @Override
    public Object call(Map<String, Object> args) {
        // 纯配置读取，包装 ReadAction 保证线程安全
        return McpThreading.read(() -> readGlobalConfig(args));
    }

    /**
     * 读取全局配置内容
     *
     * @param args 工具入参
     * @return 分组名与配置项内容列表
     */
    private Map<String, Object> readGlobalConfig(Map<String, Object> args) {
        String requestedGroup = GenerateSupport.optionalString(args, "group");
        List<String> configNames = GetTemplateTool.optionalStringList(args, "configNames");

        SettingsStorageDTO storage = SettingsStorageService.getSettingsStorage();
        Map<String, GlobalConfigGroup> groupMap = storage.getGlobalConfigGroupMap();
        List<String> availableGroups = groupMap == null ? new ArrayList<>() : new ArrayList<>(groupMap.keySet());
        String currentGroup = storage.getCurrGlobalConfigGroupName();

        // 缺省读取当前组（生成时实际注入的组）
        String groupName = requestedGroup == null ? currentGroup : requestedGroup;
        if (groupName == null || groupMap == null || !groupMap.containsKey(groupName)) {
            throw new IllegalArgumentException("全局配置组不存在: " + groupName
                    + "，可用分组: " + availableGroups);
        }
        GlobalConfigGroup group = groupMap.get(groupName);
        List<GlobalConfig> groupConfigs = null == group || null == group.getElementList()
                ? new ArrayList<>() : group.getElementList();

        List<String> available = new ArrayList<>();
        for (GlobalConfig config : groupConfigs) {
            available.add(config.getName());
        }

        // 未指定时返回整组，指定时按传入顺序过滤
        List<GlobalConfig> targets = new ArrayList<>();
        if (configNames.isEmpty()) {
            targets.addAll(groupConfigs);
        } else {
            for (String configName : configNames) {
                GlobalConfig matched = null;
                for (GlobalConfig config : groupConfigs) {
                    if (configName.equals(config.getName())) {
                        matched = config;
                        break;
                    }
                }
                if (matched == null) {
                    throw new IllegalArgumentException("配置项 " + configName + " 不存在于全局配置组 " + groupName
                            + "，该分组可用配置项: " + available);
                }
                targets.add(matched);
            }
        }

        List<Map<String, Object>> configs = new ArrayList<>();
        for (GlobalConfig config : targets) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", config.getName());
            item.put("value", config.getValue() == null ? "" : config.getValue());
            configs.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("group", groupName);
        result.put("current", groupName.equals(currentGroup));
        result.put("availableGroups", availableGroups);
        result.put("count", configs.size());
        result.put("configs", configs);
        return result;
    }
}
