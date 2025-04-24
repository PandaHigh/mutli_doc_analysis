package com.example.multidoc.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "task_logs")
public class TaskLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "task_id", nullable = false)
    private AnalysisTask task;
    
    @Column(name = "log_time")
    private LocalDateTime logTime;
    
    @Column(name = "log_level", nullable = false, length = 50)
    private String logLevel;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;
    
    // Default constructor
    public TaskLog() {
        this.logTime = LocalDateTime.now();
    }
    
    // Constructor with parameters
    public TaskLog(AnalysisTask task, String message, String logLevel) {
        this.task = task;
        this.message = message;
        this.logLevel = logLevel;
        this.logTime = LocalDateTime.now();
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
    
    public LocalDateTime getLogTime() {
        return logTime;
    }
    
    public void setLogTime(LocalDateTime logTime) {
        this.logTime = logTime;
    }
    
    public String getLogLevel() {
        return logLevel;
    }
    
    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    @Override
    public String toString() {
        return "TaskLog{" +
                "id=" + id +
                ", task=" + (task != null ? task.getId() : "null") +
                ", logTime=" + logTime +
                ", logLevel='" + logLevel + '\'' +
                ", message='" + (message != null ? message.substring(0, Math.min(message.length(), 30)) + "..." : "null") + '\'' +
                '}';
    }
} 