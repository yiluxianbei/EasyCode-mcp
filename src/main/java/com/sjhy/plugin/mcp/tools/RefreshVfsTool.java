package com.sjhy.plugin.mcp.tools;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.sjhy.plugin.mcp.AbstractMcpTool;
import com.sjhy.plugin.mcp.McpThreading;
import com.sjhy.plugin.mcp.TableResolver;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP 工具：刷新 VFS（虚拟文件系统）指定路径
 * <p>
 * 使 VFS 缓存与磁盘实际状态同步。适用场景：文件在 IDE 外部（命令行/脚本/agent 工具）
 * 被创建、修改或删除后，VFS 仍是旧状态，导致 generate_code 误判文件已存在（skipped）
 * 或读到陈旧内容。在磁盘状态变更后、调用 generate_code 前执行本工具即可。
 * <p>
 * 刷新在 EDT 上同步执行（VFS 刷新内部需要写锁）。
 *
 * @author makejava
 * @version 1.0.0
 * @since 2024/01/01
 */
public class RefreshVfsTool extends AbstractMcpTool {

    @Override
    public String getName() {
        return "refresh_vfs";
    }

    @Override
    public String getDescription() {
        return "刷新 IDEA VFS（虚拟文件系统）中指定路径的缓存，使其与磁盘实际状态同步。"
                + "适用场景：文件在 IDE 外部被创建/修改/删除（命令行、脚本、agent 写文件）后，"
                + "VFS 仍是旧状态，会导致 generate_code 误判文件已存在（skipped）或读不到新文件。"
                + "建议在磁盘状态变更后、调用 generate_code 前执行。recursive=true 时递归刷新整个子目录。"
                + "返回刷新前后的 VFS 可见性状态，对比 vfsExistsBefore 与 vfsExistsAfter 可确认刷新是否生效。"
                + "参数校验失败或执行出错时返回 isError 响应，text 为 {\"error\": 原因}。";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("path", stringProp("要刷新的路径（绝对路径，或 ./ 开头的项目相对路径）"));
        properties.put("recursive", booleanProp("是否递归刷新子目录（默认 true）"));
        return schema(properties, list("path"));
    }

    @Override
    public Map<String, Object> getOutputSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("path", stringProp("实际刷新的绝对路径"));
        properties.put("diskExists", booleanProp("刷新后该路径在磁盘上是否存在"));
        properties.put("vfsExistsBefore", booleanProp("刷新前 VFS 中是否可见该路径"));
        properties.put("vfsExistsAfter", booleanProp("刷新后 VFS 中是否可见该路径"));
        properties.put("recursive", booleanProp("是否递归刷新子目录"));
        return schema(properties, list("path", "diskExists", "vfsExistsBefore", "vfsExistsAfter", "recursive"));
    }

    @Override
    public Object call(Map<String, Object> args) {
        String rawPath = str(args.get("path"));
        if (rawPath == null || rawPath.isEmpty()) {
            throw new IllegalArgumentException("参数 path 为必填项");
        }
        final boolean recursive = !Boolean.FALSE.equals(args.get("recursive"));
        String path = rawPath.replace("\\", "/");
        // ./ 相对路径与 . 均转换为项目根绝对路径
        String basePath = TableResolver.requireProject().getBasePath();
        if (basePath != null) {
            if (".".equals(path)) {
                path = basePath.replace("\\", "/");
            } else if (path.startsWith("./")) {
                path = basePath.replace("\\", "/") + path.substring(1);
            }
        }
        final String finalPath = path;
        final File ioFile = new File(finalPath);
        // EDT 上同步刷新（VFS 内部需要写锁，须 write-safe 上下文）
        return McpThreading.onEdt(() -> {
            // 刷新前的 VFS 状态
            VirtualFile before = LocalFileSystem.getInstance().findFileByPath(finalPath);
            // 同步刷新目标路径（磁盘不存在时返回 null）
            VirtualFile target = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
            VirtualFile effective = target;
            if (target == null) {
                // 目标在磁盘不存在：刷新父目录以清除陈旧子节点
                File parentIo = ioFile.getParentFile();
                if (parentIo != null) {
                    effective = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(parentIo);
                }
            }
            // 递归刷新子树（父目录兜底刷新时无论是否递归都执行，确保陈旧子节点被清除）
            if (effective != null && (recursive || target == null)) {
                effective.refresh(false, recursive);
            }
            VirtualFile after = LocalFileSystem.getInstance().findFileByPath(finalPath);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", finalPath);
            result.put("diskExists", ioFile.exists());
            result.put("vfsExistsBefore", before != null);
            result.put("vfsExistsAfter", after != null);
            result.put("recursive", recursive);
            return result;
        });
    }

    /**
     * 转字符串（null 透传）
     *
     * @param value 值
     * @return 字符串
     */
    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
