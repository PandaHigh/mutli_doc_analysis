package com.example.multidoc.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "rule_validation_results")
public class RuleValidationResult {

    @Id
    private String id;
    private String taskId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status; // PROCESSING, COMPLETED, FAILED
    private int progress;
    
    @Lob
    private String validatedRules; // JSON格式的验证结果
    
    @Lob
    private String errorMessage;

    public RuleValidationResult() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public String getValidatedRules() {
        return validatedRules;
    }

    public void setValidatedRules(String validatedRules) {
        this.validatedRules = validatedRules;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
} 