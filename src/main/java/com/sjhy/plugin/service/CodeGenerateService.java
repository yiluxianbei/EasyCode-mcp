package com.sjhy.plugin.service;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.sjhy.plugin.dto.GenerateOptions;
import com.sjhy.plugin.entity.TableInfo;
import com.sjhy.plugin.entity.Template;
import org.jetbrains.annotations.NotNull;
import com.sjhy.plugin.entity.FileWriteResult;

import java.util.Collection;
import java.util.List;

/**
 * 代码生成服务，Project级别Service
 *
 * @author makejava
 * @version 1.0.0
 * @since 2018/09/02 12:48
 */
public interface CodeGenerateService {
    /**
     * 获取实例对象
     *
     * @param project 项目对象
     * @return 实例对象
     */
    static CodeGenerateService getInstance(@NotNull Project project) {
        return ServiceManager.getService(project, CodeGenerateService.class);
    }

    /**
     * 生成
     *
     * @param templates       模板
     * @param generateOptions 生成选项
     */
    void generate(Collection<Template> templates, GenerateOptions generateOptions);

    /**
     * 生成代码，并返回逐文件写入结果（供 MCP 等需要精确感知写盘状态的调用方使用）
     *
     * @param templates       模板
     * @param generateOptions 生成选项
     * @return 逐文件写入结果列表
     */
    List<FileWriteResult> generateWithResults(Collection<Template> templates, GenerateOptions generateOptions);

    /**
     * 生成代码
     *
     * @param template  模板
     * @param tableInfo 表信息对象
     * @return 生成好的代码
     */
    String generate(Template template, TableInfo tableInfo);
}
