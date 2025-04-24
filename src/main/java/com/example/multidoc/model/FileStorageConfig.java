package com.example.multidoc.model;

import jakarta.persistence.*;

@Entity
@Table(name = "file_storage_configs")
public class FileStorageConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "config_key", nullable = false, unique = true)
    private String configKey;
    
    @Column(name = "config_value", nullable = false)
    private String configValue;
    
    public FileStorageConfig() {
    }
    
    public FileStorageConfig(String configKey, String configValue) {
        this.configKey = configKey;
        this.configValue = configValue;
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getConfigKey() {
        return configKey;
    }
    
    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }
    
    public String getConfigValue() {
        return configValue;
    }
    
    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }
    
    @Override
    public String toString() {
        return "FileStorageConfig{" +
                "id=" + id +
                ", configKey='" + configKey + '\'' +
                ", configValue='" + configValue + '\'' +
                '}';
    }
} 