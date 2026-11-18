package com.sjhy.plugin.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 生成选项
 *
 * @author makejava
 * @version 1.0.0
 * @date 2021/08/17 09:08
 */
@Data
@Builder
public class GenerateOptions {
    /**
     * 实体类模式
     */
    private Boolean entityModel;
    /**
     * 统一配置
     */
    private Boolean unifiedConfig;
    /**
     * 重新格式化代码
     */
    private Boolean reFormat;
    /**
     * 提示选是
     */
    private Boolean titleSure;
    /**
     * 提示选否
     */
    private Boolean titleRefuse;
    /**
     * 静默模式（agent/MCP 等无人值守场景：目录/文件创建失败不弹窗，只记日志并返回失败）
     */
    private Boolean silentMode;
    /**
     * 预览模式（渲染不写盘：主模板与嵌套 $generateService.run 生成的文件均不落盘）
     */
    private Boolean previewMode;
    /**
     * 模板组名（指定后嵌套 $generateService.run 按该组查找模板；缺省回退全局当前模板组）
     */
    private String templateGroupName;
    /**
     * 生成过程中的告警信息（如列类型无映射、嵌套模板渲染失败等，供调用方上报）
     */
    private List<String> warnings;
}
