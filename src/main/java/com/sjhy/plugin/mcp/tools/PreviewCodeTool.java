package com.sjhy.plugin.mcp.tools;

import com.intellij.openapi.application.ReadAction;
import com.sjhy.plugin.dto.GenerateOptions;
import com.sjhy.plugin.entity.TableInfo;
import com.sjhy.plugin.mcp.AbstractMcpTool;
import com.sjhy.plugin.mcp.McpThreading;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具：预览渲染代码（不写盘）
 * <p>
 * 入参与 generate_code 一致，按 表×模板 组合渲染并返回代码（含嵌套 $generateService.run 产物），
 * agent 可用相同参数直接调用 generate_code 落盘。
 *
 * @author makejava
 * @version 1.0.0
 * @since 2024/01/01
 */
public class PreviewCodeTool extends AbstractMcpTool {

    @Override
    public String getName() {
        return "preview_code";
    }

    @Override
    public String getDescription() {
        return "按指定表与模板渲染代码并返回结果，不写盘。可用相同参数直接调用 generate_code 落盘。"
                + "warnings 返回非致命告警，为空数组表示无告警。存在列类型无映射时返回 isError 响应，"
                + "text 为 {\"error\": 未映射列清单}；其他校验失败或执行出错时同为 isError 响应。";
    }

    @Override
    public Map<String, Object> getOutputSchema() {
        Map<String, Object> fileItem = objectProp(new LinkedHashMap<String, Object>() {{
            put("table", stringProp("数据库表名"));
            put("className", stringProp("生成的 Java 类名"));
            put("template", stringProp("模板名"));
            put("code", stringProp("渲染出的完整代码文本"));
            put("file", stringProp("渲染产物目标文件绝对路径"));
        }}, list("table", "className", "template", "code"), null);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("count", intProp("渲染出的文件数量"));
        properties.put("files", arrayProp(fileItem, "渲染结果列表"));
        properties.put("warnings", arrayProp(stringProp("非致命告警文案"), "告警列表（无告警时为空数组）"));
        return schema(properties, list("count", "files", "warnings"));
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("datasource", stringProp("数据源名称（必填，可用 list_datasources 查询）"));
        properties.put("tables", stringArrayProp("表名列表（必填，注意大小写，可用 list_tables 查询）"));
        properties.put("templates", stringArrayProp("模板名列表（必填，须存在于 templateGroup 分组内）"));
        properties.put("templateGroup", stringProp("模板分组名（必传，可用 list_templates 查询；不同分组同名模板内容不同）"));
        properties.put("saveModelName", stringProp("保存的目标模块名（必填，决定模板变量 modulePath）"));
        properties.put("savePackageName", stringProp("保存包名（必填，模板内 savePackageName 变量取此值）"));
        properties.put("savePath", stringProp("保存路径（必填，绝对路径或 ./ 相对项目路径，仅用于渲染预览，不会写盘）"));
        properties.put("preName", stringProp("表名前缀（可选，生成类名时去除）"));
        properties.put("unifiedConfig", booleanProp("统一配置：true=保存配置应用到所有表；false=仅应用到未配置过保存路径的表（默认 false）"));
        return schema(properties, list("datasource", "tables", "templates", "templateGroup", "saveModelName", "savePackageName", "savePath"));
    }

    @Override
    public Object call(Map<String, Object> args) {
        return McpThreading.read(() -> {
            GenerateSupport.GenerateRequest request = GenerateSupport.parse(args);
            // 表信息解析（含数据库 PSI 解析，须在 ReadAction 中）
            List<TableInfo> tableInfos = GenerateSupport.buildTableInfos(request);
            // 类型映射完整性校验（与正式生成一致，避免预览与生成结果不一致）
            List<String> unmapped = GenerateSupport.checkTypeMapping(tableInfos);
            if (!unmapped.isEmpty()) {
                throw new IllegalArgumentException("以下列没有类型映射，请先在 设置 > EasyCode > 类型映射 中补充映射关系: "
                        + String.join("、", unmapped));
            }
            // 预览选项：previewMode 保证嵌套 $generateService.run 只渲染不写盘
            GenerateOptions options = GenerateSupport.buildOptions(request, false);
            List<Map<String, Object>> items = new ArrayList<>();
            for (GenerateSupport.RenderedFile file : GenerateSupport.renderAll(request, tableInfos, options)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("table", file.table);
                item.put("className", file.className);
                item.put("template", file.template);
                item.put("code", file.code);
                item.put("file", file.fullPath);
                items.add(item);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("count", items.size());
            result.put("files", items);
            result.put("warnings", options.getWarnings() == null ? new ArrayList<>() : options.getWarnings());
            return result;
        });
    }
}