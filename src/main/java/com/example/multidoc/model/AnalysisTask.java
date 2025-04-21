package com.example.multidoc.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
public class AnalysisTask {
    
    @Id
    @Column(columnDefinition = "CHAR(36)")
    private String id;
    
    private String taskName;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "task_word_files", joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "file_path")
    private List<String> wordFilePaths = new ArrayList<>();
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "task_excel_files", joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "file_path")
    private List<String> excelFilePaths = new ArrayList<>();
    
    private LocalDateTime createdTime;
    
    @Enumerated(EnumType.STRING)
    private TaskStatus status;
    
    private String resultFilePath;
    
    private Integer chunkSize;
    
    private String lastCompletedStep;
    
    private Integer progress;
    
    public enum TaskStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }

    // Default constructor
    public AnalysisTask() {
        this.id = UUID.randomUUID().toString();
        this.createdTime = LocalDateTime.now();
        this.progress = 0;
        this.status = TaskStatus.PENDING;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public List<String> getWordFilePaths() {
        return wordFilePaths;
    }

    public void setWordFilePaths(List<String> wordFilePaths) {
        this.wordFilePaths = wordFilePaths;
    }

    public List<String> getExcelFilePaths() {
        return excelFilePaths;
    }

    public void setExcelFilePaths(List<String> excelFilePaths) {
        this.excelFilePaths = excelFilePaths;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public String getResultFilePath() {
        return resultFilePath;
    }

    public void setResultFilePath(String resultFilePath) {
        this.resultFilePath = resultFilePath;
    }

    public Integer getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(Integer chunkSize) {
        this.chunkSize = chunkSize;
    }

    public String getLastCompletedStep() {
        return lastCompletedStep;
    }

    public void setLastCompletedStep(String lastCompletedStep) {
        this.lastCompletedStep = lastCompletedStep;
    }
} 