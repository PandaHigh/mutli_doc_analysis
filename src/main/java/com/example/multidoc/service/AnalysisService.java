package com.example.multidoc.service;

import com.example.multidoc.model.*;
import com.example.multidoc.repository.*;
import com.example.multidoc.util.ExcelProcessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisService.class);
    private static final int TOP_RELATED_FIELDS = 5; // 相关性最高的前N个字段
    
    // 添加进度跟踪相关的常量
    private static final String STEP_EXCEL_PROCESSING = "excel_processing";
    private static final String STEP_FIELD_CLASSIFICATION = "field_classification";
    private static final String STEP_WORD_PROCESSING = "word_processing";
    private static final String STEP_CATEGORY_ANALYSIS = "category_analysis";
    private static final String STEP_RULE_EXTRACTION = "rule_extraction";
    private static final String STEP_RESULT_GENERATION = "result_generation";

    @Autowired
    private AnalysisTaskRepository taskRepository;

    @Autowired
    private ExcelFieldRepository fieldRepository;

    @Autowired
    private FieldRelationRepository relationRepository;

    @Autowired
    private FieldRuleRepository ruleRepository;

    @Autowired
    private WordChunkRepository chunkRepository;

    @Autowired
    private AnalysisResultRepository resultRepository;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private AIService aiService;

    @Autowired
    private FileStorageConfigRepository configRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Map<String, Object>> taskProgressMap = new ConcurrentHashMap<>();

    /**
     * 创建分析任务
     */
    @Transactional
    public AnalysisTask createTask(String taskName, List<String> wordFilePaths, List<String> excelFilePaths) {
        AnalysisTask task = new AnalysisTask();
        task.setTaskName(taskName);
        task.setCreatedTime(LocalDateTime.now());
        task.setStatus(AnalysisTask.TaskStatus.CREATED);
        task.setWordFilePaths(wordFilePaths);
        task.setExcelFilePaths(excelFilePaths);

        return taskRepository.save(task);
    }

    /**
     * 执行分析任务
     */
    @Transactional
    public CompletableFuture<AnalysisResult> executeAnalysisTask(String taskId) {
        AnalysisTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("任务不存在: " + taskId));

        // 初始化进度跟踪
        initializeTaskProgress(taskId);

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 第一步：处理Excel文档并提取字段
                updateTaskStatus(taskId, STEP_EXCEL_PROCESSING, "开始处理Excel文档", 0);
                processExcelDocuments(task);
                updateTaskStatus(taskId, STEP_EXCEL_PROCESSING, "Excel文档处理完成", 100);

                // 第二步：对提取的字段进行分类
                List<ExcelField> fields = fieldRepository.findByTask(task);
                updateTaskStatus(taskId, STEP_FIELD_CLASSIFICATION, "开始字段分类", 0);
                String classificationResult = aiService.classifyExcelFields(fields);
                JsonNode classificationNode = objectMapper.readTree(classificationResult);
                JsonNode categoriesNode = classificationNode.get("categories");
                updateTaskStatus(taskId, STEP_FIELD_CLASSIFICATION, "字段分类完成", 100);

                // 第三步：处理Word文档
                updateTaskStatus(taskId, STEP_WORD_PROCESSING, "开始处理Word文档", 0);
                documentService.processWordDocuments(task);
                updateTaskStatus(taskId, STEP_WORD_PROCESSING, "Word文档处理完成", 100);

                // 第四步：分析所有分类
                updateTaskStatus(taskId, STEP_CATEGORY_ANALYSIS, "开始分析分类", 0);
                List<WordChunk> chunks = chunkRepository.findByTask(task);
                List<FieldRelation> relations = new ArrayList<>();
                List<FieldRule> rules = new ArrayList<>();

                // 分析所有分类
                for (JsonNode categoryNode : categoriesNode) {
                    String category = categoryNode.get("name").asText();
                    List<ExcelField> categoryFields = new ArrayList<>();
                    
                    for (JsonNode fieldNode : categoryNode.get("fields")) {
                        String fieldName = fieldNode.asText();
                        ExcelField field = fields.stream()
                                .filter(f -> f.getFieldName().equals(fieldName))
                                .findFirst()
                                .orElse(null);
                        if (field != null) {
                            categoryFields.add(field);
                        }
                    }

                    // 分析分类相关的内容
                    for (WordChunk chunk : chunks) {
                        String analysisResult = aiService.analyzeChunkContent(chunk.getContent(), categoryFields);
                        JsonNode analysisNode = objectMapper.readTree(analysisResult);
                        
                        // 提取关系
                        JsonNode relationsNode = analysisNode.get("relations");
                        if (relationsNode != null && relationsNode.isArray()) {
                            for (JsonNode relationNode : relationsNode) {
                                FieldRelation relation = new FieldRelation();
                                relation.setTask(task);
                                relation.setSourceField(findFieldByName(fields, relationNode.get("source").asText()));
                                relation.setTargetField(findFieldByName(fields, relationNode.get("target").asText()));
                                relation.setRelationType(relationNode.get("type").asText());
                                relation.setConfidence(relationNode.get("confidence").asDouble());
                                relations.add(relation);
                            }
                        }

                        // 提取规则
                        JsonNode rulesNode = analysisNode.get("rules");
                        if (rulesNode != null && rulesNode.isArray()) {
                            for (JsonNode ruleNode : rulesNode) {
                                FieldRule rule = new FieldRule();
                                rule.setTask(task);
                                rule.setField(findFieldByName(fields, ruleNode.get("field").asText()));
                                rule.setRuleType(FieldRule.RuleType.valueOf(ruleNode.get("type").asText()));
                                rule.setRuleContent(ruleNode.get("content").asText());
                                rule.setConfidence(ruleNode.get("confidence").asDouble());
                                rules.add(rule);
                            }
                        }
                    }
                }

                // 保存关系
                relationRepository.saveAll(relations);
                updateTaskStatus(taskId, STEP_CATEGORY_ANALYSIS, "分类分析完成", 100);

                // 第五步：提取规则
                updateTaskStatus(taskId, STEP_RULE_EXTRACTION, "开始提取规则", 0);
                ruleRepository.saveAll(rules);
                updateTaskStatus(taskId, STEP_RULE_EXTRACTION, "规则提取完成", 100);

                // 第六步：生成结果
                updateTaskStatus(taskId, STEP_RESULT_GENERATION, "生成分析结果", 0);
                AnalysisResult result = generateAnalysisResult(task);
                updateTaskStatus(taskId, STEP_RESULT_GENERATION, "分析完成", 100);

                // 更新任务状态
                task.setStatus(AnalysisTask.TaskStatus.COMPLETED);
                taskRepository.save(task);

                return result;

            } catch (Exception e) {
                logger.error("分析任务执行失败: " + taskId, e);
                task.setStatus(AnalysisTask.TaskStatus.FAILED);
                taskRepository.save(task);

                AnalysisResult errorResult = new AnalysisResult();
                errorResult.setTask(task);
                errorResult.setCompletedTime(LocalDateTime.now());
                errorResult.setErrorMessage(e.getMessage());
                return resultRepository.save(errorResult);
            }
        });
    }

    private ExcelField findFieldByName(List<ExcelField> fields, String fieldName) {
        return fields.stream()
                .filter(f -> f.getFieldName().equals(fieldName))
                .findFirst()
                .orElse(null);
    }

    /**
     * 初始化任务进度
     */
    private void initializeTaskProgress(String taskId) {
        Map<String, Object> progress = new HashMap<>();
        progress.put("currentStep", STEP_EXCEL_PROCESSING);
        progress.put("currentStepProgress", 0);
        progress.put("currentStepMessage", "准备开始");
        progress.put("overallProgress", 0);
        progress.put("detailedLogs", new ArrayList<>());
        taskProgressMap.put(taskId, progress);
    }

    /**
     * 更新任务进度
     */
    private void updateTaskStatus(String taskId, String step, String message, int progress) {
        Map<String, Object> progressInfo = taskProgressMap.get(taskId);
        if (progressInfo != null) {
            progressInfo.put("currentStep", step);
            progressInfo.put("currentStepProgress", progress);
            progressInfo.put("currentStepMessage", message);
            
            // 计算总体进度
            int overallProgress = calculateOverallProgress(step, progress);
            progressInfo.put("overallProgress", overallProgress);
            
            // 记录详细日志
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> logs = (List<Map<String, Object>>) progressInfo.get("detailedLogs");
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("timestamp", LocalDateTime.now());
            logEntry.put("step", step);
            logEntry.put("message", message);
            logEntry.put("progress", progress);
            logs.add(logEntry);
            
            logger.info("任务 {} - {}: {} (进度: {}%)", taskId, step, message, progress);

            // 更新任务状态
            try {
                AnalysisTask task = taskRepository.findById(taskId).orElse(null);
                if (task != null) {
                    // 根据步骤更新任务状态
                    AnalysisTask.TaskStatus newStatus = getStatusFromStep(step);
                    if (newStatus != null && task.getStatus() != newStatus) {
                        task.setStatus(newStatus);
                        taskRepository.save(task);
                    }
                }
            } catch (Exception e) {
                logger.error("更新任务状态失败: " + taskId, e);
            }
        }
    }

    private AnalysisTask.TaskStatus getStatusFromStep(String step) {
        switch (step) {
            case STEP_EXCEL_PROCESSING:
            case STEP_FIELD_CLASSIFICATION:
            case STEP_WORD_PROCESSING:
            case STEP_CATEGORY_ANALYSIS:
            case STEP_RULE_EXTRACTION:
            case STEP_RESULT_GENERATION:
                return AnalysisTask.TaskStatus.PROCESSING;
            default:
                return null;
        }
    }

    /**
     * 计算总体进度
     */
    private int calculateOverallProgress(String currentStep, int stepProgress) {
        int baseProgress = 0;
        switch (currentStep) {
            case STEP_EXCEL_PROCESSING:
                baseProgress = 0;
                break;
            case STEP_FIELD_CLASSIFICATION:
                baseProgress = 20;
                break;
            case STEP_WORD_PROCESSING:
                baseProgress = 40;
                break;
            case STEP_CATEGORY_ANALYSIS:
                baseProgress = 60;
                break;
            case STEP_RULE_EXTRACTION:
                baseProgress = 80;
                break;
            case STEP_RESULT_GENERATION:
                baseProgress = 90;
                break;
        }
        return baseProgress + (stepProgress / 5);
    }

    /**
     * 获取任务进度信息
     */
    public Map<String, Object> getTaskProgress(String taskId) {
        Map<String, Object> progress = taskProgressMap.get(taskId);
        if (progress == null) {
            progress = new HashMap<>();
            progress.put("currentStep", "unknown");
            progress.put("currentStepProgress", 0);
            progress.put("currentStepMessage", "任务未开始");
            progress.put("overallProgress", 0);
            progress.put("detailedLogs", new ArrayList<>());
        }
        return progress;
    }

    /**
     * 处理Excel文档，提取字段结构
     */
    private void processExcelDocuments(AnalysisTask task) {
        for (String excelPath : task.getExcelFilePaths()) {
            try {
                // 提取Excel字段
                List<ExcelProcessor.ElementInfo> fieldInfos = documentService.extractExcelFields(excelPath);

                // 将字段信息保存到数据库
                for (ExcelProcessor.ElementInfo fieldInfo : fieldInfos) {
                    // 检查字段是否已存在
                    Optional<ExcelField> existingField = fieldRepository.findByTaskAndFieldName(task, fieldInfo.getValue());
                    if (existingField.isPresent()) {
                        // 如果字段已存在，更新描述
                        ExcelField field = existingField.get();
                        field.setDescription(fieldInfo.getComment());
                        fieldRepository.save(field);
                    } else {
                        // 如果字段不存在，创建新字段
                        ExcelField field = new ExcelField();
                        field.setTask(task);
                        field.setFieldName(fieldInfo.getValue());
                        field.setDescription(fieldInfo.getComment());
                        fieldRepository.save(field);
                    }
                }

                logger.info("成功处理Excel文档: {}, 提取{}个字段", excelPath, fieldInfos.size());
            } catch (Exception e) {
                logger.error("处理Excel文档失败: " + excelPath, e);
                throw new RuntimeException("处理Excel文档失败: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 生成分析结果
     */
    private AnalysisResult generateAnalysisResult(AnalysisTask task) throws JsonProcessingException {
        List<ExcelField> fields = fieldRepository.findByTask(task);
        List<FieldRelation> relations = relationRepository.findByTask(task);
        List<FieldRule> rules = ruleRepository.findByTask(task);

        // 按字段组织规则，用于编译和汇总
        Map<String, List<FieldRule>> fieldRulesMap = new HashMap<>();
        for (FieldRule rule : rules) {
            String fieldName = rule.getField().getFieldName();
            if (!fieldRulesMap.containsKey(fieldName)) {
                fieldRulesMap.put(fieldName, new ArrayList<>());
            }
            fieldRulesMap.get(fieldName).add(rule);
        }

        // 编译和汇总规则
        String compiledRulesJson = aiService.compileRules(fieldRulesMap);
        JsonNode compiledRules;
        try {
            compiledRules = objectMapper.readTree(compiledRulesJson);
        } catch (Exception e) {
            logger.error("Failed to parse compiled rules JSON: {}", compiledRulesJson);
            throw new RuntimeException("Failed to parse compiled rules: " + e.getMessage());
        }

        // 生成最终分析报告
        String reportJson = aiService.generateAnalysisReport(task.getTaskName(), fields, relations, rules);
        JsonNode report;
        try {
            report = objectMapper.readTree(reportJson);
        } catch (Exception e) {
            logger.error("Failed to parse report JSON: {}", reportJson);
            throw new RuntimeException("Failed to parse analysis report: " + e.getMessage());
        }

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("report", report);
        resultMap.put("compiledRules", compiledRules);
        resultMap.put("fields", fields.size());
        resultMap.put("relations", relations.size());
        resultMap.put("rules", rules.size());

        // 保存结果
        AnalysisResult result = new AnalysisResult();
        result.setTask(task);
        result.setCompletedTime(LocalDateTime.now());
        result.setResultJson(objectMapper.writeValueAsString(resultMap));
        result.setSummary(report.has("summary") ? report.get("summary").asText() : "分析完成");

        return resultRepository.save(result);
    }

    /**
     * 获取任务列表
     */
    public List<AnalysisTask> getAllTasks() {
        return taskRepository.findAllByOrderByCreatedTimeDesc();
    }

    /**
     * 获取任务详情
     */
    public AnalysisTask getTaskById(String taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("任务不存在: " + taskId));
    }

    /**
     * 获取分析结果
     */
    public AnalysisResult getResultByTaskId(String taskId) {
        AnalysisTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("任务不存在: " + taskId));

        return resultRepository.findByTask(task)
                .orElseThrow(() -> new RuntimeException("分析结果不存在"));
    }

    /**
     * 中止任务
     * @param taskId 任务ID
     */
    @Transactional
    public void cancelTask(String taskId) {
        AnalysisTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("任务不存在: " + taskId));
        
        if (task.getStatus() != AnalysisTask.TaskStatus.PROCESSING) {
            throw new RuntimeException("只能中止正在处理中的任务");
        }
        
        // 更新任务状态为失败
        task.setStatus(AnalysisTask.TaskStatus.FAILED);
        taskRepository.save(task);
        
        // 创建失败结果
        AnalysisResult result = new AnalysisResult();
        result.setTask(task);
        result.setErrorMessage("任务被用户中止");
        result.setCompletedTime(LocalDateTime.now());
        resultRepository.save(result);
        
        logger.info("任务 {} 已被用户中止", taskId);
    }

    /**
     * 删除任务及其相关的所有文档内容
     * @param taskId 任务ID
     */
    @Transactional
    public void deleteTask(String taskId) {
        AnalysisTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("任务不存在: " + taskId));
        
        logger.info("开始删除任务 {}: {}", taskId, task.getTaskName());
        
        // 1. 删除任务相关的所有文档块
        List<WordChunk> chunks = chunkRepository.findByTask(task);
        chunkRepository.deleteAll(chunks);
        logger.info("已删除任务 {} 的 {} 个文档块", taskId, chunks.size());
        
        // 2. 删除任务相关的所有字段
        List<ExcelField> fields = fieldRepository.findByTask(task);
        fieldRepository.deleteAll(fields);
        logger.info("已删除任务 {} 的 {} 个字段", taskId, fields.size());
        
        // 3. 删除任务相关的所有字段关系
        List<FieldRelation> relations = relationRepository.findByTask(task);
        relationRepository.deleteAll(relations);
        logger.info("已删除任务 {} 的 {} 个字段关系", taskId, relations.size());
        
        // 4. 删除任务相关的所有字段规则
        List<FieldRule> rules = ruleRepository.findByTask(task);
        ruleRepository.deleteAll(rules);
        logger.info("已删除任务 {} 的 {} 个字段规则", taskId, rules.size());
        
        // 5. 删除任务相关的分析结果
        Optional<AnalysisResult> resultOpt = resultRepository.findByTask(task);
        if (resultOpt.isPresent()) {
            resultRepository.delete(resultOpt.get());
            logger.info("已删除任务 {} 的分析结果", taskId);
        }
        
        // 6. 删除任务本身
        taskRepository.delete(task);
        logger.info("已删除任务 {}", taskId);
        
        // 7. 删除任务相关的文件
        deleteTaskFiles(task);
    }
    
    /**
     * 删除任务相关的文件
     * @param task 任务
     */
    private void deleteTaskFiles(AnalysisTask task) {
        try {
            // 删除Word文档
            for (String filePath : task.getWordFilePaths()) {
                File file = new File(filePath);
                if (file.exists()) {
                    boolean deleted = file.delete();
                    if (deleted) {
                        logger.info("已删除Word文档: {}", filePath);
                    } else {
                        logger.warn("无法删除Word文档: {}", filePath);
                    }
                }
            }
            
            // 删除Excel文档
            for (String filePath : task.getExcelFilePaths()) {
                File file = new File(filePath);
                if (file.exists()) {
                    boolean deleted = file.delete();
                    if (deleted) {
                        logger.info("已删除Excel文档: {}", filePath);
                    } else {
                        logger.warn("无法删除Excel文档: {}", filePath);
                    }
                }
            }
            
            // 删除结果文件
            if (task.getResultFilePath() != null && !task.getResultFilePath().isEmpty()) {
                File resultFile = new File(task.getResultFilePath());
                if (resultFile.exists()) {
                    boolean deleted = resultFile.delete();
                    if (deleted) {
                        logger.info("已删除结果文件: {}", task.getResultFilePath());
                    } else {
                        logger.warn("无法删除结果文件: {}", task.getResultFilePath());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("删除任务文件时出错", e);
        }
    }

    /**
     * 获取任务日志
     */
    public List<Map<String, Object>> getTaskLogs(String taskId) {
        Map<String, Object> progress = taskProgressMap.get(taskId);
        if (progress != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> logs = (List<Map<String, Object>>) progress.get("detailedLogs");
            return logs;
        }
        return new ArrayList<>();
    }
} 