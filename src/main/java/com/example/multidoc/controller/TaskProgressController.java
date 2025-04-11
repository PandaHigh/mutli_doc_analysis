package com.example.multidoc.controller;

import com.example.multidoc.model.AnalysisTask;
import com.example.multidoc.service.AnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequestMapping("/task")
public class TaskProgressController {

    @Autowired
    private AnalysisService analysisService;

    @GetMapping("/{taskId}/progress")
    public String showProgress(@PathVariable String taskId, Model model) {
        AnalysisTask task = analysisService.getTaskById(taskId);
        Map<String, Object> progress = analysisService.getTaskProgress(taskId);
        
        model.addAttribute("task", task);
        model.addAttribute("progress", progress);
        
        return "task/progress";
    }

    @GetMapping("/api/{taskId}/progress")
    @ResponseBody
    public Map<String, Object> getProgress(@PathVariable String taskId) {
        Map<String, Object> progress = analysisService.getTaskProgress(taskId);
        progress.put("logs", analysisService.getTaskLogs(taskId));
        return progress;
    }
} 