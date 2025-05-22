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
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.example.multidoc.model.WordSentence;
import com.example.multidoc.repository.WordSentenceRepository;
import com.example.multidoc.service.LuceneService;
import com.example.multidoc.service.TaskService;

@Service
public class AnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisService.class);
    private static final int TOP_RELATED_FIELDS = 5; // Still relevant? Keep for now.
    
    // --- New Step Constants for Resumability ---
    private static final String STEP_START = "start";
    private static final String STEP_EXCEL_AND_FIELD_PROCESSING = "excel_and_field_processing";
    private static final String STEP_WORD_PROCESSING = "word_processing";
    private static final String STEP_LUCENE_ANALYSIS = "lucene_analysis";
    private static final String STEP_RULE_EXTRACTION = "rule_extraction";
    private static final String STEP_RESULT_GENERATION = "result_generation";
    private static final String STEP_COMPLETE = "complete";
    // --- End New Step Constants ---

    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long RETRY_DELAY_MS = 10000; // 增加到10秒
    private static final long AI_SERVICE_TIMEOUT_SECONDS = 360; // 增加到360秒
    private static final int NUM_CATEGORIES = 15; // 字段分类数量
    private static final int TOP_K_SENTENCES = 5; // 每个字段关联的句子数量

    @Autowired
    private AnalysisTaskRepository taskRepository;

    @Autowired
    private ExcelFieldRepository fieldRepository;

    @Autowired
    private FieldRuleRepository ruleRepository;

    @Autowired
    private AnalysisResultRepository resultRepository;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private AIService aiService;

    @Autowired
    private FileStorageConfigRepository configRepository; // Keep if needed for config

    @Autowired
    private WordSentenceRepository sentenceRepository;

    @Autowired
    @Qualifier("analysisTaskExecutor")
    private Executor analysisTaskExecutor;

    @Autowired
    private FieldSentenceRelationRepository relationRepository;

    @Autowired
    private LuceneService luceneService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 处理分析任务
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void processTask(String taskId, boolean isResuming) {
        AnalysisTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        try {
            // --- Step 1: Excel and Field Processing ---
            if (!isStepCompleted(task.getLastCompletedStep(), STEP_EXCEL_AND_FIELD_PROCESSING)) {
                logger.info("Task {} - Step: {}", taskId, STEP_EXCEL_AND_FIELD_PROCESSING);
                updateTaskProgress(taskId, STEP_EXCEL_AND_FIELD_PROCESSING, "Starting Excel and field processing", 0);
                taskService.addLog(task, "开始处理Excel文档和字段", "INFO");

                // Only delete and reprocess if the step was not completed
                if (isResuming) {
                    fieldRepository.deleteByTask(task);
                    taskService.addLog(task, "清理之前的字段数据", "INFO");
                }

                // Process all Excel files to get the content for AI analysis
                List<ExcelField> allFields = new ArrayList<>();
                for (String filePath : task.getExcelFilePaths()) {
                    try {
                        List<ExcelProcessor.ElementInfo> elements = documentService.processExcelFile(filePath);
                        taskService.addLog(task, "处理Excel文件: " + filePath, "INFO");
                        for (ExcelProcessor.ElementInfo element : elements) {
                            ExcelField field = new ExcelField();
                            field.setTask(task);
                            field.setTableName(element.getTableName());
                            field.setFieldName(element.getValue());
                            field.setFieldType("STRING");
                            field.setDescription(element.getValue());
                            allFields.add(field);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to process Excel file: " + filePath, e);
                        taskService.addLog(task, "处理Excel文件失败: " + e.getMessage(), "ERROR");
                        throw new RuntimeException("Failed to process Excel file: " + e.getMessage(), e);
                    }
                }

                // Call AI service for field categorization
                if (!allFields.isEmpty()) {
                    taskService.addLog(task, "开始字段分类", "INFO");
                    
                    int totalFields = allFields.size();
                    int batchSize = 100; // 获取fieldBatchSize的值
                    try {
                        batchSize = Integer.parseInt(
                            configRepository.findByConfigKey("app.field-batch-size")
                            .map(FileStorageConfig::getConfigValue)
                            .orElse("100")
                        );
                    } catch (Exception e) {
                        logger.warn("读取字段批次配置失败，使用默认值100", e);
                    }
                    
                    int totalBatches = (int) Math.ceil((double) totalFields / batchSize);
                    taskService.addLog(task, String.format("总共需要处理 %d 个字段，分为 %d 个批次，每批次 %d 个字段", 
                        totalFields, totalBatches, batchSize), "INFO");
                    updateTaskProgress(taskId, STEP_EXCEL_AND_FIELD_PROCESSING, 
                        String.format("开始字段分类 (0/%d 批次)", totalBatches), 20);
                    
                    // 调用AI服务进行字段分类，带进度回调
                    JsonNode categoriesNode = callAIServiceWithRetry("Field Categorization", () ->
                        aiService.categorizeFields(allFields, (currentBatch, totalBatch) -> {
                            String message = String.format("正在字段分类 (%d/%d 批次)", currentBatch, totalBatch);
                            int progress = 20 + (int)(80.0 * currentBatch / totalBatch);
                            updateTaskProgress(taskId, STEP_EXCEL_AND_FIELD_PROCESSING, message, progress);
                            taskService.addLog(task, message, "INFO");
                        })
                    );

                    // Process categorization results and save fields
                    if (categoriesNode.has("categories") && categoriesNode.get("categories").isArray()) {
                        for (JsonNode categoryNode : categoriesNode.get("categories")) {
                            String categoryName = categoryNode.get("categoryName").asText();
                            taskService.addLog(task, "处理分类: " + categoryName, "INFO");

                            if (categoryNode.has("fields") && categoryNode.get("fields").isArray()) {
                                for (JsonNode fieldNode : categoryNode.get("fields")) {
                                    // Create and save field directly from AI response
                                    ExcelField field = new ExcelField();
                                    field.setTask(task);
                                    field.setTableName(fieldNode.has("tableName") ? 
                                        fieldNode.get("tableName").asText() : null);
                                    field.setFieldName(fieldNode.get("fieldName").asText());
                                    field.setFieldType(fieldNode.has("fieldType") ? 
                                        fieldNode.get("fieldType").asText() : "STRING");
                                    field.setDescription(fieldNode.has("description") ? 
                                        fieldNode.get("description").asText() : field.getFieldName());
                                    field.setCategory(categoryName);
                                    try {
                                        fieldRepository.save(field);
                                    } catch (Exception e) {
                                        logger.error(e.getMessage());
                                    }
                                    logger.debug("Task {} - Saved field '{}' with category '{}'", 
                                        task.getId(), field.getFieldName(), categoryName);
                                }
                            }
                        }
                    } else {
                        taskService.addLog(task, "字段分类结果格式错误", "ERROR");
                        throw new RuntimeException("Invalid categorization response format");
                    }
                }

                updateTaskProgress(taskId, STEP_EXCEL_AND_FIELD_PROCESSING, "Excel and field processing complete", 100);
                task.setLastCompletedStep(STEP_EXCEL_AND_FIELD_PROCESSING);
                taskRepository.save(task);
                taskService.addLog(task, "Excel文档和字段处理完成", "INFO");
                logger.info("Task {} - Completed Step: {}", taskId, STEP_EXCEL_AND_FIELD_PROCESSING);
            } else if (isResuming) {
                logger.info("Task {} - Reusing completed Excel and field processing results", taskId);
                taskService.addLog(task, "复用已完成的Excel文档和字段处理结果", "INFO");
            }

            // --- Step 2: Word Processing ---
            if (!isStepCompleted(task.getLastCompletedStep(), STEP_WORD_PROCESSING)) {
                logger.info("Task {} - Step: {}", taskId, STEP_WORD_PROCESSING);
                updateTaskProgress(taskId, STEP_WORD_PROCESSING, "Starting Word processing (sentence-level)", 0);
                taskService.addLog(task, "开始处理Word文档", "INFO");
                
                // Only delete and reprocess if the step was not completed
                if (isResuming) {
                    sentenceRepository.deleteByTask(task);
                    taskService.addLog(task, "清理之前的句子数据", "INFO");
                }
                
                try {
                    documentService.processWordDocuments(task);
                    taskService.addLog(task, "Word文档处理完成", "INFO");
                } catch (IOException e) {
                    logger.error("Task {} - Word processing failed", taskId, e);
                    taskService.addLog(task, "Word文档处理失败: " + e.getMessage(), "ERROR");
                    throw new RuntimeException("Word processing failed: " + e.getMessage(), e);
                }
                
                updateTaskProgress(taskId, STEP_WORD_PROCESSING, "Word processing complete", 100);
                task.setLastCompletedStep(STEP_WORD_PROCESSING);
                taskRepository.save(task);
                logger.info("Task {} - Completed Step: {}", taskId, STEP_WORD_PROCESSING);
            } else if (isResuming) {
                logger.info("Task {} - Reusing completed Word processing results", taskId);
                taskService.addLog(task, "复用已完成的Word文档处理结果", "INFO");
            }

            // --- Step 3: Lucene Relevance Analysis ---
            if (!isStepCompleted(task.getLastCompletedStep(), STEP_LUCENE_ANALYSIS)) {
                logger.info("Task {} - Step: {}", taskId, STEP_LUCENE_ANALYSIS);
                updateTaskProgress(taskId, STEP_LUCENE_ANALYSIS, "Starting Lucene relevance analysis", 0);
                taskService.addLog(task, "开始Lucene相关性分析", "INFO");

                List<ExcelField> allFields = fieldRepository.findByTask(task);
                List<WordSentence> allSentences = sentenceRepository.findByTask(task);
                
                logger.info("Task {} - Analyzing relevance between {} Excel fields and {} Word sentences", 
                           taskId, allFields.size(), allSentences.size());
                taskService.addLog(task, String.format("分析%d个Excel字段和%d个Word句子的相关性", 
                    allFields.size(), allSentences.size()), "INFO");

                // 使用 Lucene 评估相关性
                luceneService.calculateRelevance(allFields, allSentences);
                taskService.addLog(task, "Lucene相关性分析完成", "INFO");

                updateTaskProgress(taskId, STEP_LUCENE_ANALYSIS, "Lucene relevance analysis complete", 100);
                task.setLastCompletedStep(STEP_LUCENE_ANALYSIS);
                taskRepository.save(task);
                logger.info("Task {} - Completed Step: {}", taskId, STEP_LUCENE_ANALYSIS);
            } else if (isResuming) {
                logger.info("Task {} - Reusing completed Lucene analysis results", taskId);
                taskService.addLog(task, "复用已完成的Lucene分析结果", "INFO");
            }
            
            // --- Step 4: Rule Extraction ---
            if (!isStepCompleted(task.getLastCompletedStep(), STEP_RULE_EXTRACTION)) {
                logger.info("Task {} - Step: {}", taskId, STEP_RULE_EXTRACTION);
                updateTaskProgress(taskId, STEP_RULE_EXTRACTION, "Starting rule extraction", 0);
                taskService.addLog(task, "开始规则提取", "INFO");

                // Only delete and reprocess if the step was not completed
                if (isResuming) {
                    ruleRepository.deleteByTask(task);
                    taskService.addLog(task, "清理之前的规则数据", "INFO");
                }

                List<String> categories = fieldRepository.findDistinctCategoriesByTask(task);
                int totalCategories = categories.size();
                AtomicInteger processedCategories = new AtomicInteger(0);

                for (String category : categories) {
                    List<ExcelField> fieldsInCategory = fieldRepository.findByTaskAndCategory(task, category);
                    if (!fieldsInCategory.isEmpty()) {
                        taskService.addLog(task, String.format("处理分类'%s'的规则提取", category), "INFO");
                        extractRulesForCategory(task, category, fieldsInCategory);
                    }
                    
                    // Update progress
                    int progress = (int) ((processedCategories.incrementAndGet() * 100.0) / totalCategories);
                    String message = String.format("Processed %d/%d categories", processedCategories.get(), totalCategories);
                    updateTaskProgress(taskId, STEP_RULE_EXTRACTION, message, progress);
                }

                updateTaskProgress(taskId, STEP_RULE_EXTRACTION, "Rule extraction complete", 100);
                task.setLastCompletedStep(STEP_RULE_EXTRACTION);
                taskRepository.save(task);
                taskService.addLog(task, "规则提取完成", "INFO");
                logger.info("Task {} - Completed Step: {}", taskId, STEP_RULE_EXTRACTION);
            } else if (isResuming) {
                logger.info("Task {} - Reusing completed rule extraction results", taskId);
                taskService.addLog(task, "复用已完成的规则提取结果", "INFO");
            }

            // --- Step 5: Result Generation ---
            if (!isStepCompleted(task.getLastCompletedStep(), STEP_RESULT_GENERATION)) {
                logger.info("Task {} - Step: {}", taskId, STEP_RESULT_GENERATION);
                updateTaskProgress(taskId, STEP_RESULT_GENERATION, "Generating final results", 0);
                taskService.addLog(task, "开始生成最终结果", "INFO");

                generateResults(task);
                taskService.addLog(task, "结果生成完成", "INFO");

                updateTaskProgress(taskId, STEP_RESULT_GENERATION, "Result generation complete", 100);
                task.setLastCompletedStep(STEP_RESULT_GENERATION);
                taskRepository.save(task);
                logger.info("Task {} - Completed Step: {}", taskId, STEP_RESULT_GENERATION);
            }

            // Mark task as complete
            task.setStatus(AnalysisTask.TaskStatus.COMPLETED);
            task.setCompletedTime(LocalDateTime.now());
            task.setLastCompletedStep(STEP_COMPLETE);
            taskRepository.save(task);
            taskService.addLog(task, "任务完成", "INFO");

            logger.info("Task {} - Analysis completed successfully", taskId);

        } catch (Exception e) {
            logger.error("Task {} - Error during analysis: {}", taskId, e.getMessage(), e);
            taskService.addLog(task, "任务执行出错: " + e.getMessage(), "ERROR");
            task.setStatus(AnalysisTask.TaskStatus.FAILED);
            taskRepository.save(task);
            throw new RuntimeException("Analysis failed: " + e.getMessage(), e);
        }
    }

    private boolean isStepCompleted(String lastCompletedStep, String targetStep) {
        List<String> stepsOrder = Arrays.asList(
            STEP_START,
            STEP_EXCEL_AND_FIELD_PROCESSING,
            STEP_WORD_PROCESSING,
            STEP_LUCENE_ANALYSIS,
            STEP_RULE_EXTRACTION,
            STEP_RESULT_GENERATION,
            STEP_COMPLETE // End marker
        );
        int lastCompletedIndex = stepsOrder.indexOf(lastCompletedStep);
        int targetIndex = stepsOrder.indexOf(targetStep);

        if (lastCompletedIndex == -1 || targetIndex == -1) {
             logger.warn("Invalid step encountered: lastCompleted={}, target={}", lastCompletedStep, targetStep);
             return false; // Or throw an error?
        }
        // The target step is completed if the last completed step is at or after it in the sequence.
        return lastCompletedIndex >= targetIndex;
    }

    private void updateTaskProgress(String taskId, String step, String message, int progress) {
        try {
            AnalysisTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));
            task.setProgress(progress);
            taskRepository.save(task);
            logger.debug("Task {} - {} - Progress: {}% - {}", taskId, step, progress, message);
                } catch (Exception e) {
            logger.error("Failed to update task progress: {}", e.getMessage());
        }
    }

    private void extractRulesForCategory(AnalysisTask task, String category, List<ExcelField> fieldsInCategory) {
        try {
            // 记录分类中的具体字段信息
            StringBuilder fieldDetails = new StringBuilder();
            fieldDetails.append("分类 '").append(category).append("' 包含以下字段：\n");
            for (ExcelField field : fieldsInCategory) {
                fieldDetails.append(String.format("- 字段名: %s, 描述: %s\n", 
                    field.getFieldName(), 
                    field.getDescription() != null ? field.getDescription() : "无描述"));
            }
            taskService.addLog(task, fieldDetails.toString(), "INFO");
            
            logger.info("Task {} - 开始处理分类 '{}' 中的 {} 个字段", task.getId(), category, fieldsInCategory.size());
            
            // 收集该分类所有字段关联的句子
            Set<WordSentence> allRelevantSentences = new HashSet<>();
            for (ExcelField field : fieldsInCategory) {
                // 使用新的关系表获取相关句子
                List<FieldSentenceRelation> relations = relationRepository.findByFieldIdOrderByRelevanceScoreDesc(field.getId());
                
                // 记录字段的相关句子详情
                StringBuilder sentenceDetails = new StringBuilder();
                sentenceDetails.append(String.format("\n字段 '%s' 的相关句子（共 %d 个）：\n", 
                    field.getFieldName(), relations.size()));
                
                for (FieldSentenceRelation relation : relations) {
                    sentenceDetails.append(String.format("- 相关性得分: %.2f\n", relation.getRelevanceScore()));
                    sentenceDetails.append(String.format("  来源文件: %s\n", relation.getSourceFile()));
                    sentenceDetails.append(String.format("  句子内容: %s\n", relation.getSentenceContent()));
                    sentenceDetails.append("  ---\n");
                    
                    WordSentence sentence = new WordSentence();
                    sentence.setId(relation.getSentenceId());
                    sentence.setContent(relation.getSentenceContent());
                    sentence.setSourceFile(relation.getSourceFile());
                    allRelevantSentences.add(sentence);
                }
                
                taskService.addLog(task, sentenceDetails.toString(), "INFO");
            }
            
            // 从集合转为列表并去重
            List<WordSentence> dedupedSentences = new ArrayList<>(allRelevantSentences);
            
            // 按源文件和索引排序，保持上下文结构
            dedupedSentences.sort(Comparator.comparing(WordSentence::getSourceFile).thenComparingInt(WordSentence::getSentenceIndex));
            
            logger.info("Task {} - 分类 '{}' 关联的去重句子数量: {}", task.getId(), category, dedupedSentences.size());
            
            // 构建句子文本
            StringBuilder sentencesText = new StringBuilder();
            for (WordSentence sentence : dedupedSentences) {
                sentencesText.append(sentence.getContent()).append("\n");
            }
            
            // 构建规则提取提示
            String prompt = String.format(
                "分类: %s\n\n字段列表:\n%s\n相关文本:\n%s",
                category,
                fieldDetails.toString(),
                sentencesText.toString()
            );
            
            logger.info("Task {} - 发送给AI的规则提取提示内容：\n{}", task.getId(), prompt);
            
            // 调用AI服务提取规则
            JsonNode rulesNode = callAIServiceWithRetry("Rule Extraction", () ->
                aiService.extractRules(prompt, fieldsInCategory)
            );

            // 保存规则
            processAndSaveRules(task, rulesNode);
            
        } catch (Exception e) {
            logger.error("Task {} - 处理分类 '{}' 时出错", task.getId(), category, e);
            throw new RuntimeException("规则提取失败: " + e.getMessage(), e);
        }
    }

    private void processAndSaveRules(AnalysisTask task, JsonNode rulesNode) {
        try {
            if (rulesNode.has("rules") && rulesNode.get("rules").isArray()) {
                for (JsonNode ruleNode : rulesNode.get("rules")) {
                    FieldRule rule = new FieldRule();
                    rule.setTask(task);
                    rule.setRuleType(FieldRule.RuleType.valueOf(ruleNode.get("type").asText().toUpperCase()));
                    rule.setRuleContent(ruleNode.get("content").asText());
                    rule.setConfidence(ruleNode.has("confidence") ? 
                        (float) ruleNode.get("confidence").asDouble() : 1.0f);
                    
                    // 处理相关字段
                    List<String> fieldNames = new ArrayList<>();
                    if (ruleNode.has("fields") && ruleNode.get("fields").isArray()) {
                        for (JsonNode fieldNode : ruleNode.get("fields")) {
                            fieldNames.add(fieldNode.asText());
                        }
                    }
                    rule.setFieldNames(objectMapper.writeValueAsString(fieldNames));
                    
                    ruleRepository.save(rule);
                }
            }
        } catch (JsonProcessingException e) {
            logger.error("Failed to process rules JSON", e);
            throw new RuntimeException("Failed to process rules: " + e.getMessage(), e);
        }
    }

    private void generateResults(AnalysisTask task) {
        try {
            // 获取所有分析结果
            List<ExcelField> fields = fieldRepository.findByTask(task);
            List<FieldRule> rules = ruleRepository.findByTask(task);
            
            // 构建结果文本
            StringBuilder resultText = new StringBuilder();
            resultText.append("任务ID: ").append(task.getId()).append("\n");
            resultText.append("任务名称: ").append(task.getTaskName()).append("\n");
            resultText.append("完成时间: ").append(LocalDateTime.now()).append("\n\n");
            
            // 添加字段信息
            resultText.append("字段信息:\n");
            for (ExcelField field : fields) {
                resultText.append("- 字段名: ").append(field.getFieldName()).append("\n");
                resultText.append("  表名: ").append(field.getTableName()).append("\n");
                resultText.append("  描述: ").append(field.getDescription()).append("\n");
                resultText.append("  分类: ").append(field.getCategory()).append("\n\n");
            }
            
            // 添加规则信息
            resultText.append("规则信息:\n");
            for (FieldRule rule : rules) {
                resultText.append("- 规则类型: ").append(rule.getRuleType()).append("\n");
                resultText.append("  规则内容: ").append(rule.getRuleContent()).append("\n");
                resultText.append("  置信度: ").append(rule.getConfidence()).append("\n");
                resultText.append("  相关字段: ").append(rule.getFieldNames()).append("\n\n");
            }
            
            // 确保结果目录存在
            Path resultDir = Paths.get("uploads/results");
            Files.createDirectories(resultDir);
            
            // 写入结果文件
            Path resultPath = resultDir.resolve(task.getId() + "_result.txt");
            try (FileWriter writer = new FileWriter(resultPath.toFile())) {
                writer.write(resultText.toString());
            }
            
            // 保存到数据库
            AnalysisResult analysisResult = new AnalysisResult();
            analysisResult.setTask(task);
            analysisResult.setCompletedTime(LocalDateTime.now());
            analysisResult.setResultText(resultText.toString());
            analysisResult.setFieldCount(fields.size());
            analysisResult.setSummaryText(""); // 不再生成分析报告
            resultRepository.save(analysisResult);
            
        } catch (Exception e) {
            logger.error("Failed to generate results", e);
            throw new RuntimeException("Failed to generate results: " + e.getMessage(), e);
        }
    }

    /**
     * 获取所有任务
     */
    public List<AnalysisTask> getAllTasks() {
        return taskRepository.findAll();
    }

    /**
     * 根据ID获取任务
     */
    public AnalysisTask getTaskById(String taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));
    }

    /**
     * 获取任务进度
     */
    public Map<String, Object> getTaskProgress(String taskId) {
        AnalysisTask task = getTaskById(taskId);
        Map<String, Object> progress = new HashMap<>();
        progress.put("status", task.getStatus());
        progress.put("progress", task.getProgress());
        progress.put("lastCompletedStep", task.getLastCompletedStep());
        return progress;
    }

    /**
     * 获取任务结果
     */
    public AnalysisResult getResultByTaskId(String taskId) {
        return resultRepository.findByTaskId(taskId)
                .orElseThrow(() -> new RuntimeException("Result not found for task: " + taskId));
    }

    /**
     * 创建新任务
     */
    public AnalysisTask createTask(String taskName, List<String> wordFilePaths, List<String> excelFilePaths) {
        AnalysisTask task = new AnalysisTask();
        task.setId(UUID.randomUUID().toString());
        task.setTaskName(taskName);
        task.setWordFilePaths(wordFilePaths);
        task.setExcelFilePaths(excelFilePaths);
        task.setStatus(AnalysisTask.TaskStatus.PENDING);
        task.setCreatedTime(LocalDateTime.now());
        return taskRepository.save(task);
    }

    /**
     * 执行分析任务
     */
    public CompletableFuture<AnalysisResult> executeAnalysisTask(String taskId) {
        AnalysisTask task = getTaskById(taskId);
        if (task.getStatus() != AnalysisTask.TaskStatus.PENDING) {
            throw new RuntimeException("Task is not in PENDING state");
        }
        task.setStatus(AnalysisTask.TaskStatus.RUNNING);
        taskRepository.save(task);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                processTask(taskId, false);
                return getResultByTaskId(taskId);
            } catch (Exception e) {
                logger.error("Task {} - Error during execution", taskId, e);
                task.setStatus(AnalysisTask.TaskStatus.FAILED);
                taskRepository.save(task);
                throw new RuntimeException("Task execution failed: " + e.getMessage(), e);
            }
        }, analysisTaskExecutor);
    }

    /**
     * 删除任务
     */
    public void deleteTask(String taskId) {
        AnalysisTask task = getTaskById(taskId);
        taskRepository.delete(task);
    }

    /**
     * 取消任务
     */
    public void cancelTask(String taskId) {
        AnalysisTask task = getTaskById(taskId);
        if (task.getStatus() == AnalysisTask.TaskStatus.RUNNING) {
            task.setStatus(AnalysisTask.TaskStatus.CANCELLED);
            taskRepository.save(task);
        }
    }

    /**
     * 恢复任务
     */
    public void resumeTask(String taskId) {
        AnalysisTask task = getTaskById(taskId);
        if (task.getStatus() == AnalysisTask.TaskStatus.FAILED) {
            task.setStatus(AnalysisTask.TaskStatus.PENDING);
            taskRepository.save(task);
            executeAnalysisTask(taskId);
        }
    }

    /**
     * 获取字段规则
     */
    public List<FieldRule> getFieldRules(AnalysisTask task) {
        return ruleRepository.findByTask(task);
    }

    /**
     * 导出结果
     */
    public String exportResults(AnalysisTask task) {
        AnalysisResult result = getResultByTaskId(task.getId());
        try {
            String exportPath = configRepository.findByConfigKey("result_export_path")
                    .orElseThrow(() -> new RuntimeException("Export path not configured"))
                    .getConfigValue();
            
            Path exportDir = Paths.get(exportPath).toAbsolutePath();
            Files.createDirectories(exportDir);
            
            String fileName = task.getId() + "_" + task.getTaskName().replaceAll("[^a-zA-Z0-9\\-_\\.]", "_") + "_result.txt";
            Path filePath = exportDir.resolve(fileName);
            
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(result.getResultText());
            }
            
            logger.info("Exported result to: {}", filePath);
            return exportPath + "/" + fileName;
        } catch (IOException e) {
            logger.error("Failed to export results", e);
            throw new RuntimeException("Failed to export results: " + e.getMessage(), e);
        }
    }

    private <T> T callAIServiceWithRetry(String operationName, Supplier<T> operation) {
        int attempts = 0;
        long currentDelay = RETRY_DELAY_MS;
        Exception lastException = null;
        
        while (attempts < MAX_RETRY_ATTEMPTS) {
            try {
                logger.info("执行{}操作 (尝试 {}/{})", operationName, attempts + 1, MAX_RETRY_ATTEMPTS);
                return operation.get();
            } catch (Exception e) {
                attempts++;
                lastException = e;
                
                // 分析异常类型
                String errorMessage = e.getMessage();
                boolean isJsonParseError = 
                    (e instanceof com.fasterxml.jackson.core.JsonParseException) || 
                    (e.getCause() instanceof com.fasterxml.jackson.core.JsonParseException) ||
                    (errorMessage != null && errorMessage.contains("JsonParseException"));
                
                boolean isHTMLorJSONError = errorMessage != null && 
                    (errorMessage.contains("text/html") || isJsonParseError);
                
                // 根据错误类型调整重试延迟
                long retryDelay = currentDelay;
                if (isJsonParseError) {
                    // 特别处理JSON解析错误，这通常是由AI返回的格式问题引起的
                    // 可能需要快速重试
                    retryDelay = Math.min(currentDelay, 5000); // 使用较短的延迟，因为这很可能是内容问题而不是服务问题
                    logger.warn("{} - 检测到JSON解析错误，可能是AI返回的格式问题 (尝试 {}/{})", 
                        operationName, attempts, MAX_RETRY_ATTEMPTS);
                } else if (isHTMLorJSONError) {
                    // HTML响应或JSON解析错误可能是临时服务问题，使用更长延迟
                    retryDelay = Math.min(currentDelay * 2, 120000);
                    logger.warn("{} - 检测到HTML响应或JSON解析错误 (尝试 {}/{})", 
                        operationName, attempts, MAX_RETRY_ATTEMPTS);
                } else if (errorMessage != null && 
                          (errorMessage.contains("429") || errorMessage.contains("too many requests"))) {
                    // 限流错误，使用更长延迟
                    retryDelay = Math.min(currentDelay * 3, 180000);
                    logger.warn("{} - 检测到请求频率限制 (尝试 {}/{})", 
                        operationName, attempts, MAX_RETRY_ATTEMPTS);
                }
                
                if (attempts >= MAX_RETRY_ATTEMPTS) {
                    logger.error("{} - 已达到最大重试次数 ({}次)", operationName, MAX_RETRY_ATTEMPTS, e);
                    throw new RuntimeException(operationName + " 失败，已达到最大重试次数(" + 
                        MAX_RETRY_ATTEMPTS + "): " + errorMessage, e);
                }
                
                logger.warn("{} - 尝试 {} 失败: {}, 将在 {} 毫秒后重试...", 
                    operationName, attempts, errorMessage, retryDelay);
                
                try {
                    Thread.sleep(retryDelay);
                    currentDelay = retryDelay; // 更新当前延迟时间
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(operationName + " 中断", ie);
                }
            }
        }
        
        // 不应该到达这里，但为了编译通过
        throw new RuntimeException(operationName + " 失败，已达到最大重试次数");
    }
} 