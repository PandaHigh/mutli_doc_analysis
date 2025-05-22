package com.example.multidoc.service;

import com.example.multidoc.model.AnalysisTask;
import com.example.multidoc.model.TaskLog;
import com.example.multidoc.repository.AnalysisTaskRepository;
import com.example.multidoc.repository.TaskLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskServiceImpl implements TaskService {

    @Autowired
    private AnalysisTaskRepository taskRepository;

    @Autowired
    private TaskLogRepository logRepository;

    @Override
    public AnalysisTask getTaskById(String id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found: " + id));
    }

    @Override
    public List<TaskLog> getTaskLogs(String taskId) {
        return logRepository.findByTaskIdOrderByTimestampAsc(taskId);
    }

    @Override
    @Transactional
    public void addLog(AnalysisTask task, String message, String level) {
        TaskLog log = new TaskLog(task, message, level);
        try {
            logRepository.save(log);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    @Transactional
    public void updateTaskProgress(AnalysisTask task, int progress) {
        task.setProgress(progress);
        taskRepository.save(task);
    }

    @Override
    @Transactional
    public void updateTaskStatus(AnalysisTask task, String status) {
        task.setStatus(AnalysisTask.TaskStatus.valueOf(status));
        taskRepository.save(task);
    }
} 