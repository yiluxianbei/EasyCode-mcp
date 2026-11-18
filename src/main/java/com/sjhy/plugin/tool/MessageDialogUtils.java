package com.sjhy.plugin.tool;

import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import com.sjhy.plugin.dict.GlobalDict;
import com.intellij.openapi.ui.Messages;

import javax.swing.*;

/**
 * 消息弹框工具类
 * 对message dialog弹框进行兼容处理
 *
 * @author makejava
 * @version 1.0.0
 * @since 2021/02/01 15:36
 */
public class MessageDialogUtils {

    /**
     * yes no确认框
     *
     * @param msg 消息
     * @return 是否确认
     */
    public static boolean yesNo(String msg) {
        return yesNo(null, msg);
    }

    /**
     * yes no确认框
     * <p>使用平台 Messages API：对话框进入 IDEA modality 栈，关闭窗口（点X）等价于选 No。
     *
     * @param project 项目对象
     * @param msg     消息
     * @return 是否确认
     */
    public static boolean yesNo(Project project, String msg) {
        return Messages.showYesNoDialog(project, msg, GlobalDict.TITLE_INFO, "Yes", "No", UIUtil.getQuestionIcon()) == Messages.YES;
    }

    /**
     * 显示确认框
     * <p>使用平台 Messages API：对话框进入 IDEA modality 栈，点关闭按钮（X）等价于 Cancel。
     *
     * @param msg     确认框消息
     * @param project 项目
     * @return 点击按钮
     */
    public static int yesNoCancel(Project project, String msg, String yesText, String noText, String cancelText) {
        return Messages.showYesNoCancelDialog(project, msg, GlobalDict.TITLE_INFO,
                yesText, noText, cancelText, UIUtil.getQuestionIcon());
    }

}
