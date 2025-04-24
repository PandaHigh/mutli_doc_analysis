package com.example.multidoc.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.example.multidoc.util.StringListConverter;

@Entity
@Table(name = "analysis_tasks")
public class AnalysisTask {
    
    @Id
    private String id;
    
    @Column(name = "task_name", nullable = false)
    private String taskName;
    
    @Column(name = "created_time", nullable = false)
    private LocalDateTime createdTime;
    
    @Column(name = "completed_time")
    private LocalDateTime completedTime;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TaskStatus status;
    
    @Column
    private int progress = 0;
    
    @Column(name = "last_completed_step")
    private String lastCompletedStep = "start";
    
    @Column(name = "chunk_size")
    private Integer chunkSize = 5000;
    
    @Column(name = "word_file_paths", columnDefinition = "json")
    @Convert(converter = StringListConverter.class)
    private List<String> wordFilePaths;
    
    @Column(name = "excel_file_paths", columnDefinition = "json")
    @Convert(converter = StringListConverter.class)
    private List<String> excelFilePaths;
    
    @JsonIgnore
    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExcelField> fields;
    
    @JsonIgnore
    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WordSentence> sentences;
    
    public enum TaskStatus {
        PENDING, RUNNING, PROCESSING, COMPLETED, FAILED, CANCELLED
    }
    
    public AnalysisTask() {
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
    
    public LocalDateTime getCompletedTime() {
        return completedTime;
    }
    
    public void setCompletedTime(LocalDateTime completedTime) {
        this.completedTime = completedTime;
    }
    
    public TaskStatus getStatus() {
        return status;
    }
    
    public void setStatus(TaskStatus status) {
        this.status = status;
    }
    
    public int getProgress() {
        return progress;
    }
    
    public void setProgress(int progress) {
        this.progress = progress;
    }
    
    public String getLastCompletedStep() {
        return lastCompletedStep;
    }
    
    public void setLastCompletedStep(String lastCompletedStep) {
        this.lastCompletedStep = lastCompletedStep;
    }
    
    public Integer getChunkSize() {
        return chunkSize;
    }
    
    public void setChunkSize(Integer chunkSize) {
        this.chunkSize = chunkSize;
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
    
    public List<ExcelField> getFields() {
        return fields;
    }
    
    public void setFields(List<ExcelField> fields) {
        this.fields = fields;
    }
    
    public List<WordSentence> getSentences() {
        return sentences;
    }
    
    public void setSentences(List<WordSentence> sentences) {
        this.sentences = sentences;
    }
    
    public String getResultFilePath() {
        return "uploads/results/" + id + "_result.json";
    }
    
    @Override
    public String toString() {
        return "AnalysisTask{" +
                "id='" + id + '\'' +
                ", taskName='" + taskName + '\'' +
                ", status=" + status +
                ", progress=" + progress +
                ", lastCompletedStep='" + lastCompletedStep + '\'' +
                '}';
    }
} 