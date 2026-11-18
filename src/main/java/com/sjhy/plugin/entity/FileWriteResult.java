package com.sjhy.plugin.entity;

import lombok.Data;

/**
 * 单个生成文件的写入结果（自带上报所需元数据，result 为真实写入状态）
 *
 * @author makejava
 * @version 1.0.0
 * @since 2024/01/01
 */
@Data
public class FileWriteResult {
    /**
     * 数据库原始表名（取不到时为类名，如实体类模式）
     */
    private String tableName;

    /**
     * 模板名（与生成该文件的模板一致；嵌套生成时为 $generateService.run 传入的模板名）
     */
    private String template;

    /**
     * 表对应的类名（前缀处理后的类名，与模板变量 $tableInfo.name 一致）
     */
    private String className;

    /**
     * 渲染后的目标文件名（模板 callback.fileName）
     */
    private String fileName;

    /**
     * 渲染后的保存路径（模板 callback.savePath）
     */
    private String savePath;

    /**
     * 写入结果
     */
    private SaveResult result;

    /**
     * 渲染产物全文（preview/嵌套回传场景填充；generate_code 写盘清单消费方不使用该字段）
     */
    private String code;
}
