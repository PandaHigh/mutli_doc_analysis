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
import com.example.multidoc.model.WordChunk;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.io.FileInputStream;
import java.lang.StringBuilder;

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

    public WordProcessor() {
        this.documentParser = new ApachePoiDocumentParser();
    }

    public List<WordChunkInfo> chunkWordDocument(String filePath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(Paths.get(filePath))) {
            Document document = documentParser.parse(inputStream);
            List<TextSegment> segments = DocumentSplitters.recursive(maxChunkSize, overlapSize)
                .split(document);
            
            List<WordChunkInfo> chunks = new ArrayList<>();
            int chunkIndex = 0;

            for (TextSegment segment : segments) {
                if (segment.text().trim().isEmpty()) {
                    continue;  // Skip empty segments
                }
                
                WordChunkInfo chunk = new WordChunkInfo();
                chunk.setChunkIndex(chunkIndex++);
                chunk.setContent(segment.text());
                
                Metadata metadata = segment.metadata();
                String overlap = metadata.get("overlap");
                String startPosition = metadata.get("start_position");
                String endPosition = metadata.get("end_position");
                
                chunk.setOverlap(overlap != null ? overlap : "0");
                chunk.setStartPosition(startPosition != null ? startPosition : String.valueOf(0));
                chunk.setEndPosition(endPosition != null ? endPosition : String.valueOf(segment.text().length()));
                
                chunks.add(chunk);
            }

            return chunks;
        }
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

    private int roughTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.split("\\s+").length;
    }

    @Data
    public static class WordChunkInfo {
        private int chunkIndex;
        private String content;
        private String overlap;
        private String startPosition;
        private String endPosition;

        public WordChunkInfo() {
            // Default constructor
        }

        public WordChunkInfo(int chunkIndex, String content, String overlap, String startPosition, String endPosition) {
            this.chunkIndex = chunkIndex;
            this.content = content;
            this.overlap = overlap;
            this.startPosition = startPosition;
            this.endPosition = endPosition;
        }

        public int getChunkIndex() {
            return chunkIndex;
        }

        public void setChunkIndex(int chunkIndex) {
            this.chunkIndex = chunkIndex;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getOverlap() {
            return overlap;
        }

        public void setOverlap(String overlap) {
            this.overlap = overlap;
        }

        public String getStartPosition() {
            return startPosition;
        }

        public void setStartPosition(String startPosition) {
            this.startPosition = startPosition;
        }

        public String getEndPosition() {
            return endPosition;
        }

        public void setEndPosition(String endPosition) {
            this.endPosition = endPosition;
        }
    }

    /**
     * 处理Word文档并分块，确保在段落边界进行分块
     * @param file Word文档文件
     * @param chunkSize 文本块大小（字符数）
     * @return 文档块列表
     */
    public static List<WordChunk> processDocument(File file, int chunkSize) {
        List<WordChunk> chunks = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {
            
            StringBuilder currentChunk = new StringBuilder();
            int currentSize = 0;
            int chunkIndex = 0;
            int startPosition = 0;
            int totalPosition = 0;

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText().trim();
                if (text.isEmpty()) continue;

                // 如果当前段落加上当前块的大小超过chunkSize，且当前块不为空
                if (currentSize + text.length() > chunkSize && currentSize > 0) {
                    // 保存当前块
                    WordChunk chunk = new WordChunk();
                    chunk.setChunkIndex(chunkIndex++);
                    chunk.setContent(currentChunk.toString());
                    chunk.setStartPosition(startPosition);
                    chunk.setEndPosition(startPosition + currentSize - 1);
                    chunks.add(chunk);
                    
                    // 重置当前块
                    startPosition = totalPosition;
                    currentChunk = new StringBuilder();
                    currentSize = 0;
                }

                // 如果段落本身超过块大小，需要按句子分割
                if (text.length() > chunkSize) {
                    String[] sentences = text.split("(?<=[.!?。！？])");
                    for (String sentence : sentences) {
                        if (currentSize + sentence.length() > chunkSize && currentSize > 0) {
                            // 保存当前块
                            WordChunk chunk = new WordChunk();
                            chunk.setChunkIndex(chunkIndex++);
                            chunk.setContent(currentChunk.toString());
                            chunk.setStartPosition(startPosition);
                            chunk.setEndPosition(startPosition + currentSize - 1);
                            chunks.add(chunk);
                            
                            // 重置当前块
                            startPosition = totalPosition;
                            currentChunk = new StringBuilder();
                            currentSize = 0;
                        }
                        
                        currentChunk.append(sentence).append("\n");
                        currentSize += sentence.length() + 1;
                        totalPosition += sentence.length() + 1;
                    }
                } else {
                    currentChunk.append(text).append("\n");
                    currentSize += text.length() + 1;
                    totalPosition += text.length() + 1;
                }
            }

            // 保存最后一个块
            if (currentSize > 0) {
                WordChunk chunk = new WordChunk();
                chunk.setChunkIndex(chunkIndex);
                chunk.setContent(currentChunk.toString());
                chunk.setStartPosition(startPosition);
                chunk.setEndPosition(startPosition + currentSize - 1);
                chunks.add(chunk);
            }

            logger.info("成功处理Word文档: {}, 共生成{}个块", file.getName(), chunks.size());
            return chunks;

        } catch (Exception e) {
            logger.error("处理Word文档失败: " + file.getPath(), e);
            throw new RuntimeException("处理Word文档失败: " + e.getMessage(), e);
        }
    }
} 