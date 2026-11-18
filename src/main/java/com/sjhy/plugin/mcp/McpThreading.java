package com.sjhy.plugin.mcp;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * MCP 线程工具
 * <p>
 * MCP HTTP 线程非 EDT：PSI 读取需 ReadAction；VFS/PSI 写入需 EDT。
 *
 * @author makejava
 * @version 1.0.0
 * @since 2024/01/01
 */
public final class McpThreading {

    private McpThreading() {
        throw new UnsupportedOperationException();
    }

    /**
     * 在读操作中执行（可重入，已处于读操作中则直接执行）
     *
     * @param supplier 逻辑
     * @param <T>      返回类型
     * @return 执行结果
     */
    public static <T> T read(Supplier<T> supplier) {
        return ReadAction.compute(supplier::get);
    }

    /**
     * 在 EDT 上同步执行（invokeAndWait，ModalityState.NON_MODAL：write-safe 上下文，
     * 供后续写操作使用；若调用时有模态对话框打开，任务会等对话框关闭后执行）
     *
     * @param supplier 逻辑
     * @param <T>      返回类型
     * @return 执行结果
     */
    public static <T> T onEdt(Supplier<T> supplier) {
        if (ApplicationManager.getApplication().isDispatchThread()) {
            return supplier.get();
        }
        AtomicReference<T> reference = new AtomicReference<>();
        ApplicationManager.getApplication().invokeAndWait(() -> reference.set(supplier.get()), ModalityState.NON_MODAL);
        return reference.get();
    }
}
