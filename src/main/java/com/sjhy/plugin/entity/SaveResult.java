package com.sjhy.plugin.entity;

/**
 * 文件写入结果（真实写盘状态，供 MCP 等调用方精确感知每个文件的写入结果）
 *
 * @author makejava
 * @version 1.0.0
 * @since 2024/01/01
 */
public enum SaveResult {
    /**
     * 新建文件并写入成功
     */
    CREATED,
    /**
     * 覆盖已存在文件成功
     */
    OVERWRITTEN,
    /**
     * 跳过未写入（文件冲突拒绝覆盖 / 未开启写文件）
     */
    SKIPPED,
    /**
     * 写入失败（目录或文件创建失败等）
     */
    FAILED
}
