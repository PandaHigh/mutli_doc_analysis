package com.example.multidoc.controller;

import com.example.multidoc.model.AnalysisTask;
import com.example.multidoc.model.AnalysisResult;
import com.example.multidoc.model.FieldRule;
import com.example.multidoc.model.ExcelField;
import com.example.multidoc.service.AnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
public class TaskResultController {
    
    private static final Logger logger = LoggerFactory.getLogger(TaskResultController.class);
    
    @Autowired
    private AnalysisService analysisService;

    @GetMapping("/task/{taskId}/result")
    public String showResult(@PathVariable String taskId, Model model) {
        try {
            logger.info("获取任务结果，任务ID: {}", taskId);
            
            // 获取任务信息
            AnalysisTask task = analysisService.getTaskById(taskId);
            if (task == null) {
                logger.error("未找到任务: {}", taskId);
                model.addAttribute("error", "未找到任务");
                return "error";
            }
            
            // 检查任务状态
            if (task.getStatus() != AnalysisTask.TaskStatus.COMPLETED) {
                logger.warn("任务未完成，当前状态: {}", task.getStatus());
                model.addAttribute("error", "任务尚未完成，当前状态: " + task.getStatus());
                return "task/detail";
            }
            
            // 获取分析结果
            AnalysisResult result = analysisService.getResultByTaskId(taskId);
            if (result == null) {
                logger.error("未找到任务结果: {}", taskId);
                model.addAttribute("error", "未找到任务结果");
                return "error";
            }
            
            // 处理 summaryText，如果它是 JSON 字符串，则提取 summary 字段
            if (result.getSummaryText() != null && !result.getSummaryText().isEmpty()) {
                String summaryText = result.getSummaryText();
                
                // 检查是否为 JSON 格式，并提取 summary 字段
                if (summaryText.startsWith("{") && summaryText.endsWith("}")) {
                    try {
                        // 使用正则表达式查找 summary 字段
                        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"summary\"\\s*:\\s*\"(.+?)\"(?=,|\\})", java.util.regex.Pattern.DOTALL);
                        java.util.regex.Matcher matcher = pattern.matcher(summaryText);
                        
                        if (matcher.find()) {
                            String extractedSummary = matcher.group(1);
                            // 替换转义字符
                            extractedSummary = extractedSummary.replace("\\n", "\n").replace("\\\"", "\"");
                            result.setSummaryText(extractedSummary);
                            logger.info("成功从 JSON 中提取 summary 字段");
                        }
                    } catch (Exception e) {
                        logger.warn("尝试解析 summaryText 失败: {}", e.getMessage());
                    }
                }
            }
            
            // 获取字段规则
            List<FieldRule> fieldRules = analysisService.getFieldRules(task);
            
            // 获取所有Excel字段
            List<ExcelField> excelFields = analysisService.getExcelFieldsByTask(task);
            
            logger.info("任务：{}，结果状态：{}", task.getTaskName(), "找到");
            logger.info("分析报告长度：{}", result.getSummaryText() != null ? result.getSummaryText().length() : 0);
            logger.info("字段规则数量：{}", fieldRules != null ? fieldRules.size() : 0);
            
            // 添加模型属性
            model.addAttribute("task", task);
            model.addAttribute("result", result);
            model.addAttribute("fieldRules", fieldRules);
            model.addAttribute("excelFields", excelFields);
            
            return "task/result";
        } catch (Exception e) {
            logger.error("获取结果失败", e);
            model.addAttribute("error", "获取结果失败: " + e.getMessage());
            return "error";
        }
    }

    @GetMapping("/task/{taskId}/export")
    public String exportResults(@PathVariable String taskId, Model model) {
        try {
            AnalysisTask task = analysisService.getTaskById(taskId);
            String exportPath = analysisService.exportResults(task);
            return "redirect:/download?file=" + exportPath;
        } catch (Exception e) {
            logger.error("导出结果失败", e);
            model.addAttribute("error", "导出结果失败: " + e.getMessage());
            return "error";
        }
    }
} 