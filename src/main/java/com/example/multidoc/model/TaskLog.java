package com.example.multidoc.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class TaskLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "task_id")
    private AnalysisTask task;
    
    private LocalDateTime timestamp;
    
    @Column(length = 1000)
    private String message;
    
    private String level; // INFO, WARN, ERROR
    
    public TaskLog() {
        this.timestamp = LocalDateTime.now();
    }
    
    public TaskLog(AnalysisTask task, String message, String level) {
        this();
        this.task = task;
        this.message = message;
        this.level = level;
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

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }
} 