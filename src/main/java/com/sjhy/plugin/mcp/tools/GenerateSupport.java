package com.sjhy.plugin.mcp.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.intellij.database.psi.DbTable;
import com.intellij.database.util.DasUtil;
import com.intellij.database.util.DbUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.ReflectionUtil;
import com.sjhy.plugin.dto.GenerateOptions;
import com.sjhy.plugin.dto.SettingsStorageDTO;
import com.sjhy.plugin.entity.Callback;
import com.sjhy.plugin.entity.ColumnInfo;
import com.sjhy.plugin.entity.FileWriteResult;
import com.sjhy.plugin.entity.TableInfo;
import com.sjhy.plugin.entity.Template;
import com.sjhy.plugin.entity.TypeMapper;
import com.sjhy.plugin.enums.MatchType;
import com.sjhy.plugin.mcp.McpThreading;
import com.sjhy.plugin.mcp.TableResolver;
import com.sjhy.plugin.service.CodeGenerateService;
import com.sjhy.plugin.service.TableInfoSettingsService;
import com.sjhy.plugin.service.impl.CodeGenerateServiceImpl;
import com.sjhy.plugin.tool.CloneUtils;
import com.sjhy.plugin.tool.CompatibleUtils;
import com.sjhy.plugin.tool.CurrGroupUtils;
import com.sjhy.plugin.tool.GlobalTool;
import com.sjhy.plugin.tool.ModuleUtils;
import com.sjhy.plugin.tool.NameUtils;
import com.sjhy.plugin.tool.StringUtils;
import com.sjhy.plugin.tool.TemplateUtils;
import com.sjhy.plugin.tool.TimeUtils;
import com.sjhy.plugin.tool.VelocityUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 代码生成请求支撑（preview_code 与 generate_code 完全共用）
 * <p>
 * 职责：参数解析与 headless 校验（模板组/模板存在性、类型映射完整性、保存路径/模块）、
 * 表信息构建（统一配置语义与 UI 一致）、模板渲染（复刻服务批量生成循环）、生成写盘编排。
 *
 * @author makejava
 * @version 1.0.0
 * @since 2024/01/01
 */
public final class GenerateSupport {

    private GenerateSupport() {
        throw new UnsupportedOperationException();
    }

    /**
     * 解析后的生成请求
     */
    static class GenerateRequest {
        String datasource;
        String templateGroup;
        List<String> tableNames;
        List<String> templateNames;
        String saveModelName;
        String savePackageName;
        String savePath;
        String preName;
        boolean unifiedConfig;
        boolean overwrite;
        boolean reFormat;

        /**
         * 解析结果（读操作中构建）：选中表引用、全部表引用、克隆并注入全局变量的模板
         */
        TableResolver.TableRef selectedRef;
        List<TableResolver.TableRef> tableRefs;
        List<Template> templates;
    }

    /**
     * 单次渲染产物
     */
    static class RenderedFile {
        String table;
        String className;
        String template;
        String code;
        String fileName;
        String dir;
        String fullPath;
    }

    /**
     * 解析并校验请求（须在 ReadAction 中调用）
     *
     * @param args 工具入参
     * @return 解析结果
     */
    static GenerateRequest parse(Map<String, Object> args) {
        GenerateRequest request = new GenerateRequest();
        request.datasource = requiredString(args, "datasource");
        request.templateGroup = requiredString(args, "templateGroup");
        request.tableNames = stringList(args, "tables");
        request.templateNames = stringList(args, "templates");
        request.saveModelName = requiredString(args, "saveModelName");
        request.savePackageName = requiredString(args, "savePackageName");
        request.savePath = requiredString(args, "savePath");
        request.preName = optionalString(args, "preName");
        request.unifiedConfig = Boolean.TRUE.equals(args.get("unifiedConfig"));
        // overwrite 可选（默认 false）：preview_code 无需传入；generate_code 在工具层自行校验必填
        request.overwrite = Boolean.TRUE.equals(args.get("overwrite"));
        request.reFormat = Boolean.TRUE.equals(args.get("reFormat"));

        // 模板组必须存在
        SettingsStorageDTO storage = com.sjhy.plugin.service.SettingsStorageService.getSettingsStorage();
        if (storage.getTemplateGroupMap() == null || !storage.getTemplateGroupMap().containsKey(request.templateGroup)) {
            throw new IllegalArgumentException("模板分组不存在: " + request.templateGroup
                    + "，可用分组: " + (storage.getTemplateGroupMap() == null ? new ArrayList<>() : storage.getTemplateGroupMap().keySet())
                    + "（可用 list_templates 查询）");
        }
        // 组内模板必须存在
        List<Template> groupTemplates = storage.getTemplateGroupMap().get(request.templateGroup).getElementList();
        List<String> available = new ArrayList<>();
        for (Template template : groupTemplates) {
            available.add(template.getName());
        }
        List<Template> templates = new ArrayList<>();
        for (String templateName : request.templateNames) {
            Template matched = null;
            for (Template template : groupTemplates) {
                if (templateName.equals(template.getName())) {
                    matched = template;
                    break;
                }
            }
            if (matched == null) {
                throw new IllegalArgumentException("模板 " + templateName + " 不存在于分组 " + request.templateGroup
                        + "，该分组可用模板: " + available);
            }
            templates.add(matched);
        }
        // 克隆并注入全局变量（与批量生成一致，防止篡改原配置）
        List<Template> cloned = CloneUtils.cloneByJson(templates, new TypeReference<ArrayList<Template>>() {
        });
        TemplateUtils.addGlobalConfig(cloned);
        request.templates = cloned;

        // 解析表
        request.tableRefs = new ArrayList<>();
        for (String tableName : request.tableNames) {
            request.tableRefs.add(TableResolver.resolveTable(request.datasource, null, tableName));
        }
        request.selectedRef = request.tableRefs.get(0);

        // 保存路径处理（与 UI 一致：项目内绝对路径转为 ./ 相对路径）
        Project project = TableResolver.requireProject();
        String basePath = project.getBasePath();
        String savePath = request.savePath.replace("\\", "/");
        if (!StringUtils.isEmpty(basePath) && savePath.startsWith(basePath.replace("\\", "/"))) {
            if (savePath.length() > basePath.length()) {
                if ("/".equals(savePath.substring(basePath.length(), basePath.length() + 1))) {
                    savePath = savePath.replace(basePath, ".");
                }
            } else {
                savePath = savePath.replace(basePath, ".");
            }
        }
        request.savePath = savePath;

        // 模块校验
        if (!StringUtils.isEmpty(request.saveModelName)) {
            Module module = ModuleManager.getInstance(project).findModuleByName(request.saveModelName);
            if (module == null) {
                List<String> moduleNames = new ArrayList<>();
                for (Module item : ModuleManager.getInstance(project).getModules()) {
                    moduleNames.add(item.getName());
                }
                throw new IllegalArgumentException("模块不存在: " + request.saveModelName + "，可用模块: " + moduleNames);
            }
        }
        return request;
    }

    /**
     * 构建表信息并应用保存配置（unifiedConfig 语义与 UI 一致，仅在内存中生效）
     * <p>
     * 顺序：全部表先按持久化配置读取 → 选中表应用传入配置 → unifiedConfig=true 时覆盖全部表，
     * false 时仅覆盖"从未配置过保存路径"的表 → 表名去前缀重命名。
     *
     * @param request 请求
     * @return 表信息列表
     */
    static List<TableInfo> buildTableInfos(GenerateRequest request) {
        List<TableInfo> tableInfos = new ArrayList<>();
        TableInfo selected = null;
        List<Integer> emptyPathIndexes = new ArrayList<>();
        for (int i = 0; i < request.tableRefs.size(); i++) {
            TableResolver.TableRef ref = request.tableRefs.get(i);
            TableInfo tableInfo = TableInfoSettingsService.getInstance().getTableInfo(ref.getDbTable());
            if (StringUtils.isEmpty(tableInfo.getSavePath())) {
                emptyPathIndexes.add(i);
            }
            if (i == 0) {
                selected = tableInfo;
            }
            tableInfos.add(tableInfo);
        }
        // 选中表始终应用传入配置
        applySaveConfig(selected, request);
        // 其余表按统一配置语义处理
        for (int i = 1; i < tableInfos.size(); i++) {
            if (request.unifiedConfig || emptyPathIndexes.contains(i)) {
                applySaveConfig(tableInfos.get(i), request);
            }
        }
        // 表名去前缀（与批量生成循环一致）
        for (TableInfo tableInfo : tableInfos) {
            renameByPreName(tableInfo);
        }
        return tableInfos;
    }

    /**
     * 应用保存配置到表信息
     *
     * @param tableInfo 表信息
     * @param request   请求
     */
    private static void applySaveConfig(TableInfo tableInfo, GenerateRequest request) {
        tableInfo.setSavePath(request.savePath);
        tableInfo.setSavePackageName(request.savePackageName);
        tableInfo.setPreName(request.preName);
        tableInfo.setTemplateGroupName(request.templateGroup);
        if (!StringUtils.isEmpty(request.saveModelName)) {
            tableInfo.setSaveModelName(request.saveModelName);
        }
    }

    /**
     * 表名去前缀重命名（复刻批量生成循环逻辑）
     *
     * @param tableInfo 表信息
     */
    private static void renameByPreName(TableInfo tableInfo) {
        if (!StringUtils.isEmpty(tableInfo.getPreName()) && tableInfo.getObj().getName().startsWith(tableInfo.getPreName())) {
            String newName = tableInfo.getObj().getName().substring(tableInfo.getPreName().length());
            tableInfo.setName(NameUtils.getInstance().getClassName(newName));
        }
    }

    /**
     * 渲染全部 表×模板 组合（不写盘，preview_code 专用；嵌套 $generateService.run 在预览模式下同样不写盘）
     *
     * @param request    请求
     * @param tableInfos 表信息列表
     * @return 渲染产物列表
     */
    static List<RenderedFile> renderAll(GenerateRequest request, List<TableInfo> tableInfos, GenerateOptions options) {
        Project project = TableResolver.requireProject();
        CodeGenerateService codeGenerateService = CodeGenerateService.getInstance(project);
        List<RenderedFile> rendered = new ArrayList<>();
        for (TableInfo tableInfo : tableInfos) {
            // 嵌套生成（模板内 $generateService.run）的渲染产物收集器
            List<FileWriteResult> nestedResults = new ArrayList<>();
            Map<String, Object> param = buildParam(project, codeGenerateService, tableInfo, tableInfos, options, nestedResults);
            for (Template template : request.templates) {
                Callback callback = new Callback();
                callback.setWriteFile(false);
                callback.setReformat(false);
                callback.setFileName(tableInfo.getName() + "Default.java");
                callback.setSavePath(tableInfo.getSavePath());
                param.put("callback", callback);
                String code = VelocityUtils.generate(template.getCode(), param);
                // 渲染为空的结果不作为产物返回（模板条件分支可能整体为空）
                if (StringUtils.isEmpty(code) || code.isBlank()) {
                    continue;
                }
                RenderedFile file = new RenderedFile();
                file.table = tableInfo.getObj() == null ? tableInfo.getName() : tableInfo.getObj().getName();
                file.className = tableInfo.getName();
                file.template = template.getName();
                file.code = code;
                file.fileName = callback.getFileName();
                String path = callback.getSavePath() == null ? "" : callback.getSavePath().replace("\\", "/");
                if (path.startsWith(".")) {
                    path = project.getBasePath() + path.substring(1);
                }
                file.dir = path;
                file.fullPath = joinPath(path, callback.getFileName());
                rendered.add(file);
            }
            // 汇入嵌套生成（模板内 $generateService.run）的产物，预览模式下同样不写盘
            for (FileWriteResult nested : nestedResults) {
                RenderedFile file = new RenderedFile();
                file.table = nested.getTableName() == null ? tableInfo.getName() : nested.getTableName();
                file.className = nested.getClassName();
                file.template = nested.getTemplate();
                file.code = nested.getCode();
                file.fileName = nested.getFileName();
                String path = nested.getSavePath() == null ? "" : nested.getSavePath().replace("\\", "/");
                if (path.startsWith(".")) {
                    path = project.getBasePath() + path.substring(1);
                }
                file.dir = path;
                file.fullPath = joinPath(path, file.fileName);
                rendered.add(file);
            }
        }
        return rendered;
    }

    /**
     * 构建渲染参数（复刻服务批量生成循环的参数装配）
     *
     * @param project            项目
     * @param codeGenerateService 生成服务
     * @param tableInfo          当前表信息
     * @param tableInfos         全部表信息
     * @param options            生成选项
     * @param nestedResults      嵌套渲染产物收集器（模板内 $generateService.run 产生）
     * @return 参数 Map
     */
    private static Map<String, Object> buildParam(Project project, CodeGenerateService codeGenerateService,
                                                   TableInfo tableInfo, List<TableInfo> tableInfos, GenerateOptions options,
                                                   List<FileWriteResult> nestedResults) {
        SettingsStorageDTO settings = com.sjhy.plugin.service.SettingsStorageService.getSettingsStorage();
        Map<String, Object> param = new HashMap<>(20);
        param.put("author", settings.getAuthor());
        param.put("tool", GlobalTool.getInstance());
        param.put("time", TimeUtils.getInstance());
        param.put("projectPath", project.getBasePath());
        param.put("dbUtil", ReflectionUtil.newInstance(DbUtil.class));
        param.put("dasUtil", ReflectionUtil.newInstance(DasUtil.class));
        param.put("tableInfoList", tableInfos);
        param.put("tableInfo", tableInfo);
        // 模块路径与导包列表
        if (!StringUtils.isEmpty(tableInfo.getSaveModelName())) {
            Module module = ModuleManager.getInstance(project).findModuleByName(tableInfo.getSaveModelName());
            if (module != null) {
                param.put("modulePath", ModuleUtils.getModuleDir(module).getPath());
            }
        }
        Set<String> importList = new TreeSet<>();
        if (tableInfo.getFullColumn() != null) {
            for (ColumnInfo columnInfo : tableInfo.getFullColumn()) {
                if (columnInfo.getType() == null || columnInfo.getType().startsWith("java.lang")) {
                    continue;
                }
                importList.add(NameUtils.getInstance().getClsFullNameRemoveGeneric(columnInfo.getType()));
            }
        }
        param.put("importList", importList);
        // 挂嵌套产物收集器：preview 下 $generateService.run 生成的附加文件（含代码内容）汇入并回传
        param.put("generateService", new com.sjhy.plugin.tool.ExtraCodeGenerateUtils(
                (CodeGenerateServiceImpl) codeGenerateService, tableInfo, options, nestedResults));
        return param;
    }

    /**
     * 构建生成选项
     *
     * @param request    请求
     * @param forWrite   是否用于写盘
     * @return 生成选项
     */
    static GenerateOptions buildOptions(GenerateRequest request, boolean forWrite) {
        return GenerateOptions.builder()
                .entityModel(false)
                .unifiedConfig(request.unifiedConfig)
                .reFormat(forWrite && request.reFormat)
                .titleSure(forWrite && request.overwrite)
                .titleRefuse(forWrite && !request.overwrite)
                .silentMode(forWrite)
                .previewMode(!forWrite)
                .templateGroupName(request.templateGroup)
                .warnings(new ArrayList<>())
                .build();
    }

    /**
     * 校验全部表的类型映射完整性（无弹窗版本，复刻 MainAction.typeValidator）
     *
     * @param tableInfos 表信息列表
     * @return 未映射类型描述列表（空列表表示全部已映射）
     */
    static List<String> checkTypeMapping(List<TableInfo> tableInfos) {
        List<TypeMapper> typeMapperList = CurrGroupUtils.getCurrTypeMapperGroup().getElementList();
        Set<String> unmapped = new LinkedHashSet<>();
        for (TableInfo tableInfo : tableInfos) {
            if (tableInfo.getFullColumn() == null) {
                continue;
            }
            for (ColumnInfo columnInfo : tableInfo.getFullColumn()) {
                if (columnInfo.getObj() == null) {
                    // 自定义附加列无数据库类型
                    continue;
                }
                String typeName;
                try {
                    typeName = CompatibleUtils.getDataType(columnInfo.getObj()).getSpecification();
                } catch (Exception e) {
                    continue;
                }
                boolean mapped = false;
                for (TypeMapper typeMapper : typeMapperList) {
                    try {
                        if (typeMapper.getMatchType() == MatchType.ORDINARY) {
                            if (typeName.equalsIgnoreCase(typeMapper.getColumnType())) {
                                mapped = true;
                                break;
                            }
                        } else {
                            if (Pattern.compile(typeMapper.getColumnType(), Pattern.CASE_INSENSITIVE).matcher(typeName).matches()) {
                                mapped = true;
                                break;
                            }
                        }
                    } catch (PatternSyntaxException ignored) {
                        // 正则语法错误按未匹配处理
                    }
                }
                if (!mapped) {
                    unmapped.add(tableInfo.getName() + "." + columnInfo.getName() + "（" + typeName + "）");
                }
            }
        }
        return new ArrayList<>(unmapped);
    }

    /**
     * 持久化选中表配置（复刻 SelectSavePath#onOK）
     *
     * @param request 请求
     */
    static void persistSelected(GenerateRequest request) {
        TableInfo selected = TableInfoSettingsService.getInstance().getTableInfo(request.selectedRef.getDbTable());
        applySaveConfig(selected, request);
        TableInfoSettingsService.getInstance().saveTableInfo(selected);
    }

    /**
     * 注入选中表到缓存（绕过 DatabaseView 选中）
     *
     * @param request 请求
     */
    static void injectSelection(GenerateRequest request) {
        List<DbTable> dbTables = new ArrayList<>();
        for (TableResolver.TableRef ref : request.tableRefs) {
            dbTables.add(ref.getDbTable());
        }
        com.sjhy.plugin.tool.CacheDataUtils cacheDataUtils = com.sjhy.plugin.tool.CacheDataUtils.getInstance();
        cacheDataUtils.setSelectDbTable(request.selectedRef.getDbTable());
        cacheDataUtils.setDbTableList(dbTables);
    }

    /**
     * 拼接路径
     *
     * @param dir      目录
     * @param fileName 文件名
     * @return 完整路径
     */
    static String joinPath(String dir, String fileName) {
        if (dir.endsWith("/")) {
            return dir + fileName;
        }
        return dir + "/" + fileName;
    }

    /**
     * 必填字符串参数
     *
     * @param args 参数
     * @param name 参数名
     * @return 值
     */
    static String requiredString(Map<String, Object> args, String name) {
        String value = optionalString(args, name);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("参数 " + name + " 为必填项");
        }
        return value;
    }

    /**
     * 必填布尔参数
     *
     * @param args 参数
     * @param name 参数名
     * @return 值
     */
    static boolean requiredBoolean(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value == null) {
            throw new IllegalArgumentException("参数 " + name + " 为必填项");
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * 可选字符串参数
     *
     * @param args 参数
     * @param name 参数名
     * @return 值（缺省 null）
     */
    static String optionalString(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value == null) {
            return null;
        }
        String str = String.valueOf(value);
        return str.isEmpty() ? null : str;
    }

    /**
     * 字符串列表参数（兼容单值）
     *
     * @param args 参数
     * @param name 参数名
     * @return 列表
     */
    static List<String> stringList(Map<String, Object> args, String name) {
        Object value = args.get(name);
        List<String> result = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item != null && !String.valueOf(item).isEmpty()) {
                    result.add(String.valueOf(item));
                }
            }
        } else if (value instanceof String && !((String) value).isEmpty()) {
            result.add((String) value);
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("参数 " + name + " 为必填项（至少一个元素）");
        }
        return result;
    }
}
