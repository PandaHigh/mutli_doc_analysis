package com.example.multidoc.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "field_sentence_relation")
public class FieldSentenceRelation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "field_id", nullable = false)
    private Long fieldId;
    
    @Column(name = "sentence_id", nullable = false)
    private Long sentenceId;
    
    @Column(name = "field_name", nullable = false)
    private String fieldName;
    
    @Column(name = "field_type")
    private String fieldType;
    
    @Column(name = "field_description", columnDefinition = "TEXT")
    private String fieldDescription;
    
    @Column(name = "sentence_content", columnDefinition = "TEXT", nullable = false)
    private String sentenceContent;
    
    @Column(name = "source_file")
    private String sourceFile;
    
    @Column(name = "relevance_score")
    private Float relevanceScore = 0.0f;
    
    @Column(name = "created_time")
    private LocalDateTime createdTime = LocalDateTime.now();

    // Default constructor
    public FieldSentenceRelation() {
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFieldId() {
        return fieldId;
    }

    public void setFieldId(Long fieldId) {
        this.fieldId = fieldId;
    }

    public Long getSentenceId() {
        return sentenceId;
    }

    public void setSentenceId(Long sentenceId) {
        this.sentenceId = sentenceId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public String getFieldDescription() {
        return fieldDescription;
    }

    public void setFieldDescription(String fieldDescription) {
        this.fieldDescription = fieldDescription;
    }

    public String getSentenceContent() {
        return sentenceContent;
    }

    public void setSentenceContent(String sentenceContent) {
        this.sentenceContent = sentenceContent;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public Float getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(Float relevanceScore) {
        this.relevanceScore = relevanceScore;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }
} 