package com.example.multidoc.controller;

import com.example.multidoc.model.AnalysisTask;
import com.example.multidoc.model.FieldRule;
import com.example.multidoc.service.AnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
public class TaskResultController {

    @Autowired
    private AnalysisService analysisService;

    @GetMapping("/task/{taskId}/result")
    public String showResult(@PathVariable String taskId, Model model) {
        AnalysisTask task = analysisService.getTaskById(taskId);
        List<FieldRule> fieldRules = analysisService.getFieldRules(task);
        
        model.addAttribute("task", task);
        model.addAttribute("fieldRules", fieldRules);
        
        return "task/result";
    }

    @GetMapping("/task/{taskId}/export")
    public String exportResults(@PathVariable String taskId) {
        AnalysisTask task = analysisService.getTaskById(taskId);
        String exportPath = analysisService.exportResults(task);
        return "redirect:/download?file=" + exportPath;
    }
} 