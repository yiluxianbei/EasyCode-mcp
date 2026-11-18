package com.sjhy.plugin.mcp;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具声明规范测试
 * <p>
 * 验证 tools/list 输出中的工具声明满足 MCP 规范与 codemode 脚本编写约定：
 * 每个工具必须声明 outputSchema（顶层 type=object，字段级描述在 schema 的 description 中），
 * description 保留 schema 表达不了的语义信息，其中失败形态（isError）说明必须存在。
 *
 * @author makejava
 * @version 1.0.0
 * @since 2024/01/01
 */
public class McpToolDeclarationTest {

    /**
     * 通过协议层完整链路验证 tools/list 输出
     */
    @Test
    public void toolsListShouldContainOutputSchema() {
        McpProtocolHandler handler = new McpProtocolHandler(new McpToolRegistry());
        Map<String, Object> request = request(1, "tools/list");
        Map<String, Object> response = handler.handle(request);
        assertNotNull(response);
        assertEquals("2.0", response.get("jsonrpc"));
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
        assertEquals(7, tools.size());
        for (Map<String, Object> tool : tools) {
            String name = String.valueOf(tool.get("name"));
            @SuppressWarnings("unchecked")
            Map<String, Object> outputSchema = (Map<String, Object>) tool.get("outputSchema");
            assertNotNull("tool " + name + " missing outputSchema", outputSchema);
            assertEquals("tool " + name + " outputSchema top-level type must be object",
                    "object", outputSchema.get("type"));
            assertNotNull("tool " + name + " outputSchema has no properties", outputSchema.get("properties"));
        }
    }

    /**
     * 每个工具的 description 必须说明失败形态（isError），
     * 结构性信息（字段名/类型/枚举）由 outputSchema 承担，不在 description 中重复罗列
     */
    @Test
    public void descriptionShouldDocumentErrorForm() {
        for (McpTool tool : new McpToolRegistry().getTools()) {
            String description = tool.getDescription();
            assertNotNull(description);
            assertTrue("tool " + tool.getName() + " description 未说明失败形态",
                    description.contains("isError"));
            assertFalse("tool " + tool.getName() + " description 不应逐字段罗列返回结构（结构性信息由 outputSchema 承担）",
                    description.contains("返回结构：{"));
        }
    }

    /**
     * 生成 JSON-RPC 请求
     */
    private Map<String, Object> request(Object id, String method) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.put("params", new ArrayList<>());
        return request;
    }
}
