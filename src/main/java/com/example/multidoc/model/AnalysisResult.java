package com.example.multidoc.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "analysis_results")
public class AnalysisResult {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "task_id", nullable = false, unique = true)
    private AnalysisTask task;
    
    @Column(name = "completed_time")
    private LocalDateTime completedTime;
    
    @Column(name = "result_text", columnDefinition = "TEXT")
    private String resultText;
    
    @Column(name = "summary_text", columnDefinition = "TEXT")
    private String summaryText;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    // 添加字段计数
    private Integer fieldCount;

    // Default constructor
    public AnalysisResult() {
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AnalysisTask getTask() {
        return task;
    }

    public void setTask(AnalysisTask task) {
        this.task = task;
    }

    public LocalDateTime getCompletedTime() {
        return completedTime;
    }

    public void setCompletedTime(LocalDateTime completedTime) {
        this.completedTime = completedTime;
    }

    public String getResultText() {
        return resultText;
    }

    public void setResultText(String resultText) {
        this.resultText = resultText;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public void setSummaryText(String summaryText) {
        this.summaryText = summaryText;
    }

    public void setSummary(String summary) {
        this.summaryText = summary;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getFieldCount() {
        return fieldCount;
    }

    public void setFieldCount(Integer fieldCount) {
        this.fieldCount = fieldCount;
    }

    @Override
    public String toString() {
        return "AnalysisResult{" +
                "id=" + id +
                ", task=" + (task != null ? task.getId() : "null") +
                ", completedTime=" + completedTime +
                ", errorMessage='" + (errorMessage != null ? "有错误" : "无错误") + '\'' +
                '}';
    }
} 