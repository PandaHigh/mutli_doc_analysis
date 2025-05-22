package com.example.multidoc.service;

import com.example.multidoc.model.*;
import com.example.multidoc.repository.*;
import com.example.multidoc.util.ExcelProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AnalysisServiceResumabilityTest {

    @InjectMocks
    private AnalysisService analysisService;

    @Mock(lenient = true)
    private AnalysisTaskRepository taskRepository;

    @Mock(lenient = true)
    private ExcelFieldRepository fieldRepository;

    @Mock(lenient = true)
    private FieldRuleRepository ruleRepository;

    @Mock(lenient = true)
    private AnalysisResultRepository resultRepository;

    @Mock(lenient = true)
    private DocumentService documentService;

    @Mock(lenient = true)
    private AIService aiService;

    @Mock(lenient = true)
    private FileStorageConfigRepository configRepository;

    @Mock(lenient = true)
    private WordSentenceRepository sentenceRepository;

    @Mock(lenient = true)
    private Executor analysisTaskExecutor;

    @Mock(lenient = true)
    private FieldSentenceRelationRepository relationRepository;

    @Mock(lenient = true)
    private LuceneService luceneService;

    @Mock(lenient = true)
    private TaskService taskService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private AnalysisTask mockTask;
    private final String TEST_TASK_ID = "test-task-id";
    private FileStorageConfig batchSizeConfig;

    @BeforeEach
    public void setup() {
        // 创建测试任务
        mockTask = new AnalysisTask();
        mockTask.setId(TEST_TASK_ID);
        mockTask.setTaskName("Test Task");
        mockTask.setStatus(AnalysisTask.TaskStatus.PENDING);
        mockTask.setCreatedTime(LocalDateTime.now());
        mockTask.setLastCompletedStep("start"); // 初始步骤
        
        List<String> wordFiles = Arrays.asList("test_doc1.docx", "test_doc2.docx");
        List<String> excelFiles = Arrays.asList("test_excel1.xlsx", "test_excel2.xlsx");
        mockTask.setWordFilePaths(wordFiles);
        mockTask.setExcelFilePaths(excelFiles);
        
        // 配置mock行为
        when(taskRepository.findById(TEST_TASK_ID)).thenReturn(Optional.of(mockTask));
        
        // 模拟配置
        batchSizeConfig = new FileStorageConfig();
        batchSizeConfig.setConfigKey("app.field-batch-size");
        batchSizeConfig.setConfigValue("50");
        when(configRepository.findByConfigKey("app.field-batch-size"))
            .thenReturn(Optional.of(batchSizeConfig));
        
        // 模拟AI返回的字段分类结果
        setupMockAIResponses();
    }
    
    private void setupMockAIResponses() {
        // 模拟字段分类结果
        ObjectNode categoriesNode = objectMapper.createObjectNode();
        ArrayNode categoriesArray = categoriesNode.putArray("categories");
        
        ObjectNode category1 = categoriesArray.addObject();
        category1.put("categoryName", "基本信息");
        ArrayNode fields1 = category1.putArray("fields");
        addMockField(fields1, "用户表", "用户ID", "STRING", "用户唯一标识");
        addMockField(fields1, "用户表", "用户名", "STRING", "用户登录名");
        
        ObjectNode category2 = categoriesArray.addObject();
        category2.put("categoryName", "联系方式");
        ArrayNode fields2 = category2.putArray("fields");
        addMockField(fields2, "用户表", "电话", "STRING", "用户联系电话");
        addMockField(fields2, "用户表", "邮箱", "STRING", "用户邮箱地址");
        
        // 使用宽松模式
        when(aiService.categorizeFields(anyList(), any())).thenReturn(categoriesNode);
        
        // 模拟规则提取结果
        ObjectNode rulesNode = objectMapper.createObjectNode();
        ArrayNode rulesArray = rulesNode.putArray("rules");
        
        ObjectNode rule1 = rulesArray.addObject();
        rule1.put("type", "EXPLICIT");
        rule1.put("content", "用户ID必须是唯一的");
        rule1.put("confidence", 0.95);
        ArrayNode fieldsForRule1 = rule1.putArray("fields");
        fieldsForRule1.add("用户ID");
        
        ObjectNode rule2 = rulesArray.addObject();
        rule2.put("type", "IMPLICIT");
        rule2.put("content", "邮箱地址应符合标准邮箱格式");
        rule2.put("confidence", 0.85);
        ArrayNode fieldsForRule2 = rule2.putArray("fields");
        fieldsForRule2.add("邮箱");
        
        // 使用宽松模式
        when(aiService.extractRules(anyString(), anyList())).thenReturn(rulesNode);
    }
    
    private void addMockField(ArrayNode fieldsArray, String tableName, String fieldName, String fieldType, String description) {
        ObjectNode field = fieldsArray.addObject();
        field.put("tableName", tableName);
        field.put("fieldName", fieldName);
        field.put("fieldType", fieldType);
        field.put("description", description);
    }

    @Test
    public void testExcelProcessingStepResumability() throws Exception {
        // 设置任务在开始状态
        mockTask.setLastCompletedStep("start");
        
        // 创建一些模拟ExcelField对象
        List<ExcelField> mockFields = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ExcelField field = new ExcelField();
            field.setId((long)i);
            field.setFieldName("测试字段" + i);
            field.setCategory("测试分类");
            mockFields.add(field);
        }
        
        // Mock DocumentService的processExcelFile方法，让其返回一些元素
        List<ExcelProcessor.ElementInfo> mockElements = new ArrayList<>();
        ExcelProcessor.ElementInfo element = new ExcelProcessor.ElementInfo();
        element.setTableName("测试表");
        element.setValue("测试字段");
        mockElements.add(element);
        when(documentService.processExcelFile(anyString())).thenReturn(mockElements);
        
        // Mock数据清理和保存
        doNothing().when(fieldRepository).deleteByTask(any(AnalysisTask.class));
        when(fieldRepository.save(any(ExcelField.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // 执行恢复处理
        analysisService.processTask(TEST_TASK_ID, true);
        
        // 验证：
        // 1. 由于是恢复模式且步骤未完成，应调用fieldRepository.deleteByTask清理之前的数据
        verify(fieldRepository).deleteByTask(any(AnalysisTask.class));
        // 2. 应该调用documentService.processExcelFile处理Excel文件
        verify(documentService, atLeastOnce()).processExcelFile(anyString());
    }
    
    @Test
    public void testWordProcessingStepResumability() throws IOException {
        // 设置任务已完成Excel处理步骤
        mockTask.setLastCompletedStep("excel_and_field_processing");
        
        // Mock文档处理和数据清理
        doNothing().when(sentenceRepository).deleteByTask(any(AnalysisTask.class));
        doNothing().when(documentService).processWordDocuments(any(AnalysisTask.class));
        
        // 执行恢复处理
        analysisService.processTask(TEST_TASK_ID, true);
        
        // 验证：
        // 1. 应该清理之前的句子数据
        verify(sentenceRepository).deleteByTask(any(AnalysisTask.class));
        // 2. 应该调用文档处理服务
        verify(documentService).processWordDocuments(any(AnalysisTask.class));
    }
    
    @Test
    public void testLuceneAnalysisStepResumability() {
        // 设置任务已完成Word处理步骤
        mockTask.setLastCompletedStep("word_processing");
        
        // Mock数据查询和处理
        when(fieldRepository.findByTask(any(AnalysisTask.class))).thenReturn(new ArrayList<>());
        when(sentenceRepository.findByTask(any(AnalysisTask.class))).thenReturn(new ArrayList<>());
        
        // 执行恢复处理
        analysisService.processTask(TEST_TASK_ID, true);
        
        // 验证：
        // 1. 应该调用Lucene服务计算相关性
        verify(luceneService).calculateRelevance(anyList(), anyList());
    }
    
    @Test
    public void testRuleExtractionStepResumability() throws IOException {
        // 设置任务已完成Lucene分析步骤
        mockTask.setLastCompletedStep("lucene_analysis");
        
        // 准备测试数据
        ExcelField field1 = new ExcelField();
        field1.setId(1L);
        field1.setFieldName("测试字段1");
        field1.setCategory("基本信息");
        
        ExcelField field2 = new ExcelField();
        field2.setId(2L);
        field2.setFieldName("测试字段2");
        field2.setCategory("联系方式");
        
        // Mock数据查询和处理
        when(fieldRepository.findDistinctCategoriesByTask(any(AnalysisTask.class)))
            .thenReturn(Arrays.asList("基本信息", "联系方式"));
        when(fieldRepository.findByTaskAndCategory(any(AnalysisTask.class), eq("基本信息")))
            .thenReturn(Collections.singletonList(field1));
        when(fieldRepository.findByTaskAndCategory(any(AnalysisTask.class), eq("联系方式")))
            .thenReturn(Collections.singletonList(field2));
            
        // 使用anyLong()而不是固定值，解决参数匹配问题
        when(relationRepository.findByFieldIdOrderByRelevanceScoreDesc(anyLong()))
            .thenReturn(new ArrayList<>());
            
        doNothing().when(ruleRepository).deleteByTask(any(AnalysisTask.class));
        when(ruleRepository.save(any(FieldRule.class))).thenReturn(new FieldRule());
        
        // 执行恢复处理
        analysisService.processTask(TEST_TASK_ID, true);
        
        // 验证：
        // 1. 应该清理之前的规则数据
        verify(ruleRepository).deleteByTask(any(AnalysisTask.class));
        // 2. 应该查询不同的分类
        verify(fieldRepository).findDistinctCategoriesByTask(any(AnalysisTask.class));
    }
    
    @Test
    public void testResultGenerationStepResumability() throws IOException {
        // 设置任务已完成规则提取步骤
        mockTask.setLastCompletedStep("rule_extraction");
        
        // Mock数据查询和结果保存
        when(fieldRepository.findByTask(any(AnalysisTask.class))).thenReturn(new ArrayList<>());
        when(ruleRepository.findByTask(any(AnalysisTask.class))).thenReturn(new ArrayList<>());
        when(resultRepository.save(any(AnalysisResult.class))).thenReturn(new AnalysisResult());
        
        // 执行恢复处理
        analysisService.processTask(TEST_TASK_ID, true);
        
        // 验证：
        // 1. 应该保存分析结果
        verify(resultRepository).save(any(AnalysisResult.class));
    }
    
    @Test
    public void testSkipCompletedSteps() throws IOException {
        // 设置任务已完成Excel和Word处理步骤
        mockTask.setLastCompletedStep("word_processing");
        
        // 基本的Mock设置以确保测试能够运行
        when(fieldRepository.findByTask(any(AnalysisTask.class))).thenReturn(new ArrayList<>());
        when(sentenceRepository.findByTask(any(AnalysisTask.class))).thenReturn(new ArrayList<>());
        
        // 执行恢复处理
        analysisService.processTask(TEST_TASK_ID, true);
        
        // 验证：
        // 1. 不应调用Excel处理相关方法
        verify(fieldRepository, never()).deleteByTask(any(AnalysisTask.class));
        
        // 2. 不应调用Word处理相关方法
        verify(documentService, never()).processWordDocuments(any(AnalysisTask.class));
    }
    
    @Test
    public void testResumeTask() {
        // 设置任务为失败状态
        mockTask.setStatus(AnalysisTask.TaskStatus.FAILED);
        
        // 准备一个将会作为mockito spy的AnalysisService
        AnalysisService spyService = spy(analysisService);
        
        // 模拟executeAnalysisTask方法的行为
        CompletableFuture<AnalysisResult> future = CompletableFuture.completedFuture(new AnalysisResult());
        doReturn(future).when(spyService).executeAnalysisTask(anyString());
        
        // 执行恢复
        spyService.resumeTask(TEST_TASK_ID);
        
        // 验证任务状态已更新为PENDING
        verify(taskRepository).save(argThat(task -> task.getStatus() == AnalysisTask.TaskStatus.PENDING));
    }
} 