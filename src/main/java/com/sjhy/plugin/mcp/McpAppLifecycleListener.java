package com.sjhy.plugin.mcp;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * MCP 服务启动监听器
 * <p>
 * IDE 启动（主窗口创建）时触发应用级 {@link McpServerService} 实例化（幂等：服务为应用级单例）。
 * 使用 AppLifecycleListener 而非已废弃的 StartupActivity，保证在新版 IDE 上的长期可用性。
 * 启动异常不影响 IDE。
 *
 * @author makejava
 * @version 1.0.0
 * @since 2024/01/01
 */
public class McpAppLifecycleListener implements AppLifecycleListener {

    private static final Logger LOG = Logger.getInstance(McpAppLifecycleListener.class);

    @Override
    public void appFrameCreated(@NotNull List<String> commandLineArgs) {
        try {
            ApplicationManager.getApplication().getService(McpServerService.class);
        } catch (Throwable e) {
            LOG.error("Failed to initialize EasyCode MCP server service", e);
        }
    }
}
