package com.example.multidoc.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class AnalysisResult {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "task_id")
    private AnalysisTask task;
    
    private LocalDateTime completedTime;
    
    // 存储JSON格式的汇总结果
    @Column(columnDefinition = "LONGTEXT")
    private String resultJson;
    
    // 存储结果摘要
    @Column(columnDefinition = "TEXT")
    private String summary;
    
    // 存储可能的错误信息
    @Column(columnDefinition = "TEXT")
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

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
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
} 