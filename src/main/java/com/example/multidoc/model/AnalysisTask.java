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
    
    private LocalDateTime createdTime;
    
    @Enumerated(EnumType.STRING)
    private TaskStatus status;
    
    @ElementCollection
    @CollectionTable(name = "word_files", joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "file_path")
    private List<String> wordFilePaths = new ArrayList<>();
    
    @ElementCollection
    @CollectionTable(name = "excel_files", joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "file_path")
    private List<String> excelFilePaths = new ArrayList<>();
    
    private String resultFilePath;
    
    private Integer chunkSize;
    
    public enum TaskStatus {
        CREATED, PROCESSING, COMPLETED, FAILED, WAITING_FOR_SELECTION
    }

    // Default constructor
    public AnalysisTask() {
        this.id = UUID.randomUUID().toString();
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

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
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
} 