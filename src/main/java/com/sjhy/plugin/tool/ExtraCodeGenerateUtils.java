package com.sjhy.plugin.tool;

import com.sjhy.plugin.dto.GenerateOptions;
import com.sjhy.plugin.entity.TableInfo;
import com.sjhy.plugin.entity.Template;
import com.sjhy.plugin.service.impl.CodeGenerateServiceImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.sjhy.plugin.dto.SettingsStorageDTO;
import com.sjhy.plugin.entity.FileWriteResult;
import com.sjhy.plugin.entity.TemplateGroup;
import com.sjhy.plugin.service.SettingsStorageService;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.List;

/**
 * 额外的代码生成工具
 *
 * @author makejava
 * @version 1.0.0
 * @since 2018/11/01 10:11
 */
public class ExtraCodeGenerateUtils {
    /**
     * 日志
     */
    private static final Logger LOG = Logger.getInstance(ExtraCodeGenerateUtils.class);
    /**
     * 代码生成服务
     */
    private CodeGenerateServiceImpl codeGenerateService;
    /**
     * 表信息对象
     */
    private TableInfo tableInfo;
    /**
     * 生成配置
     */
    private GenerateOptions generateOptions;
    /**
     * 嵌套生成结果收集器（null 表示不收集；由外层 generate 创建并传入，
     * 嵌套层级再深也会逐层汇入最外层收集器，最终并入外层写入结果清单）
     */
    private List<FileWriteResult> nestedResults;

    public ExtraCodeGenerateUtils(CodeGenerateServiceImpl codeGenerateService, TableInfo tableInfo, GenerateOptions generateOptions) {
        this(codeGenerateService, tableInfo, generateOptions, null);
    }

    public ExtraCodeGenerateUtils(CodeGenerateServiceImpl codeGenerateService, TableInfo tableInfo, GenerateOptions generateOptions, List<FileWriteResult> nestedResults) {
        this.codeGenerateService = codeGenerateService;
        this.tableInfo = tableInfo;
        this.generateOptions = generateOptions;
        this.nestedResults = nestedResults;
    }

    /**
     * 生成代码
     *
     * @param templateName 模板名称
     * @param param        附加参数
     */
    public void run(String templateName, Map<String, Object> param) {
        // 按本次生成使用的分组解析模板（不再读全局当前分组，也不读表配置里的历史分组）
        String groupName = resolveTemplateGroupName();
        TemplateGroup templateGroup = CurrGroupUtils.getTemplateGroup(groupName);
        Template currTemplate = TemplateUtils.findTemplate(templateGroup, templateName);
        if (currTemplate == null) {
            // 不再静默：写日志 + 收集告警，由 UI（Notification）与 MCP（warnings）回传
            String message = TemplateUtils.getMissingTemplateMessage(groupName, templateGroup, templateName, getTableName());
            LOG.warn(message);
            collectWarning(message);
            return;
        }
        // 生成代码（结果并入外层收集器）
        List<FileWriteResult> results = codeGenerateService.generate(
                Collections.singletonList(currTemplate), Collections.singletonList(this.tableInfo), this.generateOptions, param);
        if (nestedResults != null) {
            nestedResults.addAll(results);
        }
    }

    /**
     * 解析本次生成使用的模板分组名
     * <p>
     * 优先取生成配置里的分组（UI 生成对话框所选分组 / MCP templateGroup 参数），
     * 仅在为空时兜底取全局当前分组（模板调试等无生成上下文的调用）。
     *
     * @return 模板分组名
     */
    private String resolveTemplateGroupName() {
        if (generateOptions != null && !StringUtils.isEmpty(generateOptions.getTemplateGroupName())) {
            return generateOptions.getTemplateGroupName();
        }
        SettingsStorageDTO settingsStorage = SettingsStorageService.getSettingsStorage();
        return settingsStorage == null ? null : settingsStorage.getCurrTemplateGroupName();
    }

    /**
     * 获取当前表名（取不到时用类名）
     *
     * @return 表名
     */
    private String getTableName() {
        if (tableInfo == null) {
            return null;
        }
        return tableInfo.getObj() == null ? tableInfo.getName() : tableInfo.getObj().getName();
    }

    /**
     * 告警去重追加到生成配置（收集器为 null 时跳过）
     *
     * @param message 告警文案
     */
    private void collectWarning(String message) {
        if (generateOptions == null) {
            return;
        }
        List<String> warnings = generateOptions.getWarnings();
        if (warnings == null || warnings.contains(message)) {
            return;
        }
        warnings.add(message);
    }
}
