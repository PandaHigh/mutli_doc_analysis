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
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
        processWordDocuments(task, 5000); // 默认块大小为5000字符
    }

    /**
     * 处理Word文档
     * @param task 分析任务
     * @param chunkSize 文本块大小（字符数）
     */
    public void processWordDocuments(AnalysisTask task, int chunkSize) {
        task.setChunkSize(chunkSize);
        
        // 创建一个临时文件来存储所有文档的拼接内容
        StringBuilder allContent = new StringBuilder();
        int currentPosition = 0;
        List<FilePosition> filePositions = new ArrayList<>();
        
        // 首先读取所有文档内容并拼接
        for (String filePath : task.getWordFilePaths()) {
            try {
                File file = new File(filePath);
                if (!file.exists()) {
                    throw new RuntimeException("文件不存在: " + filePath);
                }

                // 直接读取文档内容
                try (FileInputStream fis = new FileInputStream(file);
                     XWPFDocument document = new XWPFDocument(fis)) {
                    
                    // 记录文件位置信息
                    FilePosition filePosition = new FilePosition();
                    filePosition.fileName = file.getName();
                    filePosition.startPosition = currentPosition;
                    
                    // 读取所有段落并拼接
                    for (XWPFParagraph paragraph : document.getParagraphs()) {
                        String text = paragraph.getText().trim();
                        if (!text.isEmpty()) {
                            allContent.append(text).append("\n");
                            currentPosition += text.length() + 1;
                        }
                    }
                    
                    filePosition.endPosition = currentPosition - 1;
                    filePositions.add(filePosition);
                }
                
            } catch (Exception e) {
                logger.error("处理Word文档失败: " + filePath, e);
                throw new RuntimeException("处理Word文档失败: " + e.getMessage(), e);
            }
        }
        
        // 创建临时文件存储拼接后的内容
        File tempFile = null;
        try {
            tempFile = File.createTempFile("combined_", ".docx");
            try (FileOutputStream fos = new FileOutputStream(tempFile);
                 XWPFDocument document = new XWPFDocument()) {
                
                // 将拼接的内容写入临时文档
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setText(allContent.toString());
                
                document.write(fos);
            }
            
            // 使用processDocument方法对临时文件进行分块
            List<WordChunk> chunks = WordProcessor.processDocument(tempFile, chunkSize);
            
            // 保存分块结果
            for (int i = 0; i < chunks.size(); i++) {
                WordChunk chunk = chunks.get(i);
                String chunkContent = chunk.getContent();
                
                // 确定这个块属于哪个源文件
                String sourceFile = determineSourceFile(filePositions, chunk.getStartPosition(), chunk.getEndPosition());
                
                // 更新块信息并保存
                chunk.setTask(task);
                chunk.setSourceFile(sourceFile);
                chunk.setChunkIndex(i);
                wordChunkRepository.save(chunk);
            }
            
        } catch (Exception e) {
            logger.error("处理拼接文档失败", e);
            throw new RuntimeException("处理拼接文档失败: " + e.getMessage(), e);
        } finally {
            // 删除临时文件
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
    
    // 辅助类：记录文件位置信息
    private static class FilePosition {
        String fileName;
        int startPosition;
        int endPosition;
    }
    
    // 确定文本块属于哪个源文件
    private String determineSourceFile(List<FilePosition> filePositions, int start, int end) {
        for (FilePosition pos : filePositions) {
            if (start >= pos.startPosition && end <= pos.endPosition) {
                return pos.fileName;
            }
        }
        // 如果找不到完全匹配的文件，返回包含最多内容的文件
        int maxOverlap = 0;
        String bestMatch = null;
        for (FilePosition pos : filePositions) {
            int overlap = Math.min(end, pos.endPosition) - Math.max(start, pos.startPosition);
            if (overlap > maxOverlap) {
                maxOverlap = overlap;
                bestMatch = pos.fileName;
            }
        }
        return bestMatch;
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