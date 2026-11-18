package com.sjhy.plugin.mcp.tools;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.sjhy.plugin.dto.GenerateOptions;
import com.sjhy.plugin.entity.FileWriteResult;
import com.sjhy.plugin.entity.SaveResult;
import com.sjhy.plugin.entity.TableInfo;
import com.sjhy.plugin.mcp.AbstractMcpTool;
import com.sjhy.plugin.mcp.McpThreading;
import com.sjhy.plugin.mcp.McpTool;
import com.sjhy.plugin.mcp.TableResolver;
import com.sjhy.plugin.service.CodeGenerateService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具：正式生成代码写盘
 * <p>
 * headless 流程：参数解析与校验（模板组/模板/类型映射/路径/模块）→ 表信息构建（统一配置语义）
 * → 持久化选中表配置 → CacheDataUtils 注入选中表 → EDT 上复用 {@link com.sjhy.plugin.service.CodeGenerateService}
 * 批量生成写盘 → 直接以写盘链路回传的逐文件真实结果组装返回清单（无预渲染、无状态推断）。
 * 生成过程无任何弹窗（目录自动创建；文件冲突由 overwrite 决定覆盖或跳过）。
 *
 * @author makejava
 * @version 1.0.0
 * @since 2024/01/01
 */
public class GenerateCodeTool extends AbstractMcpTool {

    @Override
    public String getName() {
        return "generate_code";
    }

    @Override
    public String getDescription() {
        return "按指定表与模板生成代码文件并写盘。overwrite=true 时已存在文件直接覆盖，false 时跳过已存在文件；目录不存在会自动创建。"
                + "返回逐文件的写入状态清单，status 含义：created=新创建，overwritten=覆盖已存在文件，"
                + "skipped=文件已存在且 overwrite=false 被跳过，failed=写入失败；written 为 created+overwritten 的数量。"
                + "warnings 返回非致命告警，为空数组表示无告警。"
                + "存在列类型无映射时返回 isError 响应，text 为 {\"error\": 未映射列清单}；其他校验失败或执行出错时同为 isError 响应。";
    }

    @Override
    public Map<String, Object> getOutputSchema() {
        Map<String, Object> fileItem = objectProp(new LinkedHashMap<String, Object>() {{
            put("table", stringProp("数据库表名"));
            put("className", stringProp("生成的 Java 类名"));
            put("template", stringProp("模板名"));
            put("file", stringProp("写入的文件绝对路径"));
            put("status", enumProp("写入状态", "created", "overwritten", "skipped", "failed"));
        }}, list("table", "className", "template", "file", "status"), null);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("total", intProp("生成的文件总数（含模板追加生成的附加文件）"));
        properties.put("written", intProp("实际写入成功数（created+overwritten）"));
        properties.put("files", arrayProp(fileItem, "逐文件写入状态清单"));
        properties.put("warnings", arrayProp(stringProp("非致命告警文案"), "告警列表（无告警时为空数组）"));
        return schema(properties, list("total", "written", "files", "warnings"));
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
        properties.put("savePath", stringProp("保存路径（必填，绝对路径或 ./ 相对项目路径，目录不存在自动创建）"));
        properties.put("preName", stringProp("表名前缀（可选，生成类名时去除）"));
        properties.put("unifiedConfig", booleanProp("统一配置：true=保存配置应用到所有表；false=仅应用到未配置过保存路径的表（默认 false）"));
        properties.put("overwrite", booleanProp("文件冲突策略（必填）：true=已存在文件直接覆盖；false=跳过已存在文件"));
        properties.put("reFormat", booleanProp("写盘后是否使用 IDEA 格式化器格式化生成的文件（默认 false）"));
        return schema(properties, list("datasource", "tables", "templates", "templateGroup", "saveModelName", "savePackageName", "savePath", "overwrite"));
    }

    @Override
    public Object call(Map<String, Object> args) {
        // overwrite 仅 generate_code 必填（parse 已改为可选，此处自行校验）
        GenerateSupport.requiredBoolean(args, "overwrite");
        // 1. 读操作：解析校验（含数据库 PSI 解析，须在 ReadAction 中） + 表信息构建
        java.util.concurrent.atomic.AtomicReference<GenerateSupport.GenerateRequest> requestRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        McpThreading.read(() -> {
            GenerateSupport.GenerateRequest request = GenerateSupport.parse(args);
            requestRef.set(request);
            List<TableInfo> tableInfos = GenerateSupport.buildTableInfos(request);
            List<String> unmapped = GenerateSupport.checkTypeMapping(tableInfos);
            if (!unmapped.isEmpty()) {
                throw new IllegalArgumentException("以下列没有类型映射，请先在 设置 > EasyCode > 类型映射 中补充映射关系: "
                        + String.join("、", unmapped));
            }
            return null;
        });
        GenerateSupport.GenerateRequest request = requestRef.get();

        // 2. 写盘：持久化选中表配置（读操作）→ 注入选中表 → EDT 上批量生成（逐文件真实写入结果回传）
        McpThreading.read(() -> {
            GenerateSupport.persistSelected(request);
            return null;
        });
        GenerateSupport.injectSelection(request);
        // 生成选项（含本次模板分组与告警收集器，嵌套模板按此分组解析）
        GenerateOptions options = GenerateSupport.buildOptions(request, true);
        java.util.concurrent.atomic.AtomicReference<List<FileWriteResult>> writeResultsRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        McpThreading.onEdt(() -> {
            writeResultsRef.set(CodeGenerateService.getInstance(TableResolver.requireProject())
                    .generateWithResults(new ArrayList<>(request.templates), options));
            return null;
        });
        List<FileWriteResult> writeResults = writeResultsRef.get() == null ? new ArrayList<>() : writeResultsRef.get();

        // 2.5 强制落盘：writeFileContent 走 document.setText 时只改内存文档，UI 流程靠 IDEA 自动保存（窗口失活等）兑底，
        //     无人值守场景无自动保存触发点，不显式保存会出现磁盘 0 字节文件。
        //     saveAllDocuments 须在写命令之外的 EDT 上执行（此时生成循环内短事务均已结束）。
        McpThreading.onEdt(() -> {
            FileDocumentManager.getInstance().saveAllDocuments();
            return null;
        });

        // 3. 清单直接由真实写入结果组装（无预渲染、无匹配、无存在性推断）
        List<Map<String, Object>> files = new ArrayList<>();
        int written = 0;
        for (FileWriteResult writeResult : writeResults) {
            if (writeResult.getResult() == SaveResult.CREATED || writeResult.getResult() == SaveResult.OVERWRITTEN) {
                written++;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("table", writeResult.getTableName() == null ? writeResult.getClassName() : writeResult.getTableName());
            item.put("className", writeResult.getClassName());
            item.put("template", writeResult.getTemplate());
            item.put("file", GenerateSupport.joinPath(writeResult.getSavePath(), writeResult.getFileName()));
            item.put("status", statusOf(writeResult.getResult()));
            files.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", files.size());
        result.put("written", written);
        result.put("files", files);
        result.put("warnings", options.getWarnings() == null ? new ArrayList<String>() : options.getWarnings());
        return result;
    }

    /**
     * 写入结果枚举转状态文本
     *
     * @param saveResult 写入结果
     * @return 状态文本
     */
    private String statusOf(SaveResult saveResult) {
        if (saveResult == SaveResult.CREATED) {
            return "created";
        }
        if (saveResult == SaveResult.OVERWRITTEN) {
            return "overwritten";
        }
        if (saveResult == SaveResult.SKIPPED) {
            return "skipped";
        }
        return "failed";
    }
}
