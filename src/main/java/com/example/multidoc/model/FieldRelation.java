package com.example.multidoc.model;

import jakarta.persistence.*;

@Entity
public class FieldRelation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "task_id")
    private AnalysisTask task;
    
    @ManyToOne
    @JoinColumn(name = "source_field_id")
    private ExcelField sourceField;
    
    @ManyToOne
    @JoinColumn(name = "target_field_id")
    private ExcelField targetField;
    
    // 关联强度，范围0-1
    @Column(name = "relation_score")
    private Double relationScore;
    
    @Column(name = "relation_description", columnDefinition = "TEXT")
    private String relationDescription;

    // Default constructor
    public FieldRelation() {
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

    public ExcelField getSourceField() {
        return sourceField;
    }

    public void setSourceField(ExcelField sourceField) {
        this.sourceField = sourceField;
    }

    public ExcelField getTargetField() {
        return targetField;
    }

    public void setTargetField(ExcelField targetField) {
        this.targetField = targetField;
    }

    public Double getRelationScore() {
        return relationScore;
    }

    public void setRelationScore(Double relationScore) {
        this.relationScore = relationScore;
    }
    
    public String getRelationDescription() {
        return relationDescription;
    }

    public void setRelationDescription(String relationDescription) {
        this.relationDescription = relationDescription;
    }
} 