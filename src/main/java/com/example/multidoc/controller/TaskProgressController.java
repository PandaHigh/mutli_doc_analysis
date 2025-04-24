package com.example.multidoc.controller;

import com.example.multidoc.model.AnalysisTask;
import com.example.multidoc.model.TaskLog;
import com.example.multidoc.service.AnalysisService;
import com.example.multidoc.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/task")
public class TaskProgressController {

    @Autowired
    private TaskService taskService;

    @Autowired
    private AnalysisService analysisService;

    @GetMapping("/{id}/progress")
    public String showProgress(@PathVariable String id, Model model) {
        AnalysisTask task = taskService.getTaskById(id);
        model.addAttribute("task", task);
        return "task/progress";
    }

    @GetMapping("/api/task/{id}/progress")
    public ResponseEntity<?> getProgress(@PathVariable String id) {
        AnalysisTask task = taskService.getTaskById(id);
        Map<String, Object> progress = analysisService.getTaskProgress(id);
        
        // 获取任务日志
        List<TaskLog> taskLogs = taskService.getTaskLogs(id);
        
        // 格式化日志数据
        List<Map<String, Object>> formattedLogs = taskLogs.stream()
            .map(log -> {
                Map<String, Object> formattedLog = new HashMap<>();
                formattedLog.put("timestamp", log.getLogTime().toString());
                formattedLog.put("step", log.getLogLevel()); // 使用logLevel作为step
                formattedLog.put("message", log.getMessage());
                formattedLog.put("progress", task.getProgress()); // 使用任务当前进度
                return formattedLog;
            })
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(Map.of(
            "overallProgress", progress.get("progress"),
            "currentStep", progress.get("lastCompletedStep"),
            "currentStepProgress", task.getProgress(),
            "currentStepMessage", "当前步骤: " + progress.get("lastCompletedStep"),
            "status", task.getStatus().name(),
            "logs", formattedLogs
        ));
    }

    // 进度响应对象
    private static class ProgressResponse {
        private int progress;
        private String status;
        private List<TaskLog> logs;

        public ProgressResponse(int progress, String status, List<TaskLog> logs) {
            this.progress = progress;
            this.status = status;
            this.logs = logs;
        }

        public int getProgress() {
            return progress;
        }

        public String getStatus() {
            return status;
        }

        public List<TaskLog> getLogs() {
            return logs;
        }
    }
} 