package com.sjhy.plugin.tool;

import com.sjhy.plugin.entity.GlobalConfig;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.Arrays;

import static org.junit.Assert.*;
import com.sjhy.plugin.entity.Template;
import com.sjhy.plugin.entity.TemplateGroup;

/**
 * 模板工具测试用例
 *
 * @author makejava
 * @version 1.0.0
 * @since 2018/09/01 15:28
 */
public class TemplateUtilsTest {

    /**
     * 测试向模板中添加全局配置
     */
    @Test
    public void addGlobalConfig() {
        GlobalConfig globalConfig = new GlobalConfig("init", "Hello World!");
        String template = "$init,${init}";
        // 添加全局变量
        template = TemplateUtils.addGlobalConfig(template, Collections.singleton(globalConfig));
        Assert.assertEquals(template, "Hello World!,Hello World!");
    }

    /**
     * 测试在模板组内按名称查找模板
     */
    @Test
    public void findTemplate() {
        TemplateGroup templateGroup = new TemplateGroup();
        templateGroup.setName("bn-api");
        templateGroup.setElementList(Arrays.asList(
                new Template("entity.java.vm", "entity"),
                new Template("enum.java.vm", "enum")));
        // 命中
        Template matched = TemplateUtils.findTemplate(templateGroup, "enum.java.vm");
        Assert.assertNotNull(matched);
        Assert.assertEquals("enum", matched.getCode());
        // 未命中
        Assert.assertNull(TemplateUtils.findTemplate(templateGroup, "service.java.vm"));
        // 模板组为 null
        Assert.assertNull(TemplateUtils.findTemplate(null, "enum.java.vm"));
        // 模板名为空
        Assert.assertNull(TemplateUtils.findTemplate(templateGroup, ""));
        Assert.assertNull(TemplateUtils.findTemplate(templateGroup, null));
        // 模板列表为空
        TemplateGroup emptyGroup = new TemplateGroup();
        emptyGroup.setElementList(Collections.emptyList());
        Assert.assertNull(TemplateUtils.findTemplate(emptyGroup, "enum.java.vm"));
    }

    /**
     * 测试嵌套模板未找到时的告警文案
     */
    @Test
    public void getMissingTemplateMessage() {
        TemplateGroup templateGroup = new TemplateGroup();
        templateGroup.setName("bn-service");
        templateGroup.setElementList(Arrays.asList(
                new Template("service.java.vm", "service"),
                new Template("mapper.java.vm", "mapper")));
        String message = TemplateUtils.getMissingTemplateMessage("bn-service", templateGroup, "enum.java.vm", "sys_user");
        Assert.assertTrue(message.contains("bn-service"));
        Assert.assertTrue(message.contains("enum.java.vm"));
        Assert.assertTrue(message.contains("sys_user"));
        Assert.assertTrue(message.contains("service.java.vm"));
        Assert.assertTrue(message.contains("mapper.java.vm"));
        // 分组不存在
        String missingGroupMessage = TemplateUtils.getMissingTemplateMessage("not-exists", null, "enum.java.vm", "sys_user");
        Assert.assertTrue(missingGroupMessage.contains("not-exists"));
        Assert.assertTrue(missingGroupMessage.contains("不存在"));
    }
}