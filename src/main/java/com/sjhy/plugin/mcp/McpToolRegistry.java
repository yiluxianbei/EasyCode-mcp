package com.sjhy.plugin.mcp;

import com.sjhy.plugin.mcp.tools.GenerateCodeTool;
import com.sjhy.plugin.mcp.tools.GetGlobalConfigTool;
import com.sjhy.plugin.mcp.tools.GetTemplateTool;
import com.sjhy.plugin.mcp.tools.ListDatasourcesTool;
import com.sjhy.plugin.mcp.tools.ListTablesTool;
import com.sjhy.plugin.mcp.tools.ListTemplatesTool;
import com.sjhy.plugin.mcp.tools.PreviewCodeTool;
import com.sjhy.plugin.mcp.tools.RefreshVfsTool;
import com.sjhy.plugin.mcp.tools.TableDetailTool;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP 工具注册表
 * <p>
 * 集中注册所有暴露给 AI Agent 的 MCP 工具。
 *
 * @author makejava
 * @version 1.0.0
 * @since 2024/01/01
 */
public class McpToolRegistry {

    /**
     * 已注册的工具列表（顺序即 tools/list 返回顺序）
     */
    private final List<McpTool> tools = new ArrayList<>();

    public McpToolRegistry() {
        register(new ListDatasourcesTool());
        register(new ListTablesTool());
        register(new TableDetailTool());
        register(new ListTemplatesTool());
        register(new GetTemplateTool());
        register(new GetGlobalConfigTool());
        register(new PreviewCodeTool());
        register(new GenerateCodeTool());
        register(new RefreshVfsTool());
    }

    /**
     * 注册工具（重名忽略后者）
     *
     * @param tool 工具实例
     */
    public void register(McpTool tool) {
        if (getTool(tool.getName()) == null) {
            tools.add(tool);
        }
    }

    /**
     * 获取全部工具
     *
     * @return 工具列表
     */
    public List<McpTool> getTools() {
        return tools;
    }

    /**
     * 按名称查找工具
     *
     * @param name 工具名
     * @return 工具实例，不存在返回 null
     */
    public McpTool getTool(String name) {
        if (name == null) {
            return null;
        }
        for (McpTool tool : tools) {
            if (name.equals(tool.getName())) {
                return tool;
            }
        }
        return null;
    }
}
