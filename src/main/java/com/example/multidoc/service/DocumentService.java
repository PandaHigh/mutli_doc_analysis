package com.example.multidoc.service;

import com.example.multidoc.config.FileStorageConfig;
import com.example.multidoc.model.AnalysisTask;
import com.example.multidoc.model.WordSentence;
import com.example.multidoc.repository.WordSentenceRepository;
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
import java.util.List;
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
    private WordSentenceRepository wordSentenceRepository;

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
     * 处理Word文档 - 以句子为单位
     */
    public void processWordDocuments(AnalysisTask task) throws IOException {
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
            
            // 使用 processSentences 方法对临时文件进行句子拆分
            List<WordProcessor.WordSentenceInfo> sentences = wordProcessor.processSentences(tempFile);
            
            // 保存句子结果
            for (int i = 0; i < sentences.size(); i++) {
                WordProcessor.WordSentenceInfo sentenceInfo = sentences.get(i);
                String sentenceContent = sentenceInfo.getContent();
                
                // 确定这个句子属于哪个源文件
                String sourceFile = determineSourceFile(filePositions, sentenceInfo.getStartPosition(), sentenceInfo.getEndPosition());
                
                // 创建并保存 WordSentence 实体
                WordSentence sentence = new WordSentence();
                sentence.setTask(task);
                sentence.setSentenceIndex(sentenceInfo.getSentenceIndex());
                sentence.setContent(sentenceContent);
                sentence.setSourceFile(sourceFile);
                sentence.setStartPosition(sentenceInfo.getStartPosition());
                sentence.setEndPosition(sentenceInfo.getEndPosition());
                
                wordSentenceRepository.save(sentence);
            }
            
            logger.info("成功处理和保存 {} 个句子", sentences.size());
            
        } finally {
            if (tempFile != null && tempFile.exists()) {
                try {
                    Files.delete(tempFile.toPath());
                } catch (IOException e) {
                    logger.warn("删除临时文件失败: " + tempFile.getPath(), e);
                }
            }
        }
    }

    /**
     * 用于跟踪文件位置的内部类
     */
    private static class FilePosition {
        String fileName;
        int startPosition;
        int endPosition;
    }

    /**
     * 根据位置确定句子属于哪个源文件
     */
    private String determineSourceFile(List<FilePosition> filePositions, int start, int end) {
        for (FilePosition pos : filePositions) {
            if (start >= pos.startPosition && end <= pos.endPosition) {
                return pos.fileName;
            }
        }
        return "unknown";
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
     * 处理Excel文件并提取字段信息
     * @param filePath Excel文件路径
     * @return 字段信息列表
     */
    public List<ExcelProcessor.ElementInfo> processExcelFile(String filePath) {
        try {
            logger.info("开始处理Excel文件: {}", filePath);
            List<ExcelProcessor.ElementInfo> fields = excelProcessor.extractFields(filePath);
            logger.info("成功从Excel文件中提取 {} 个字段", fields.size());
            return fields;
        } catch (Exception e) {
            logger.error("处理Excel文件失败: " + filePath, e);
            throw new RuntimeException("处理Excel文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从Word文档提取前2000字
     * @param filePath 文档路径
     * @return 文档前2000字内容
     */
    public String extractDocumentPrefix(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new RuntimeException("文件不存在: " + filePath);
        }
        
        StringBuilder content = new StringBuilder();
        final int MAX_LENGTH = 2000;
        
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {
            
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText().trim();
                if (!text.isEmpty()) {
                    content.append(text).append("\n");
                    
                    // 如果已经超过2000字，则截断
                    if (content.length() >= MAX_LENGTH) {
                        content.setLength(MAX_LENGTH);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("提取文档前缀失败: " + filePath, e);
            throw new IOException("提取文档前缀失败: " + e.getMessage(), e);
        }
        
        return content.toString();
    }
} 