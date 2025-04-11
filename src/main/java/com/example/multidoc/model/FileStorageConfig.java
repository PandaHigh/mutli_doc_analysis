package com.example.multidoc.model;

import jakarta.persistence.*;

@Entity
public class FileStorageConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String wordUploadPath;
    private String excelUploadPath;
    private Long maxFileSize;
    private String allowedWordExtensions;
    private String allowedExcelExtensions;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private Integer maxCategories;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getWordUploadPath() {
        return wordUploadPath;
    }

    public void setWordUploadPath(String wordUploadPath) {
        this.wordUploadPath = wordUploadPath;
    }

    public String getExcelUploadPath() {
        return excelUploadPath;
    }

    public void setExcelUploadPath(String excelUploadPath) {
        this.excelUploadPath = excelUploadPath;
    }

    public Long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(Long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public String getAllowedWordExtensions() {
        return allowedWordExtensions;
    }

    public void setAllowedWordExtensions(String allowedWordExtensions) {
        this.allowedWordExtensions = allowedWordExtensions;
    }

    public String getAllowedExcelExtensions() {
        return allowedExcelExtensions;
    }

    public void setAllowedExcelExtensions(String allowedExcelExtensions) {
        this.allowedExcelExtensions = allowedExcelExtensions;
    }

    public Integer getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(Integer chunkSize) {
        this.chunkSize = chunkSize;
    }

    public Integer getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(Integer chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    public Integer getMaxCategories() {
        return maxCategories;
    }

    public void setMaxCategories(Integer maxCategories) {
        this.maxCategories = maxCategories;
    }
} 