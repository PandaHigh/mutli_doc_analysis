package com.example.multidoc.service;

import com.example.multidoc.config.FileStorageConfig;
import com.example.multidoc.model.AnalysisTask;
import com.example.multidoc.model.WordChunk;
import com.example.multidoc.repository.WordChunkRepository;
import com.example.multidoc.util.ExcelProcessor;
import com.example.multidoc.util.WordProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);

    @Autowired
    private FileStorageConfig fileStorageConfig;

    @Autowired
    private WordProcessor wordProcessor;

    @Autowired
    private ExcelProcessor excelProcessor;

    @Autowired
    private WordChunkRepository wordChunkRepository;

    /**
     * 保存上传的Word文档
     * @param file 上传的文件
     * @return 保存后的文件路径
     */
    public String saveWordDocument(MultipartFile file) throws IOException {
        return saveFile(file, fileStorageConfig.getWordUploadPath());
    }

    /**
     * 保存上传的Excel文档
     * @param file 上传的文件
     * @return 保存后的文件路径
     */
    public String saveExcelDocument(MultipartFile file) throws IOException {
        return saveFile(file, fileStorageConfig.getExcelUploadPath());
    }

    /**
     * 保存文件到指定目录
     */
    private String saveFile(MultipartFile file, String targetDir) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IOException("文件名不能为空");
        }

        // 生成唯一文件名
        String fileExtension = getFileExtension(originalFilename);
        String uniqueFilename = UUID.randomUUID().toString() + fileExtension;

        Path targetPath = Paths.get(targetDir, uniqueFilename);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        return targetPath.toString();
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? "" : filename.substring(dotIndex);
    }

    /**
     * 处理Word文档
     */
    public void processWordDocuments(AnalysisTask task) {
        processWordDocuments(task, 1000); // 默认块大小为1000字符
    }

    /**
     * 处理Word文档
     * @param task 分析任务
     * @param chunkSize 文本块大小（字符数）
     */
    public void processWordDocuments(AnalysisTask task, int chunkSize) {
        task.setChunkSize(chunkSize);
        
        for (String filePath : task.getWordFilePaths()) {
            try {
                File file = new File(filePath);
                if (!file.exists()) {
                    throw new RuntimeException("文件不存在: " + filePath);
                }

                // 使用WordProcessor处理文档
                List<WordChunk> chunks = WordProcessor.processDocument(file, chunkSize);
                
                // 保存文档块
                for (WordChunk chunk : chunks) {
                    chunk.setTask(task);
                    chunk.setSourceFile(file.getName()); // 设置源文件名
                    wordChunkRepository.save(chunk);
                }
            } catch (Exception e) {
                logger.error("处理Word文档失败: " + filePath, e);
                throw new RuntimeException("处理Word文档失败: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 将Excel文档转换为Markdown
     * @param filePath Excel文件路径
     * @return Markdown文本
     */
    public String convertExcelToMarkdown(String filePath) {
        return excelProcessor.convertExcelToMarkdown(filePath);
    }

    /**
     * 提取Excel字段列表
     * @param filePath Excel文件路径
     * @return 字段信息列表
     */
    public List<ExcelProcessor.ElementInfo> extractExcelFields(String filePath) {
        return excelProcessor.extractFields(filePath);
    }

    /**
     * 获取指定任务的所有Word块内容
     * @param task 分析任务
     * @return Word块内容列表
     */
    public List<String> getWordChunksContent(AnalysisTask task) {
        List<String> contents = new ArrayList<>();
        
        // 获取当前任务的所有文档源文件名
        Set<String> sourceFiles = new HashSet<>();
        for (String filePath : task.getWordFilePaths()) {
            File file = new File(filePath);
            sourceFiles.add(file.getName());
        }
        
        // 只获取当前任务上传的文档块
        for (String sourceFile : sourceFiles) {
            List<WordChunk> chunks = wordChunkRepository.findByTaskAndSourceFileOrderByChunkIndex(task, sourceFile);
            for (WordChunk chunk : chunks) {
                contents.add(chunk.getContent());
            }
        }
        
        return contents;
    }
} 