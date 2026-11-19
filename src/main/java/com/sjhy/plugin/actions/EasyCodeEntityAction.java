package com.sjhy.plugin.actions;

import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifier;
import com.sjhy.plugin.tool.CacheDataUtils;
import com.sjhy.plugin.ui.SelectSavePath;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 从Java类生成代码菜单
 *
 * @author Mario Luo
 */
public class EasyCodeEntityAction extends AnAction {

    private final CacheDataUtils cacheDataUtils = CacheDataUtils.getInstance();

    /**
     * update() 中需要读取项目模型（模块/文件索引），必须声明在后台线程执行，
     * 否则 2024.2+ 平台会在 EDT 上请求 'module' 时抛出
     * "'module' is requested on EDT ... See ActionUpdateThread javadoc"。
     */
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }

        // 过滤选择Java文件
        VirtualFile[] psiFiles = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (psiFiles == null) {
            return;
        }

        // 获取选中的类（PSI 访问需要读锁）
        List<PsiClass> psiClassList = ReadAction.compute(
                (ThrowableComputable<List<PsiClass>, RuntimeException>) () -> resolvePsiClassByFile(project, psiFiles));
        if (psiClassList.size() == 0) {
            return;
        }

        // 缓存选中值
        cacheDataUtils.setSelectPsiClass(psiClassList.get(0));
        cacheDataUtils.setPsiClassList(psiClassList);
        new SelectSavePath(project, true).show();
    }

    /**
     * 解析类
     */
    private List<PsiClass> resolvePsiClassByFile(Project project, VirtualFile[] psiFiles) {
        PsiManager psiManager = PsiManager.getInstance(project);
        List<PsiJavaFile> psiJavaFiles = Arrays.stream(psiFiles)
                .filter(f -> f != null && !f.isDirectory())
                .map(psiManager::findFile)
                .filter(f -> f instanceof PsiJavaFile)
                .map(f -> (PsiJavaFile) f)
                .collect(Collectors.toList());
        List<PsiClass> psiClassList = Lists.newArrayListWithCapacity(psiJavaFiles.size());
        for (PsiJavaFile psiJavaFile : psiJavaFiles) {
            Arrays.stream(psiJavaFile.getClasses())
                    .filter(o -> o.getModifierList() != null && o.getModifierList().hasModifierProperty(PsiModifier.PUBLIC))
                    .findFirst().ifPresent(psiClassList::add);
        }
        return psiClassList;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getData(CommonDataKeys.PROJECT);
        // 统一走 event.getData()，不要使用 event.getDataContext().getData() 绕过数据快照
        VirtualFile[] files = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (project == null || files == null || files.length == 0) {
            event.getPresentation().setVisible(false);
            return;
        }

        // 非java的文件不显示；存在模块不展示：选择多个模块
        ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
        Module module = null;
        for (VirtualFile file : files) {
            if (file == null || (!file.isDirectory() && !"java".equals(file.getExtension()))) {
                event.getPresentation().setVisible(false);
                return;
            }
            Module fileModule = fileIndex.getModuleForFile(file);
            if (fileModule == null || (module != null && module != fileModule)) {
                event.getPresentation().setVisible(false);
                return;
            }
            module = fileModule;
        }
        event.getPresentation().setVisible(true);
    }


}
