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

@Service
public class AnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisService.class);
    private static final int TOP_RELATED_FIELDS = 5; // Still relevant? Keep for now.
    
    // --- New Step Constants for Resumability ---
    private static final String STEP_START = "start";
    private static final String STEP_EXCEL_PROCESSING = "excel_processing";
    private static final String STEP_WORD_PROCESSING = "word_processing";
    private static final String STEP_CHUNK_ANALYSIS = "chunk_analysis"; // Replaces category analysis
    private static final String STEP_RESULT_GENERATION = "result_generation";
    private static final String STEP_COMPLETE = "complete";
    // --- End New Step Constants ---

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 5000; // 增加到5秒
    private static final long AI_SERVICE_TIMEOUT_SECONDS = 240; // 增加到240秒

    @Autowired
    private AnalysisTaskRepository taskRepository;

    @Autowired
    private ExcelFieldRepository fieldRepository;

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
    private FileStorageConfigRepository configRepository; // Keep if needed for config

    // No longer need separate maps for relations/rules per task, will save directly
    // private final Map<String, List<FieldRelation>> taskRelationsMap = new ConcurrentHashMap<>();
    // private final Map<String, List<FieldRule>> taskRulesMap = new ConcurrentHashMap<>();
    // Keep progress map for live updates
    private final Map<String, Map<String, Object>> taskProgressMap = new ConcurrentHashMap<>();


    @Autowired
    @Qualifier("analysisTaskExecutor")
    private Executor analysisTaskExecutor;

    private final ObjectMapper objectMapper = new ObjectMapper();


    /**
     * Creates an analysis task.
     */
    @Transactional
    public AnalysisTask createTask(String taskName, List<String> wordFilePaths, List<String> excelFilePaths) {
        AnalysisTask task = new AnalysisTask();
        task.setId(UUID.randomUUID().toString()); // 确保ID是唯一的
        task.setTaskName(taskName);
        task.setCreatedTime(LocalDateTime.now());
        task.setStatus(AnalysisTask.TaskStatus.PENDING);
        task.setLastCompletedStep(STEP_START); // 初始步骤
        task.setProgress(0);
        task.setWordFilePaths(wordFilePaths);
        task.setExcelFilePaths(excelFilePaths);
        
        // 保存任务并返回保存后的实体
        task = taskRepository.save(task);
        
        // 验证任务是否成功保存
        if (task.getId() == null) {
            throw new RuntimeException("Failed to create task: ID is null after save");
        }
        
        logger.info("Created new task: {} with ID: {}", taskName, task.getId());
        return task;
    }

    /**
     * Executes or resumes an analysis task based on its last completed step.
     */
    @Transactional
    public CompletableFuture<AnalysisResult> executeAnalysisTask(String taskId) {
        // Fetch task fresh inside the CompletableFuture to ensure latest state
        return CompletableFuture.supplyAsync(() -> {
            AnalysisTask task = taskRepository.findById(taskId)
                    .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

            logger.info("Starting/Resuming analysis task: {} from step: {}", taskId, task.getLastCompletedStep());
            initializeTaskProgress(taskId); // Initialize or re-initialize progress tracking

            try {
                String currentStep = task.getLastCompletedStep();
                boolean isResuming = task.getStatus() == AnalysisTask.TaskStatus.FAILED;

                // --- Step 1: Process Excel Documents ---
                if (!isStepCompleted(currentStep, STEP_EXCEL_PROCESSING)) {
                    logger.info("Task {} - Step: {}", taskId, STEP_EXCEL_PROCESSING);
                    updateTaskProgress(taskId, STEP_EXCEL_PROCESSING, "Starting Excel processing", 0);
                    processExcelDocuments(task);
                    updateTaskProgress(taskId, STEP_EXCEL_PROCESSING, "Excel processing complete", 100);
                    task.setLastCompletedStep(STEP_EXCEL_PROCESSING);
                    taskRepository.save(task); // Save progress
                    logger.info("Task {} - Completed Step: {}", taskId, STEP_EXCEL_PROCESSING);
                } else if (isResuming) {
                    logger.info("Task {} - Reusing completed Excel processing results", taskId);
                }

                // --- Step 2: Process Word Documents (Chunking) ---
                if (!isStepCompleted(task.getLastCompletedStep(), STEP_WORD_PROCESSING)) {
                    logger.info("Task {} - Step: {}", taskId, STEP_WORD_PROCESSING);
                    updateTaskProgress(taskId, STEP_WORD_PROCESSING, "Starting Word processing (chunking)", 0);
                    // Only delete and reprocess if the step was not completed
                    if (isResuming) {
                        chunkRepository.deleteByTask(task);
                    }
                    documentService.processWordDocuments(task); // Default chunk size from DocumentService
                    updateTaskProgress(taskId, STEP_WORD_PROCESSING, "Word processing complete", 100);
                    task.setLastCompletedStep(STEP_WORD_PROCESSING);
                    taskRepository.save(task); // Save progress
                    logger.info("Task {} - Completed Step: {}", taskId, STEP_WORD_PROCESSING);
                } else if (isResuming) {
                    logger.info("Task {} - Reusing completed Word processing results", taskId);
                }

                // --- Step 3: Analyze Word Chunks (Replaces Classification and Category Analysis) ---
                if (!isStepCompleted(task.getLastCompletedStep(), STEP_CHUNK_ANALYSIS)) {
                    logger.info("Task {} - Step: {}", taskId, STEP_CHUNK_ANALYSIS);
                    updateTaskProgress(taskId, STEP_CHUNK_ANALYSIS, "Starting Word chunk analysis", 0);

                    List<ExcelField> allFields = fieldRepository.findByTask(task);
                    // Fetch only chunks that need processing (PENDING or FAILED)
                    List<WordChunk> chunksToProcess = chunkRepository.findByTaskAndStatusIn(task,
                            Arrays.asList(WordChunk.ChunkStatus.PENDING, WordChunk.ChunkStatus.FAILED));
                    
                    logger.info("Task {} - Analyzing {} Word chunks with {} fields.", taskId, chunksToProcess.size(), allFields.size());

                    if (!chunksToProcess.isEmpty()) {
                        analyzeWordChunks(task, allFields, chunksToProcess);
                    } else {
                        logger.info("Task {} - No pending or failed chunks to analyze.", taskId);
                    }

                    updateTaskProgress(taskId, STEP_CHUNK_ANALYSIS, "Word chunk analysis complete", 100);
                    task.setLastCompletedStep(STEP_CHUNK_ANALYSIS);
                    taskRepository.save(task); // Save progress
                    logger.info("Task {} - Completed Step: {}", taskId, STEP_CHUNK_ANALYSIS);
                } else if (isResuming) {
                    logger.info("Task {} - Reusing completed Word chunk analysis results", taskId);
                }

                // --- Step 4: Generate Final Result ---
                if (!isStepCompleted(task.getLastCompletedStep(), STEP_RESULT_GENERATION)) {
                    logger.info("Task {} - Step: {}", taskId, STEP_RESULT_GENERATION);
                    updateTaskProgress(taskId, STEP_RESULT_GENERATION, "Generating final analysis result", 0);
                    AnalysisResult result = generateAnalysisResult(task); // Reuse existing method for now
                    updateTaskProgress(taskId, STEP_RESULT_GENERATION, "Result generation complete", 100);
                    task.setLastCompletedStep(STEP_RESULT_GENERATION);
                    task.setStatus(AnalysisTask.TaskStatus.COMPLETED); // Mark task as fully completed
                    task.setProgress(100);
                    taskRepository.save(task);
                    logger.info("Task {} - Completed Step: {}", taskId, STEP_RESULT_GENERATION);
                    logger.info("Task {} - Analysis task fully completed.", taskId);
                    return result; // Return the final result
                } else if (isResuming) {
                    logger.info("Task {} - Reusing completed result generation", taskId);
                    return resultRepository.findByTask(task)
                            .orElseThrow(() -> new RuntimeException("Completed task has no result: " + taskId));
                }

                // If all steps were already complete, fetch the existing result
                logger.info("Task {} - All steps previously completed. Fetching existing result.", taskId);
                return resultRepository.findByTask(task)
                         .orElseThrow(() -> new RuntimeException("Completed task has no result: " + taskId));

            } catch (Exception e) {
                logger.error("Task {} - Analysis task execution failed", taskId, e);
                // Reload task to set status on the latest version
                AnalysisTask failedTask = taskRepository.findById(taskId)
                    .orElseThrow(() -> new RuntimeException("Task disappeared during failure handling: " + taskId));
                failedTask.setStatus(AnalysisTask.TaskStatus.FAILED);
                // Don't change lastCompletedStep on failure, so it can be resumed
                taskRepository.save(failedTask);

                // Ensure an error result exists or is created
                return resultRepository.findByTask(failedTask).orElseGet(() -> {
                     AnalysisResult errorResult = new AnalysisResult();
                     errorResult.setTask(failedTask);
                     errorResult.setCompletedTime(LocalDateTime.now());
                     errorResult.setErrorMessage("Task failed: " + e.getMessage());
                     return resultRepository.save(errorResult);
                 });
            }
        }, analysisTaskExecutor); // Still run async
    }

    /**
     * Checks if a target step is considered completed based on the last successful step.
     */
     private boolean isStepCompleted(String lastCompletedStep, String targetStep) {
        List<String> stepsOrder = Arrays.asList(
            STEP_START,
            STEP_EXCEL_PROCESSING,
            STEP_WORD_PROCESSING,
            STEP_CHUNK_ANALYSIS,
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


    /**
     * Analyzes a list of Word chunks against all Excel fields using the AI service.
     * Handles chunk status updates and saving results (relations/rules).
     */
     private void analyzeWordChunks(AnalysisTask task, List<ExcelField> allFields, List<WordChunk> chunksToProcess) {
        String taskId = task.getId();
        AtomicInteger processedChunksCounter = new AtomicInteger(0);
        int totalChunksToProcess = chunksToProcess.size();

        logger.info("Task {} - Starting concurrent analysis of {} chunks", taskId, totalChunksToProcess);

        // 确保任务存在且已保存
        final AnalysisTask finalTask = taskRepository.findById(taskId)
            .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        List<CompletableFuture<Void>> chunkFutures = new ArrayList<>();

        // 批量更新chunk状态为PROCESSING
        chunksToProcess.forEach(chunk -> {
            chunk.setStatus(WordChunk.ChunkStatus.PROCESSING);
        });
        chunkRepository.saveAll(chunksToProcess);

        for (WordChunk chunk : chunksToProcess) {
            CompletableFuture<Void> chunkFuture = CompletableFuture.runAsync(() -> {
                try {
                    logger.debug("Task {} - Analyzing chunk {}/{} (ID: {}) in thread {}", taskId,
                            processedChunksCounter.get() + 1, totalChunksToProcess, chunk.getId(), 
                            Thread.currentThread().getName());

                    // AI Call to analyze chunk content
                    JsonNode analysisNode = callAIServiceWithRetry("Chunk Analysis", () ->
                        aiService.analyzeChunkContent(chunk.getContent(), allFields)
                    );

                    // Process and save rules - ensure task is passed
                    processAndSaveAnalysisResults(finalTask, allFields, analysisNode);

                    // Mark chunk as ANALYZED on success
                    chunk.setStatus(WordChunk.ChunkStatus.ANALYZED);
                    chunkRepository.save(chunk);
                    logger.debug("Task {} - Successfully analyzed chunk {} in thread {}", taskId, chunk.getId(), 
                            Thread.currentThread().getName());

                } catch (Exception e) {
                    logger.error("Task {} - Failed to analyze chunk {} in thread {}: {}", taskId, chunk.getId(), 
                            Thread.currentThread().getName(), e.getMessage(), e);
                    // Mark chunk as FAILED
                    chunk.setStatus(WordChunk.ChunkStatus.FAILED);
                    chunkRepository.save(chunk);
                } finally {
                    // Update overall progress for this step
                    int processedCount = processedChunksCounter.incrementAndGet();
                    int progress = (int) ((processedCount * 100.0) / totalChunksToProcess);
                    String message = String.format("Analyzing chunk %d/%d", processedCount, totalChunksToProcess);
                    updateTaskProgress(taskId, STEP_CHUNK_ANALYSIS, message, progress);
                }
            }, analysisTaskExecutor);
            chunkFutures.add(chunkFuture);
        }

        // Wait for all chunk analysis futures to complete
        try {
            logger.info("Task {} - Waiting for all {} chunks to complete analysis", taskId, totalChunksToProcess);
            CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture[0])).join();
            logger.info("Task {} - Finished processing all submitted chunks.", taskId);
        } catch (Exception e) {
            logger.error("Task {} - Error waiting for chunk analysis completion: {}", taskId, e.getMessage(), e);
        }

        // Final check for any chunks that might have failed
        long failedCount = chunkRepository.countByTaskAndStatus(finalTask, WordChunk.ChunkStatus.FAILED);
        if (failedCount > 0) {
            logger.warn("Task {} - {} chunks failed analysis during the process.", taskId, failedCount);
        } else {
            logger.info("Task {} - All chunks analyzed successfully or were already processed.", taskId);
        }
     }

     /**
      * Processes the AI analysis results (relations, rules) from a chunk and saves them directly.
      */
     @Transactional(isolation = Isolation.READ_COMMITTED)
     protected void processAndSaveAnalysisResults(AnalysisTask task, List<ExcelField> allFields, JsonNode analysisNode) {
        // 验证任务
        if (task == null) {
            logger.error("Task is null when trying to save rules");
            throw new IllegalArgumentException("Task cannot be null");
        }

        if (task.getId() == null) {
            logger.error("Task ID is null when trying to save rules for task: {}", task);
            throw new IllegalArgumentException("Task ID cannot be null");
        }

        // 验证任务是否存在于数据库
        if (!taskRepository.existsById(task.getId())) {
            logger.error("Task {} does not exist in database", task.getId());
            throw new RuntimeException("Task not found in database: " + task.getId());
        }

        List<FieldRule> rulesToSave = new ArrayList<>();

        // 提取规则
        JsonNode rulesNode = analysisNode.get("rules");
        if (rulesNode != null && rulesNode.isArray()) {
            for (JsonNode ruleNode : rulesNode) {
                if (ruleNode.has("fields") && ruleNode.has("type") && ruleNode.has("content")) {
                    JsonNode fieldsNode = ruleNode.get("fields");
                    if (!fieldsNode.isArray() || fieldsNode.size() == 0) {
                        logger.warn("Task {} - Skipping rule due to invalid fields array: {}", task.getId(), ruleNode.toString());
                        continue;
                    }

                    FieldRule rule = new FieldRule();
                    rule.setTask(task); // 确保设置task
                    
                    // 直接将fields数组保存到fieldNames
                    try {
                        rule.setFieldNames(fieldsNode.toString());
                    } catch (Exception e) {
                        logger.error("Task {} - Failed to set field names for rule: {}", task.getId(), e.getMessage());
                        continue;
                    }

                    try {
                        rule.setRuleType(FieldRule.RuleType.valueOf(ruleNode.get("type").asText().toUpperCase()));
                    } catch (IllegalArgumentException e) {
                        logger.warn("Task {} - Invalid rule type: {}", task.getId(), ruleNode.get("type").asText());
                        continue;
                    }

                    rule.setRuleContent(ruleNode.get("content").asText());
                    rule.setConfidence(ruleNode.has("confidence") ? ruleNode.get("confidence").asDouble() : 1.0);
                    rulesToSave.add(rule);
                }
            }
        }

        // 保存提取的规则
        if (!rulesToSave.isEmpty()) {
            try {
                ruleRepository.saveAll(rulesToSave);
                logger.debug("Task {} - Saved {} rules.", task.getId(), rulesToSave.size());
            } catch (Exception e) {
                logger.error("Task {} - Failed to save rules: {}", task.getId(), e.getMessage());
                throw e;
            }
        }
    }

    private ExcelField findFieldByNameAndTable(List<ExcelField> fields, String fieldName, String tableName) {
        return fields.stream()
                .filter(f -> f.getFieldName().equals(fieldName) && 
                    (tableName == null || tableName.equals(f.getTableName())))
                .findFirst()
                .orElse(null);
    }

    /**
     * Initializes or resets the progress tracking map for a task.
     */
    private void initializeTaskProgress(String taskId) {
        taskProgressMap.computeIfAbsent(taskId, k -> {
            Map<String, Object> progress = new ConcurrentHashMap<>(); // Use ConcurrentHashMap for thread safety
            progress.put("currentStep", STEP_START);
        progress.put("currentStepProgress", 0);
            progress.put("currentStepMessage", "Initializing...");
            progress.put("overallProgress", 0); // Will be updated by updateTaskProgress
            progress.put("detailedLogs", Collections.synchronizedList(new ArrayList<>())); // Thread-safe list
            return progress;
        });
        // Reset message for existing tasks being resumed
        Map<String, Object> progressInfo = taskProgressMap.get(taskId);
        progressInfo.put("currentStepMessage", "Resuming analysis...");
        progressInfo.put("currentStepProgress", 0);

    }

    /**
     * Updates the live progress information for a task.
     */
    private void updateTaskProgress(String taskId, String step, String message, int stepProgress) {
        Map<String, Object> progressInfo = taskProgressMap.get(taskId);
        if (progressInfo != null) {
            progressInfo.put("currentStep", step);
            progressInfo.put("currentStepProgress", stepProgress);
            progressInfo.put("currentStepMessage", message);
            
            // Calculate overall progress based on defined steps
            int overallProgress = calculateOverallProgress(step, stepProgress);
            progressInfo.put("overallProgress", overallProgress);
            
             // Update task entity progress (optional, maybe only on step completion)
             try {
                AnalysisTask task = taskRepository.findById(taskId).orElse(null);
                if (task != null && task.getStatus() == AnalysisTask.TaskStatus.PROCESSING) {
                     task.setProgress(overallProgress);
                     // Avoid saving task entity on every minor progress update if performance is critical
                     // taskRepository.save(task);
                }
             } catch (Exception e) {
                  logger.error("Failed to update task progress in DB for task {}: {}", taskId, e.getMessage());
             }


            // Add detailed log entry (thread-safe)
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> logs = (List<Map<String, Object>>) progressInfo.get("detailedLogs");
            Map<String, Object> logEntry = new HashMap<>(); // Log entry itself doesn't need to be concurrent
            logEntry.put("timestamp", LocalDateTime.now().toString()); // Use ISO format for consistency
            logEntry.put("step", step);
            logEntry.put("message", message);
            logEntry.put("progress", stepProgress);
            logs.add(logEntry); // Add to synchronized list

            logger.info("Task {} - Progress: [Step: {}] {} ({}%) Overall: {}%", taskId, step, message, stepProgress, overallProgress);

        } else {
            logger.warn("Attempted to update progress for unknown or untracked task: {}", taskId);
        }
    }

    // No longer need getStatusFromStep as task status is managed explicitly


    /**
     * Calculates overall progress percentage based on the current step.
     * Adjusted for the new steps.
     */
    private int calculateOverallProgress(String currentStep, int stepProgress) {
        // Define step weights that sum to 100
        Map<String, Integer> stepWeights = Map.of(
            STEP_START, 0,
            STEP_EXCEL_PROCESSING, 15,    // Excel处理占15%
            STEP_WORD_PROCESSING, 20,     // Word处理占20%
            STEP_CHUNK_ANALYSIS, 50,      // 分块分析占50%
            STEP_RESULT_GENERATION, 15,   // 结果生成占15%
            STEP_COMPLETE, 0              // 完成步骤不增加进度
        );

        // Calculate cumulative progress up to the current step
        int totalProgress = 0;
        boolean reachedCurrentStep = false;
        
        for (String step : Arrays.asList(STEP_START, STEP_EXCEL_PROCESSING, STEP_WORD_PROCESSING, 
                                        STEP_CHUNK_ANALYSIS, STEP_RESULT_GENERATION, STEP_COMPLETE)) {
            if (step.equals(currentStep)) {
                reachedCurrentStep = true;
                // Add partial progress for current step
                totalProgress += (int) ((stepProgress / 100.0) * stepWeights.get(step));
                break;
            } else {
                // Add full weight for completed steps
                totalProgress += stepWeights.get(step);
            }
        }

        // Ensure progress is between 0 and 100
        return Math.min(100, Math.max(0, totalProgress));
    }

    /**
     * Gets the current progress information for a task.
     */
    public Map<String, Object> getTaskProgress(String taskId) {
        Map<String, Object> progress = taskProgressMap.get(taskId);
        if (progress == null) {
            // Try to fetch task from DB to provide some info if it exists
            Optional<AnalysisTask> taskOpt = taskRepository.findById(taskId);
            if (taskOpt.isPresent()) {
                AnalysisTask task = taskOpt.get();
                 progress = new HashMap<>();
                 progress.put("currentStep", task.getLastCompletedStep());
                 progress.put("currentStepProgress", 0); // Assume 0 if not actively tracked
                 progress.put("currentStepMessage", "Task status: " + task.getStatus());
                 progress.put("overallProgress", task.getProgress() != null ? task.getProgress() : 0); // Check progress for null
                 progress.put("detailedLogs", new ArrayList<>()); // No live logs available
                 return progress;
            } else {
                // Task doesn't exist or isn't tracked
            progress = new HashMap<>();
            progress.put("currentStep", "unknown");
            progress.put("currentStepProgress", 0);
                progress.put("currentStepMessage", "Task not found or not started");
            progress.put("overallProgress", 0);
            progress.put("detailedLogs", new ArrayList<>());
        }
        }
        // Return a copy to prevent external modification
        return new HashMap<>(progress);
    }

    /**
     * Processes Excel documents for a task, extracting field information.
     * (Essentially unchanged, but called within the resumable flow).
     */
    @Transactional // Ensure transactional context
    protected void processExcelDocuments(AnalysisTask task) {
        logger.info("Task {} - Starting Excel processing.", task.getId());
        // Clear existing fields if reprocessing? Decide based on requirements.
        // For resumability, maybe only add new fields? Or update existing ones?
        // Current logic updates existing, adds new. Let's keep that.
        // fieldRepository.deleteByTask(task); // Uncomment if fields should be fully replaced on re-run

        Set<String> processedPaths = new HashSet<>(); // Avoid processing same file multiple times if listed twice
        for (String excelPath : task.getExcelFilePaths()) {
             if (!processedPaths.add(excelPath)) continue; // Skip if already processed this path

            try {
                logger.debug("Task {} - Processing Excel file: {}", task.getId(), excelPath);
                List<ExcelProcessor.ElementInfo> fieldInfos = documentService.extractExcelFields(excelPath);
                if (fieldInfos.isEmpty()) {
                     logger.warn("Task {} - No fields extracted from Excel file: {}", task.getId(), excelPath);
                     continue;
                }

                List<ExcelField> fieldsToSave = new ArrayList<>();
                for (ExcelProcessor.ElementInfo fieldInfo : fieldInfos) {
                    if (fieldInfo.getValue() == null || fieldInfo.getValue().trim().isEmpty()) {
                        logger.warn("Task {} - Skipping field with empty name from {}", task.getId(), excelPath);
                        continue;
                    }
                    // Use findByTaskAndFieldName for potential updates
                    Optional<ExcelField> existingFieldOpt = fieldRepository.findByTaskAndTableNameAndFieldName(task, fieldInfo.getTableName(), fieldInfo.getValue());

                    ExcelField field;
                    if (existingFieldOpt.isPresent()) {
                        field = existingFieldOpt.get();
                        logger.debug("Task {} - Updating existing field: {}", task.getId(), field.getFieldName());
                        // Update description or other relevant info if needed
                         field.setDescription(fieldInfo.getComment()); // Example update
                    } else {
                        field = new ExcelField();
                        field.setTask(task);
                        field.setTableName(fieldInfo.getTableName());
                        field.setFieldName(fieldInfo.getValue());
                        field.setFieldType("STRING"); // 默认类型
                        field.setDescription(fieldInfo.getComment());
                         logger.debug("Task {} - Creating new field: {}", task.getId(), field.getFieldName());
                    }
                    fieldsToSave.add(field);
                }
                if (!fieldsToSave.isEmpty()) {
                    try {
                        fieldRepository.saveAll(fieldsToSave);
                        logger.info("Task {} - Saved/Updated {} fields from {}", task.getId(), fieldsToSave.size(), excelPath);
                    } catch (Exception e) {
                        logger.warn("Task {} - Error saving fields, trying to save one by one: {}", task.getId(), e.getMessage());
                        // If batch save fails, try saving one by one
                        for (ExcelField field : fieldsToSave) {
                            try {
                                fieldRepository.save(field);
                            } catch (Exception ex) {
                                logger.warn("Task {} - Failed to save field {}: {}", task.getId(), field.getFieldName(), ex.getMessage());
                                // Continue with next field
                            }
                        }
                    }
                }

            } catch (Exception e) {
                logger.error("Task {} - Failed to process Excel document: {}", task.getId(), excelPath, e);
                // Continue with next file instead of failing the whole process
            }
        }
         logger.info("Task {} - Finished Excel processing.", task.getId());
    }

    /**
     * Generates the final analysis result after all steps are complete.
     * (Largely unchanged logic, but called within the new flow).
     * Ensures all relations/rules are fetched from the DB for the final report.
     */
     @Transactional(readOnly = true) // Reading data to generate result
     protected AnalysisResult generateAnalysisResult(AnalysisTask task) throws JsonProcessingException {
        logger.info("Task {} - Generating final analysis result.", task.getId());
        List<ExcelField> fields = fieldRepository.findByTask(task);
        // Fetch ALL relations and rules associated with the task directly from the DB
        List<FieldRule> rules = ruleRepository.findByTask(task);

        logger.info("Task {} - Found {} fields, {} rules for final report.",
                task.getId(), fields.size(), rules.size());

        // Compile rules using AI Service (if applicable)
        String compiledRules = null;
        if (!rules.isEmpty()) {
            try {
                 logger.debug("Task {} - Compiling rules for {} fields.", task.getId(), rules.size());
                 compiledRules = aiService.compileRules(rules);
                 logger.info("Task {} - Rules compiled successfully.", task.getId());
            } catch (Exception e) {
                 logger.error("Task {} - Failed to compile rules: {}", task.getId(), e.getMessage());
                 // Decide if this is critical - maybe proceed without compiled rules?
                 // throw new RuntimeException("Failed to compile rules: " + e.getMessage(), e);
            }
        }


        // Generate final analysis report using AI Service
        JsonNode reportNode;
        try {
            logger.debug("Task {} - Generating final analysis report.", task.getId());
            // Pass fetched data to the report generation service
            String reportJson = aiService.generateAnalysisReport(task.getTaskName(), fields, rules);
            reportNode = objectMapper.readTree(reportJson);
            logger.info("Task {} - Final analysis report generated.", task.getId());
        } catch (Exception e) {
            logger.error("Task {} - Failed to generate analysis report: {}", task.getId(), e.getMessage());
            // Create a fallback report node
            reportNode = objectMapper.createObjectNode()
                .put("error", "Failed to generate report: " + e.getMessage())
                .put("summary", "Analysis completed, but final report generation failed.");
        }

        // --- Assemble the final AnalysisResult entity ---
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("report", reportNode);
        resultMap.put("compiledRules", compiledRules); // Include compiled rules (or error node)
        resultMap.put("fieldCount", fields.size());
        resultMap.put("ruleCount", rules.size());
        // Add original field rules to the result
        resultMap.put("fieldRules", rules.stream()
            .map(rule -> {
                Map<String, Object> ruleMap = new HashMap<>();
                ruleMap.put("fieldNames", rule.getFieldNames());
                ruleMap.put("ruleType", rule.getRuleType().name());
                ruleMap.put("ruleContent", rule.getRuleContent());
                ruleMap.put("confidence", rule.getConfidence());
                return ruleMap;
            })
            .collect(Collectors.toList()));

        // Check if a result already exists, update it, otherwise create new
        AnalysisResult result = resultRepository.findByTask(task).orElse(new AnalysisResult());
        result.setTask(task);
        result.setCompletedTime(LocalDateTime.now());
        result.setResultJson(objectMapper.writeValueAsString(resultMap));
        result.setSummary(reportNode.has("summary") ? reportNode.get("summary").asText("Analysis completed.") : "Analysis completed.");
        result.setErrorMessage(null); // Clear previous errors if generation succeeds now
        result.setFieldCount(fields.size()); // Set field count

        logger.info("Task {} - Saving final analysis result.", task.getId());
        return resultRepository.save(result);
    }


    // --- Standard Service Methods (Getters, Delete, Cancel) ---
    // These generally remain the same, but ensure they handle new states if necessary.

    /**
     * Gets a list of all tasks, ordered by creation time descending.
     */
    public List<AnalysisTask> getAllTasks() {
        return taskRepository.findAllByOrderByCreatedTimeDesc();
    }

    /**
     * Gets task details by ID.
     */
    public AnalysisTask getTaskById(String taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));
    }

    /**
     * Gets the analysis result for a given task ID.
     */
    public AnalysisResult getResultByTaskId(String taskId) {
        AnalysisTask task = getTaskById(taskId); // Reuse existing method
        return resultRepository.findByTask(task)
                .orElseThrow(() -> new RuntimeException("Analysis result not found for task: " + taskId));
    }

    /**
     * Cancels a task currently in PROCESSING state.
     */
    @Transactional
    public void cancelTask(String taskId) {
        AnalysisTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));
        
        // Allow cancellation if PENDING or PROCESSING
        if (task.getStatus() != AnalysisTask.TaskStatus.PROCESSING && task.getStatus() != AnalysisTask.TaskStatus.PENDING) {
            throw new RuntimeException("Cannot cancel task in status: " + task.getStatus());
        }
        
        task.setStatus(AnalysisTask.TaskStatus.FAILED);
        // Preserve the last completed step for potential resumption
        // Don't change lastCompletedStep, allows potential resume later
        taskRepository.save(task);
        
        // Create or update the result to indicate cancellation
        AnalysisResult result = resultRepository.findByTask(task).orElse(new AnalysisResult());
        result.setTask(task);
        result.setErrorMessage("Task cancelled by user.");
        result.setCompletedTime(LocalDateTime.now()); // Record cancellation time
        resultRepository.save(result);
        
        logger.info("Task {} has been cancelled by the user.", taskId);
    }

    /**
     * Resumes a failed task from its last completed step.
     */
    @Transactional
    public void resumeTask(String taskId) {
        AnalysisTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));
        
        // Only allow resuming failed tasks
        if (task.getStatus() != AnalysisTask.TaskStatus.FAILED) {
            throw new RuntimeException("Cannot resume task in status: " + task.getStatus());
        }
        
        // Reset status to PENDING to allow execution
        task.setStatus(AnalysisTask.TaskStatus.PENDING);
        taskRepository.save(task);
        
        logger.info("Task {} has been resumed from step: {}", taskId, task.getLastCompletedStep());
    }

    /**
     * Deletes a task and all its associated data (chunks, fields, relations, rules, results, files).
     */
    @Transactional
    public void deleteTask(String taskId) {
        AnalysisTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        logger.info("Starting deletion for task {}: {}", taskId, task.getTaskName());

        // Delete associated data first (order matters for potential FK constraints)
        logger.debug("Task {} - Deleting analysis result...", taskId);
        resultRepository.deleteByTask(task);

        logger.debug("Task {} - Deleting field rules...", taskId);
        ruleRepository.deleteByTask(task);

        logger.debug("Task {} - Deleting excel fields...", taskId);
        fieldRepository.deleteByTask(task);

        logger.debug("Task {} - Deleting word chunks...", taskId);
        chunkRepository.deleteByTask(task);

        // Delete the task entity itself
        logger.debug("Task {} - Deleting task entity...", taskId);
        taskRepository.delete(task);

        // Remove from progress map
        taskProgressMap.remove(taskId);

        // Delete associated files
        logger.debug("Task {} - Deleting associated files...", taskId);
        deleteTaskFiles(task); // Reuse existing helper method

        logger.info("Successfully deleted task {} and all associated data.", taskId);
    }

    /**
     * Helper method to delete files associated with a task.
     * (Unchanged).
     */
    private void deleteTaskFiles(AnalysisTask task) {
         // Use Optional to avoid NPE if paths are null/empty
         Optional.ofNullable(task.getWordFilePaths()).orElse(Collections.emptyList()).forEach(filePath -> deleteFile(filePath, "Word"));
         Optional.ofNullable(task.getExcelFilePaths()).orElse(Collections.emptyList()).forEach(filePath -> deleteFile(filePath, "Excel"));
         Optional.ofNullable(task.getResultFilePath()).filter(s -> !s.isEmpty()).ifPresent(filePath -> deleteFile(filePath, "Result"));
    }

    private void deleteFile(String filePath, String fileType) {
         try {
                File file = new File(filePath);
                if (file.exists()) {
                 if (file.delete()) {
                     logger.info("Deleted {} file: {}", fileType, filePath);
                    } else {
                     logger.warn("Could not delete {} file: {}", fileType, filePath);
                 }
                    } else {
                  logger.warn("{} file not found for deletion: {}", fileType, filePath);
            }
        } catch (Exception e) {
             logger.error("Error deleting {} file {}: {}", fileType, filePath, e.getMessage(), e);
        }
    }


    /**
     * Gets the detailed logs for a task from the progress map.
     */
    public List<Map<String, Object>> getTaskLogs(String taskId) {
        Map<String, Object> progress = taskProgressMap.get(taskId);
        if (progress != null && progress.containsKey("detailedLogs")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> logs = (List<Map<String, Object>>) progress.get("detailedLogs");
            // Return a copy of the list to prevent external modification issues
             return new ArrayList<>(logs);
        }
        // Return empty list if no logs found or task not tracked
        return new ArrayList<>();
    }


    // --- Potentially deprecated or changed methods ---
    // getFieldRelations, getFieldRules, getAnalysisSummary, exportResults might need adjustment
    // if the way results are stored/accessed changes significantly. For now, they rely on
    // fetching all relations/rules from the DB, which should still work.

    public List<FieldRule> getFieldRules(AnalysisTask task) {
        logger.debug("Fetching rules for task {}", task.getId());
        return ruleRepository.findByTask(task);
    }

    public String getAnalysisSummary(AnalysisTask task) {
        List<FieldRule> rules = getFieldRules(task);

        StringBuilder summary = new StringBuilder();
        summary.append("Analysis Summary for Task: ").append(task.getTaskName()).append(" (ID: ").append(task.getId()).append(")\n\n");

        summary.append("Field Rule Analysis (").append(rules.size()).append(" extracted):\n");
        if (rules.isEmpty()) {
            summary.append("   - No rules extracted.\n");
        } else {
            // Group rules by field names
            Map<String, List<FieldRule>> rulesByField = rules.stream()
                .filter(rule -> rule.getFieldNames() != null && !rule.getFieldNames().isEmpty())
                .collect(Collectors.groupingBy(FieldRule::getFieldNames));

            rulesByField.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String fieldNamesStr;
                    try {
                        String[] fieldNames = objectMapper.readValue(entry.getKey(), String[].class);
                        fieldNamesStr = String.join(", ", fieldNames);
                    } catch (Exception e) {
                        logger.warn("Failed to parse field names: {}, using raw value", entry.getKey());
                        fieldNamesStr = entry.getKey();
                    }
                    
                    summary.append("   - Field(s): '").append(fieldNamesStr).append("':\n");
                    entry.getValue().forEach(rule -> {
                        summary.append("     - [").append(rule.getRuleType()).append("] ")
                            .append(rule.getRuleContent())
                            .append(" (Confidence: ").append(String.format("%.2f", rule.getConfidence())).append(")\n");
                    });
                });
        }

        return summary.toString();
    }

    public String exportResults(AnalysisTask task) {
        // This method uses getAnalysisSummary, so it should reflect the latest data.
        try {
            Path exportDir = Paths.get("exports");
            Files.createDirectories(exportDir); // Ensure directory exists

            String fileName = String.format("analysis_result_%s_%s.txt",
                task.getTaskName().replaceAll("[^a-zA-Z0-9.-]", "_"), // Sanitize name
                task.getId());
            Path filePath = exportDir.resolve(fileName);

            String summaryContent = getAnalysisSummary(task); // Get the summary

            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write("##################################################\n");
                writer.write("# Analysis Report for Task: " + task.getTaskName() + "\n");
                writer.write("# Task ID: " + task.getId() + "\n");
                writer.write("# Generated on: " + LocalDateTime.now() + "\n");
                writer.write("##################################################\n\n");
                writer.write(summaryContent);
            }

            logger.info("Exported analysis results for task {} to: {}", task.getId(), filePath.toString());
            return filePath.toString();
        } catch (IOException e) {
            logger.error("Failed to export results for task {}: {}", task.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to export results: " + e.getMessage(), e);
        }
    }

    // --- AI Service Call with Retry ---
    // Keep the generic retry wrapper, adjust validation if needed.

    private JsonNode callAIServiceWithRetry(String serviceName, Supplier<String> serviceCall) {
        int attempts = 0;
        Exception lastException = null;

        while (attempts < MAX_RETRY_ATTEMPTS) {
            attempts++;
            try {
                logger.debug("Calling AI Service '{}' (Attempt {}/{}) in thread {}", serviceName, attempts, MAX_RETRY_ATTEMPTS, 
                        Thread.currentThread().getName());
                
                // 使用 CompletableFuture 添加超时控制
                CompletableFuture<String> future = CompletableFuture.supplyAsync(serviceCall, analysisTaskExecutor);
                String result = future.get(AI_SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS); // 使用新的超时时间
                
                if (result == null || result.trim().isEmpty()) {
                    logger.warn("AI Service '{}' returned empty or null result (Attempt {}/{})", serviceName, attempts, MAX_RETRY_ATTEMPTS);
                    lastException = new RuntimeException("AI service returned empty result.");
                    if (attempts < MAX_RETRY_ATTEMPTS) {
                        Thread.sleep(RETRY_DELAY_MS * attempts);
                        continue;
                    } else {
                        break;
                    }
                }

                JsonNode response = objectMapper.readTree(result);
                
                if (response.isObject() || response.isArray()) {
                    logger.debug("AI Service '{}' call successful (Attempt {}/{})", serviceName, attempts, MAX_RETRY_ATTEMPTS);
                    return response;
                } else {
                    logger.warn("AI Service '{}' returned unexpected JSON type: {} (Attempt {}/{})", serviceName, response.getNodeType(), attempts, MAX_RETRY_ATTEMPTS);
                    lastException = new RuntimeException("AI service returned unexpected JSON type: " + response.getNodeType());
                }

            } catch (TimeoutException e) {
                logger.warn("AI Service '{}' call timed out (Attempt {}/{}): {}", serviceName, attempts, MAX_RETRY_ATTEMPTS, e.getMessage());
                lastException = e;
            } catch (JsonProcessingException jsonEx) {
                lastException = jsonEx;
                logger.warn("AI Service '{}' returned invalid JSON (Attempt {}/{}): {}", serviceName, attempts, MAX_RETRY_ATTEMPTS, jsonEx.getMessage());
            } catch (Exception e) {
                lastException = e;
                logger.warn("AI Service '{}' call failed (Attempt {}/{}): {}", serviceName, attempts, MAX_RETRY_ATTEMPTS, e.getMessage());
            }

            if (attempts < MAX_RETRY_ATTEMPTS) {
                try {
                    long delay = RETRY_DELAY_MS * attempts;
                    logger.info("Retrying AI Service '{}' after {} ms delay.", serviceName, delay);
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry delay interrupted for AI Service '" + serviceName + "'", ie);
                }
            }
        }

        logger.error("AI Service '{}' call failed permanently after {} attempts.", serviceName, MAX_RETRY_ATTEMPTS);
        throw new RuntimeException("AI Service '" + serviceName + "' failed after " + MAX_RETRY_ATTEMPTS + " attempts: " + 
                (lastException != null ? lastException.getMessage() : "Unknown error"), lastException);
    }


} 