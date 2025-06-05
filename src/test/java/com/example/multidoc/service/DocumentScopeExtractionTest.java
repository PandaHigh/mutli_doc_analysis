package com.example.multidoc.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

public class DocumentScopeExtractionTest {

    /**
     * 验证步骤常量存在
     */
    @Test
    void testDocumentScopeExtractionStepExists() throws Exception {
        Field fieldDocScopeStep = null;
        for (Field f : AnalysisService.class.getDeclaredFields()) {
            if (f.getName().equals("STEP_DOCUMENT_SCOPE_EXTRACTION")) {
                fieldDocScopeStep = f;
                f.setAccessible(true);
                break;
            }
        }
        
        assertNotNull(fieldDocScopeStep, "STEP_DOCUMENT_SCOPE_EXTRACTION字段应该存在");
        assertEquals("document_scope_extraction", fieldDocScopeStep.get(null));
    }
    
    /**
     * 验证DocumentScope实体类字段
     */
    @Test
    void testDocumentScopeEntityHasRequiredFields() throws Exception {
        // 验证DocumentScope实体类是否有必要的字段
        Class<?> documentScopeClass = Class.forName("com.example.multidoc.model.DocumentScope");
        
        // 验证字段是否存在
        Field taskField = documentScopeClass.getDeclaredField("task");
        Field filePathField = documentScopeClass.getDeclaredField("filePath");
        Field fileNameField = documentScopeClass.getDeclaredField("fileName");
        Field scopeContentField = documentScopeClass.getDeclaredField("scopeContent");
        
        assertNotNull(taskField, "task字段应该存在");
        assertNotNull(filePathField, "filePath字段应该存在");
        assertNotNull(fileNameField, "fileName字段应该存在");
        assertNotNull(scopeContentField, "scopeContent字段应该存在");
    }
    
    /**
     * 验证AIService中的extractDocumentScope方法
     */
    @Test
    void testExtractDocumentScopeMethodExists() throws Exception {
        // 检查AIService是否有新增的extractDocumentScope方法
        try {
            AIService.class.getDeclaredMethod("extractDocumentScope", String.class, String.class);
            // 如果方法存在，测试通过
            assertTrue(true);
        } catch (NoSuchMethodException e) {
            fail("AIService应该有extractDocumentScope方法，但未找到");
        }
    }
    
    /**
     * 验证DocumentService中的extractDocumentPrefix方法
     */
    @Test
    void testExtractDocumentPrefixMethodExists() throws Exception {
        // 检查DocumentService是否有新增的extractDocumentPrefix方法
        try {
            DocumentService.class.getDeclaredMethod("extractDocumentPrefix", String.class);
            // 如果方法存在，测试通过
            assertTrue(true);
        } catch (NoSuchMethodException e) {
            fail("DocumentService应该有extractDocumentPrefix方法，但未找到");
        }
    }
} 