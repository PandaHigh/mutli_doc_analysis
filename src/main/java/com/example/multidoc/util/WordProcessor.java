package com.example.multidoc.util;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.io.FileInputStream;
import java.util.regex.Pattern;

@Component
public class WordProcessor {

    private static final Logger logger = LoggerFactory.getLogger(WordProcessor.class);

    @Value("${app.upload.word-dir}")
    private String uploadDir;

    @Value("${app.chunk.max-size}")
    private int maxChunkSize;

    @Value("${app.chunk.overlap-size}")
    private int overlapSize;

    private static final String[] ALLOWED_EXTENSIONS = {".docx", ".doc"};
    private final DocumentParser documentParser;
    
    // 定义句子结束标记的正则表达式
    private static final Pattern SENTENCE_END_PATTERN = Pattern.compile("(?<=[.!?。！？])\\s*(?=\\S)");
    
    // 定义段落分隔符
    private static final String PARAGRAPH_SEPARATOR = "\n\n";
    
    // 定义最小句子长度
    private static final int MIN_SENTENCE_LENGTH = 50;
    
    // 定义上下文窗口大小
    private static final int CONTEXT_WINDOW_SIZE = 200;

    public WordProcessor() {
        this.documentParser = new ApachePoiDocumentParser();
    }

    public String processWordFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !isValidWordFile(originalFilename)) {
            throw new IllegalArgumentException("Invalid file type. Only .doc and .docx files are allowed");
        }

        // Create upload directory if it doesn't exist
        File uploadDirFile = new File(uploadDir);
        if (!uploadDirFile.exists()) {
            if (!uploadDirFile.mkdirs()) {
                throw new IOException("Failed to create upload directory");
            }
        }

        // Generate unique filename
        String fileExtension = originalFilename.substring(originalFilename.lastIndexOf('.'));
        String newFilename = UUID.randomUUID() + fileExtension;
        Path targetPath = Paths.get(uploadDir, newFilename);

        // Save the file
        Files.copy(file.getInputStream(), targetPath);
        return targetPath.toString();
    }

    private boolean isValidWordFile(String filename) {
        String lowerFilename = filename.toLowerCase();
        for (String extension : ALLOWED_EXTENSIONS) {
            if (lowerFilename.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 处理Word文档并按句子分割，保留上下文信息
     * @param file Word文档文件
     * @return 句子列表
     */
    public List<WordSentenceInfo> processSentences(File file) {
        List<WordSentenceInfo> sentences = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {
            
            int sentenceIndex = 0;
            int totalPosition = 0;
            StringBuilder fullText = new StringBuilder();
            
            // 首先构建完整文本，保留段落结构
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText().trim();
                if (text.isEmpty()) continue;
                
                fullText.append(text).append(PARAGRAPH_SEPARATOR);
            }
            
            // 使用 langchain4j 的文档分割器进行智能分割
            Document doc = Document.from(fullText.toString(), Metadata.from("source", file.getPath()));
            List<TextSegment> segments = DocumentSplitters.recursive(maxChunkSize, overlapSize).split(doc);
            
            // 处理每个分割后的段落
            for (TextSegment segment : segments) {
                String segmentText = segment.text();
                int segmentStart = fullText.indexOf(segmentText);
                
                // 按句子分割段落
                String[] sentenceTexts = SENTENCE_END_PATTERN.split(segmentText);
                int currentPosition = 0;
                StringBuilder currentSentence = new StringBuilder();
                
                for (String sentenceText : sentenceTexts) {
                    if (sentenceText.trim().isEmpty()) continue;
                    
                    // 如果当前句子太短，尝试与下一个句子合并
                    if (currentSentence.length() < MIN_SENTENCE_LENGTH) {
                        if (currentSentence.length() > 0) {
                            currentSentence.append(" ");
                        }
                        currentSentence.append(sentenceText.trim());
                        continue;
                    }
                    
                    // 处理当前累积的句子
                    if (currentSentence.length() > 0) {
                        String finalSentence = currentSentence.toString();
                        int sentenceStart = segmentText.indexOf(finalSentence, currentPosition);
                        int startPosition = segmentStart + sentenceStart;
                        int length = finalSentence.length();
                        
                        WordSentenceInfo sentence = new WordSentenceInfo();
                        sentence.setSentenceIndex(sentenceIndex++);
                        sentence.setContent(finalSentence);
                        sentence.setStartPosition(startPosition);
                        sentence.setEndPosition(startPosition + length - 1);
                        
                        // 添加上下文信息
                        String context = extractContext(fullText.toString(), startPosition, length);
                        sentence.setContext(context);
                        
                        sentences.add(sentence);
                        
                        // 更新当前位置到这个句子之后
                        currentPosition = sentenceStart + length;
                        currentSentence = new StringBuilder();
                    }
                    
                    // 开始新的句子
                    currentSentence.append(sentenceText.trim());
                }
                
                // 处理最后一个累积的句子
                if (currentSentence.length() > 0) {
                    String finalSentence = currentSentence.toString();
                    int sentenceStart = segmentText.indexOf(finalSentence, currentPosition);
                    int startPosition = segmentStart + sentenceStart;
                    int length = finalSentence.length();
                    
                    WordSentenceInfo sentence = new WordSentenceInfo();
                    sentence.setSentenceIndex(sentenceIndex++);
                    sentence.setContent(finalSentence);
                    sentence.setStartPosition(startPosition);
                    sentence.setEndPosition(startPosition + length - 1);
                    
                    // 添加上下文信息
                    String context = extractContext(fullText.toString(), startPosition, length);
                    sentence.setContext(context);
                    
                    sentences.add(sentence);
                }
            }
            
            return sentences;
            
        } catch (IOException e) {
            logger.error("处理Word文档失败: " + file.getPath(), e);
            throw new RuntimeException("处理Word文档失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 提取句子的上下文信息
     * @param fullText 完整文本
     * @param startPosition 句子开始位置
     * @param length 句子长度
     * @return 上下文信息
     */
    private String extractContext(String fullText, int startPosition, int length) {
        // 计算上下文范围，确保不会超出文本边界
        int contextStart = Math.max(0, startPosition - CONTEXT_WINDOW_SIZE);
        int contextEnd = Math.min(fullText.length(), startPosition + length + CONTEXT_WINDOW_SIZE);
        
        // 确保开始位置不会大于结束位置
        if (contextStart >= contextEnd) {
            contextStart = Math.max(0, contextEnd - length - CONTEXT_WINDOW_SIZE);
        }
        
        // 提取上下文
        String context = fullText.substring(contextStart, contextEnd);
        
        // 如果上下文被截断，添加标记
        if (contextStart > 0) {
            context = "..." + context;
        }
        if (contextEnd < fullText.length()) {
            context = context + "...";
        }
        
        return context;
    }

    @Data
    public static class WordSentenceInfo {
        private int sentenceIndex;
        private String content;
        private int startPosition;
        private int endPosition;
        private String context; // 新增：上下文信息

        public WordSentenceInfo() {
            // Default constructor
        }

        public int getSentenceIndex() {
            return sentenceIndex;
        }

        public void setSentenceIndex(int sentenceIndex) {
            this.sentenceIndex = sentenceIndex;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public int getStartPosition() {
            return startPosition;
        }

        public void setStartPosition(int startPosition) {
            this.startPosition = startPosition;
        }

        public int getEndPosition() {
            return endPosition;
        }

        public void setEndPosition(int endPosition) {
            this.endPosition = endPosition;
        }
        
        public String getContext() {
            return context;
        }
        
        public void setContext(String context) {
            this.context = context;
        }
    }
} 