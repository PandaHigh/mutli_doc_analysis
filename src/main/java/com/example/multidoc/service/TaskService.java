package com.example.multidoc.service;

import com.example.multidoc.model.AnalysisTask;
import com.example.multidoc.model.TaskLog;

import java.util.List;

public interface TaskService {
    AnalysisTask getTaskById(String id);
    List<TaskLog> getTaskLogs(String taskId);
    void addLog(AnalysisTask task, String message, String level);
    void updateTaskProgress(AnalysisTask task, int progress);
    void updateTaskStatus(AnalysisTask task, String status);
} 