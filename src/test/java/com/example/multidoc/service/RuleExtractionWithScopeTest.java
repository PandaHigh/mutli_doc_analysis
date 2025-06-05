package com.example.multidoc.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;

public class RuleExtractionWithScopeTest {

    /**
     * 验证extractRulesForCategory方法是否存在
     */
    @Test
    void testExtractRulesForCategoryMethodExists() {
        Method extractRulesMethod = null;
        try {
            // 遍历AnalysisService中的所有方法
            for (Method method : AnalysisService.class.getDeclaredMethods()) {
                if (method.getName().equals("extractRulesForCategory")) {
                    extractRulesMethod = method;
                    break;
                }
            }
            
            assertNotNull(extractRulesMethod, "extractRulesForCategory方法应该存在");
        } catch (Exception e) {
            fail("查找extractRulesForCategory方法时出错: " + e.getMessage());
        }
    }
    
    /**
     * 验证AnalysisService中是否使用DocumentScope
     */
    @Test
    void testAnalysisServiceUsesDocumentScope() throws Exception {
        boolean foundDocumentScopeRepo = false;
        for (java.lang.reflect.Field field : AnalysisService.class.getDeclaredFields()) {
            if (field.getName().equals("documentScopeRepository")) {
                foundDocumentScopeRepo = true;
                break;
            }
        }
        
        assertTrue(foundDocumentScopeRepo, "AnalysisService应该有documentScopeRepository字段");
    }
    
    /**
     * 验证数据库迁移文件是否存在
     */
    @Test
    void testMigrationFileExists() throws Exception {
        File migrationFile = new File("src/main/resources/db/migration/V5__add_document_scopes_table.sql");
        assertTrue(migrationFile.exists(), "数据库迁移文件V5__add_document_scopes_table.sql应该存在");
        
        // 读取文件内容
        String content = new String(Files.readAllBytes(migrationFile.toPath()));
        assertTrue(content.contains("CREATE TABLE document_scopes"), 
            "迁移文件应该包含创建document_scopes表的SQL语句");
        assertTrue(content.contains("scope_content"), 
            "迁移文件应该包含scope_content字段");
    }
} 