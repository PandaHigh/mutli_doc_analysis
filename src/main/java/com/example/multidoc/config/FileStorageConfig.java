package com.example.multidoc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Configuration
public class FileStorageConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(FileStorageConfig.class);
    
    @Value("${app.file-storage.word-upload-path}")
    private String wordUploadPath;
    
    @Value("${app.file-storage.excel-upload-path}")
    private String excelUploadPath;
    
    /**
     * 初始化文件存储目录
     */
    @Bean
    public FileStorageConfig initStorageFolders() {
        try {
            createDirectoryIfNotExists(wordUploadPath);
            createDirectoryIfNotExists(excelUploadPath);
            logger.info("文件存储目录初始化成功");
            return this;
        } catch (IOException e) {
            logger.error("文件存储目录初始化失败", e);
            throw new RuntimeException("无法创建文件存储目录", e);
        }
    }
    
    /**
     * 如果目录不存在则创建
     */
    private void createDirectoryIfNotExists(String path) throws IOException {
        if (StringUtils.hasText(path)) {
            Files.createDirectories(Paths.get(path));
        }
    }
    
    /**
     * 获取Word文档上传路径
     */
    public String getWordUploadPath() {
        return normalizeDirectoryPath(wordUploadPath);
    }
    
    /**
     * 获取Excel文档上传路径
     */
    public String getExcelUploadPath() {
        return normalizeDirectoryPath(excelUploadPath);
    }
    
    /**
     * 规范化目录路径，确保以路径分隔符结尾
     */
    private String normalizeDirectoryPath(String path) {
        if (path == null) {
            return "";
        }
        
        String normalizedPath = path;
        if (!normalizedPath.endsWith(File.separator)) {
            normalizedPath += File.separator;
        }
        
        return normalizedPath;
    }
} 