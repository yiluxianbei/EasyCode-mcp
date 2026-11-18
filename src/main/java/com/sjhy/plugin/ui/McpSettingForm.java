package com.sjhy.plugin.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.sjhy.plugin.dto.SettingsStorageDTO;
import com.sjhy.plugin.mcp.McpServerService;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Objects;
import javax.swing.*;

/**
 * MCP 服务设置页
 * <p>
 * 提供 MCP 服务开关与端口配置，应用后立即生效（重启内嵌服务）。
 * 纯 Swing 代码构建 UI，不依赖 UI Designer。
 *
 * @author makejava
 * @version 1.0.0
 * @since 2024/01/01
 */
public class McpSettingForm implements BaseSettings {

    /**
     * 主面板
     */
    private JPanel mainPanel;

    /**
     * MCP 开关
     */
    private JCheckBox mcpEnabledCheckBox;

    /**
     * 端口输入框
     */
    private JTextField mcpPortField;

    /**
     * 服务状态标签
     */
    private JLabel statusLabel;

    public McpSettingForm() {
        buildUi();
    }

    /**
     * 构建 UI
     */
    private void buildUi() {
        mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 12, 4, 12);
        gbc.anchor = GridBagConstraints.NORTHWEST;

        mcpEnabledCheckBox = new JCheckBox("启用 MCP 服务（AI Agent 通过 http://127.0.0.1:<端口>/mcp 调用代码生成）");
        mainPanel.add(mcpEnabledCheckBox, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(4, 12, 4, 12);
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        JPanel portPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        portPanel.add(new JLabel("端口："));
        mcpPortField = new JTextField(6);
        portPanel.add(mcpPortField);
        portPanel.add(new JLabel("（范围 1-65535，默认 " + McpServerService.DEFAULT_PORT + "）"));
        mainPanel.add(portPanel, gbc);

        gbc.gridy++;
        statusLabel = new JLabel(" ");
        mainPanel.add(statusLabel, gbc);

        gbc.gridy++;
        gbc.weighty = 1.0;
        JLabel hintLabel = new JLabel("<html>修改开关或端口后点击 Apply/OK 生效；仅监听本机 127.0.0.1，无鉴权，请勿将端口暴露到外网。</html>");
        mainPanel.add(hintLabel, gbc);
    }

    @Override
    public String getDisplayName() {
        return "MCP";
    }

    @Override
    public JComponent createComponent() {
        loadSettingsStore();
        updateStatus();
        return mainPanel;
    }

    @Override
    public boolean isModified() {
        SettingsStorageDTO storage = getSettingsStorage();
        if (mcpEnabledCheckBox.isSelected() != McpServerService.isMcpEnabled()) {
            return true;
        }
        return !Objects.equals(parsePort(), currentPort(storage));
    }

    @Override
    public void apply() throws ConfigurationException {
        Integer port = parsePort();
        if (port == null || port < 1 || port > 65535) {
            throw new ConfigurationException("MCP 端口必须为 1-65535 范围内的数字");
        }
        SettingsStorageDTO storage = getSettingsStorage();
        storage.setMcpEnabled(mcpEnabledCheckBox.isSelected());
        storage.setMcpPort(port);
        // 应用后立即生效
        ApplicationManager.getApplication().getService(McpServerService.class).restart();
        updateStatus();
    }

    /**
     * 加载配置信息
     *
     * @param settingsStorage 配置信息
     */
    @Override
    public void loadSettingsStore(@NotNull SettingsStorageDTO settingsStorage) {
        mcpEnabledCheckBox.setSelected(McpServerService.isMcpEnabled());
        mcpPortField.setText(String.valueOf(currentPort(settingsStorage)));
    }

    /**
     * 刷新服务状态显示
     */
    private void updateStatus() {
        McpServerService service = ApplicationManager.getApplication().getService(McpServerService.class);
        if (!McpServerService.isMcpEnabled()) {
            statusLabel.setText("当前状态：已禁用（设置中关闭）");
        } else if (service.isStarted()) {
            statusLabel.setText("当前状态：运行中（http://127.0.0.1:" + service.getPort() + McpServerService.PATH + "）");
        } else {
            statusLabel.setText("当前状态：未运行（端口可能被其它 IDE 实例占用）");
        }
    }

    /**
     * 解析端口输入（非法返回 null）
     *
     * @return 端口或 null
     */
    private Integer parsePort() {
        try {
            return Integer.parseInt(mcpPortField.getText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 读取配置中的端口（缺省回退默认值）
     *
     * @param storage 配置
     * @return 端口
     */
    private static int currentPort(SettingsStorageDTO storage) {
        Integer port = storage.getMcpPort();
        return port == null ? McpServerService.DEFAULT_PORT : port;
    }
}
