package com.example.multidoc.controller;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class DownloadController {

    private static final Logger logger = LoggerFactory.getLogger(DownloadController.class);

    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(@RequestParam String file) {
        try {
            // Create file resource
            Path filePath;
            if (file.contains(":") || file.startsWith("/")) {
                // Absolute path
                filePath = Paths.get(file);
            } else {
                // Relative path - resolve against application root
                filePath = Paths.get(file).toAbsolutePath();
            }
            
            File fileObj = filePath.toFile();
            
            if (!fileObj.exists() || !fileObj.isFile()) {
                logger.error("Requested file not found: {}", file);
                return ResponseEntity.notFound().build();
            }

            // Create resource from file
            Resource resource = new FileSystemResource(fileObj);
            
            // Extract filename from path for Content-Disposition header
            String filename = filePath.getFileName().toString();
            
            // Determine content type
            String contentType = "application/octet-stream"; // Default
            if (filename.endsWith(".txt")) {
                contentType = "text/plain";
            } else if (filename.endsWith(".pdf")) {
                contentType = "application/pdf";
            } else if (filename.endsWith(".json")) {
                contentType = "application/json";
            }
            
            logger.info("Downloading file: {}, content type: {}", filename, contentType);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);
                    
        } catch (Exception e) {
            logger.error("Error during file download", e);
            return ResponseEntity.internalServerError().build();
        }
    }
} 