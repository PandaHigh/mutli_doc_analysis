package com.example.multidoc.controller;

import com.example.multidoc.model.AnalysisTask;
import com.example.multidoc.model.RuleValidationResult;
import com.example.multidoc.service.AnalysisService;
import com.example.multidoc.service.RuleValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Controller
public class RuleValidationController {

    private static final Logger logger = LoggerFactory.getLogger(RuleValidationController.class);

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private RuleValidationService validationService;

    /**
     * 展示上传验证文件的页面
     */
    @GetMapping("/task/{taskId}/validate")
    public String showValidationForm(@PathVariable String taskId, Model model) {
        try {
            AnalysisTask task = analysisService.getTaskById(taskId);
            model.addAttribute("task", task);
            return "task/validate";
        } catch (Exception e) {
            logger.error("获取任务失败", e);
            model.addAttribute("error", "获取任务失败: " + e.getMessage());
            return "error";
        }
    }

    /**
     * 处理文件上传和验证请求
     */
    @PostMapping("/task/{taskId}/validate")
    public String validateRules(
            @PathVariable String taskId,
            @RequestParam("excelFiles") MultipartFile[] excelFiles,
            RedirectAttributes redirectAttributes) {

        try {
            if (excelFiles.length == 0 || excelFiles[0].isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "请至少上传一个Excel文件");
                return "redirect:/task/" + taskId + "/validate";
            }

            // 开始异步处理验证任务
            List<MultipartFile> files = List.of(excelFiles);
            CompletableFuture<RuleValidationResult> future = validationService.validateRules(taskId, files);

            // 立即返回，不等待处理完成
            redirectAttributes.addFlashAttribute("message", "提交验证规则任务成功！系统正在后台处理，请稍候查看结果");
            return "redirect:/task/" + taskId + "/validation-result";

        } catch (Exception e) {
            logger.error("提交验证任务失败", e);
            redirectAttributes.addFlashAttribute("error", "提交验证任务失败: " + e.getMessage());
            return "redirect:/task/" + taskId + "/validate";
        }
    }

    /**
     * 显示验证结果页面
     */
    @GetMapping("/task/{taskId}/validation-result")
    public String showValidationResult(@PathVariable String taskId, Model model) {
        try {
            AnalysisTask task = analysisService.getTaskById(taskId);
            model.addAttribute("task", task);

            // 检查是否有验证结果
            try {
                RuleValidationResult result = validationService.getValidationResult(taskId);
                model.addAttribute("result", result);
            } catch (Exception e) {
                logger.warn("获取验证结果失败: {}", e.getMessage());
                // 即使没有结果页面也可以显示，因为可能正在处理中
            }

            return "task/validation-result";
        } catch (Exception e) {
            logger.error("获取任务失败", e);
            model.addAttribute("error", "获取任务失败: " + e.getMessage());
            return "error";
        }
    }

    /**
     * 获取验证状态和进度的API
     */
    @GetMapping("/api/task/{taskId}/validation-status")
    @ResponseBody
    public ResponseEntity<?> getValidationStatus(@PathVariable String taskId) {
        try {
            RuleValidationResult result = validationService.getValidationResult(taskId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", result.getStatus());
            response.put("progress", result.getProgress());
            response.put("startTime", result.getStartTime());
            response.put("endTime", result.getEndTime());
            
            if (result.getStatus().equals("COMPLETED")) {
                response.put("validatedRules", result.getValidatedRules());
            } else if (result.getStatus().equals("FAILED")) {
                response.put("error", result.getErrorMessage());
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "status", "PENDING",
                "message", "验证还未开始或正在初始化"
            ));
        }
    }

    /**
     * 显示验证历史记录页面
     */
    @GetMapping("/task/{taskId}/validation-history")
    public String showValidationHistory(@PathVariable String taskId, Model model) {
        try {
            AnalysisTask task = analysisService.getTaskById(taskId);
            model.addAttribute("task", task);

            // 获取所有验证记录，按时间倒序排列
            List<RuleValidationResult> historyList = validationService.getValidationHistory(taskId);
            model.addAttribute("historyList", historyList);

            return "task/validation-history";
        } catch (Exception e) {
            logger.error("获取验证历史失败", e);
            model.addAttribute("error", "获取验证历史失败: " + e.getMessage());
            return "error";
        }
    }

    /**
     * 查看特定的历史验证结果
     */
    @GetMapping("/task/{taskId}/validation-result/{resultId}")
    public String showHistoricalValidationResult(@PathVariable String taskId, 
                                                @PathVariable String resultId, 
                                                Model model) {
        try {
            AnalysisTask task = analysisService.getTaskById(taskId);
            model.addAttribute("task", task);

            // 获取特定ID的验证结果
            RuleValidationResult result = validationService.getValidationResultById(resultId);
            model.addAttribute("result", result);

            return "task/validation-result";
        } catch (Exception e) {
            logger.error("获取历史验证结果失败", e);
            model.addAttribute("error", "获取历史验证结果失败: " + e.getMessage());
            return "error";
        }
    }
    
    /**
     * 删除验证结果
     */
    @DeleteMapping("/api/task/{taskId}/validation-result/{resultId}/delete")
    public String deleteValidationResult(@PathVariable String taskId,
                                        @PathVariable String resultId,
                                        RedirectAttributes redirectAttributes) {
        try {
            validationService.deleteValidationResult(resultId);
            redirectAttributes.addFlashAttribute("message", "验证记录删除成功");
        } catch (Exception e) {
            logger.error("删除验证记录失败", e);
            redirectAttributes.addFlashAttribute("error", "删除验证记录失败: " + e.getMessage());
        }
        return "redirect:/task/" + taskId + "/validation-history";
    }
} 