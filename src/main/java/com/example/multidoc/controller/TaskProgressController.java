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
        
        return ResponseEntity.ok(Map.of(
            "overallProgress", progress.get("overallProgress"),
            "currentStep", progress.get("currentStep"),
            "currentStepProgress", progress.get("currentStepProgress"),
            "currentStepMessage", progress.get("currentStepMessage"),
            "status", task.getStatus().name(),
            "logs", progress.get("detailedLogs")
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