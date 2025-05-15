package com.example.multidoc.service;

import com.example.multidoc.model.AnalysisTask;
import com.example.multidoc.model.FieldRule;
import com.example.multidoc.model.RuleValidationResult;
import com.example.multidoc.repository.RuleValidationResultRepository;
import com.example.multidoc.util.ExcelProcessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class RuleValidationService {

    private static final Logger logger = LoggerFactory.getLogger(RuleValidationService.class);
    
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private AIService aiService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private RuleValidationResultRepository validationResultRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ExcelProcessor excelProcessor;

    /**
     * 异步处理规则验证
     */
    @Async("analysisTaskExecutor")
    public CompletableFuture<RuleValidationResult> validateRules(String taskId, List<MultipartFile> excelFiles) {
        try {
            // 获取任务和现有规则
            AnalysisTask task = analysisService.getTaskById(taskId);
            List<FieldRule> existingRules = analysisService.getFieldRules(task);
            
            // 创建验证结果对象
            RuleValidationResult validationResult = new RuleValidationResult();
            validationResult.setId(UUID.randomUUID().toString());
            validationResult.setTaskId(taskId);
            validationResult.setStartTime(LocalDateTime.now());
            validationResult.setStatus("PROCESSING");
            validationResult.setProgress(0);
            validationResultRepository.save(validationResult);
            
            // 发送初始状态到WebSocket
            sendProgressUpdate(taskId, "开始处理验证", 0);
            
            // 保存上传的Excel文件
            List<String> excelFilePaths = new ArrayList<>();
            for (MultipartFile file : excelFiles) {
                String path = documentService.saveExcelDocument(file);
                excelFilePaths.add(path);
                sendProgressUpdate(taskId, "保存Excel文件: " + file.getOriginalFilename(), 10);
            }
            
            // 转换Excel为MD格式
            List<String> mdFilePaths = new ArrayList<>();
            for (String excelPath : excelFilePaths) {
                String mdPath = convertExcelToMd(excelPath);
                mdFilePaths.add(mdPath);
                sendProgressUpdate(taskId, "转换Excel到MD格式", 30);
            }
            
            // 准备输入数据给AI模型
            String rulesJson;
            try {
                // 简化规则对象，去掉task引用避免序列化问题
                List<Map<String, Object>> simplifiedRules = new ArrayList<>();
                for (FieldRule rule : existingRules) {
                    Map<String, Object> simplified = new HashMap<>();
                    simplified.put("id", rule.getId());
                    simplified.put("ruleType", rule.getRuleType().toString());
                    simplified.put("ruleContent", rule.getRuleContent());
                    simplified.put("fieldNames", rule.getFieldNames());
                    simplifiedRules.add(simplified);
                }
                rulesJson = objectMapper.writeValueAsString(simplifiedRules);
            } catch (Exception e) {
                throw new RuntimeException("规则序列化失败: " + e.getMessage(), e);
            }
            
            StringBuilder mdContent = new StringBuilder();
            for (String mdPath : mdFilePaths) {
                mdContent.append(Files.readString(Paths.get(mdPath))).append("\n\n");
            }
            
            sendProgressUpdate(taskId, "准备调用AI模型", 50);
            
            // 调用AI模型进行验证
            JsonNode validationResults = aiService.validateRules(rulesJson, mdContent.toString());
            
            sendProgressUpdate(taskId, "AI模型处理完成", 80);
            
            // 处理AI结果
            validationResult.setValidatedRules(validationResults.toString());
            validationResult.setEndTime(LocalDateTime.now());
            validationResult.setStatus("COMPLETED");
            validationResult.setProgress(100);
            validationResultRepository.save(validationResult);
            
            sendProgressUpdate(taskId, "验证完成", 100);
            
            return CompletableFuture.completedFuture(validationResult);
            
        } catch (Exception e) {
            logger.error("验证规则失败", e);
            sendProgressUpdate(taskId, "验证失败: " + e.getMessage(), -1);
            
            // 更新验证结果为失败状态
            RuleValidationResult result = validationResultRepository.findByTaskId(taskId)
                    .orElseGet(() -> {
                        RuleValidationResult newResult = new RuleValidationResult();
                        newResult.setId(UUID.randomUUID().toString());
                        newResult.setTaskId(taskId);
                        newResult.setStartTime(LocalDateTime.now());
                        return newResult;
                    });
            
            result.setStatus("FAILED");
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
            validationResultRepository.save(result);
            
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * 获取验证结果
     */
    public RuleValidationResult getValidationResult(String taskId) {
        return validationResultRepository.findLatestResultByTaskId(taskId)
                .orElseThrow(() -> new RuntimeException("验证结果不存在"));
    }
    
    /**
     * 获取验证历史记录
     */
    public List<RuleValidationResult> getValidationHistory(String taskId) {
        return validationResultRepository.findByTaskIdOrderByStartTimeDesc(taskId);
    }
    
    /**
     * 根据ID获取验证结果
     */
    public RuleValidationResult getValidationResultById(String resultId) {
        return validationResultRepository.findById(resultId)
                .orElseThrow(() -> new RuntimeException("验证结果不存在"));
    }
    
    /**
     * 删除验证结果
     */
    public void deleteValidationResult(String resultId) {
        logger.info("正在删除验证结果，ID: {}", resultId);
        // 检查记录是否存在
        if (!validationResultRepository.existsById(resultId)) {
            throw new RuntimeException("要删除的验证结果不存在");
        }
        validationResultRepository.deleteById(resultId);
        logger.info("验证结果删除成功，ID: {}", resultId);
    }
    
    /**
     * 将Excel转换为MD格式
     */
    private String convertExcelToMd(String excelPath) throws Exception {
        String fileName = Paths.get(excelPath).getFileName().toString();
        String mdFileName = fileName.substring(0, fileName.lastIndexOf('.')) + ".md";
        Path mdPath = Paths.get("uploads/md/" + mdFileName);
        
        // 确保目录存在
        Files.createDirectories(mdPath.getParent());
        
        // 使用ExcelProcessor直接转换为Markdown
        String mdContent = excelProcessor.convertExcelToMarkdown(excelPath);
        
        // 写入文件
        try (FileWriter writer = new FileWriter(mdPath.toFile())) {
            writer.write(mdContent);
        }
        
        return mdPath.toString();
    }
    
    /**
     * 发送进度更新到WebSocket
     */
    private void sendProgressUpdate(String taskId, String message, int progress) {
        Map<String, Object> progressData = new HashMap<>();
        progressData.put("taskId", taskId);
        progressData.put("message", message);
        progressData.put("progress", progress);
        progressData.put("timestamp", LocalDateTime.now().toString());
        
        messagingTemplate.convertAndSend("/topic/validation/" + taskId, progressData);
        logger.info("发送验证进度: {}%, {}", progress, message);
    }
} 