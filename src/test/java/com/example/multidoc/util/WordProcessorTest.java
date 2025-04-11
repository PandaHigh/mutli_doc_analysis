package com.example.multidoc.util;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WordProcessorTest {

    private WordProcessor wordProcessor;
    private Path uploadDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        wordProcessor = new WordProcessor();
        uploadDir = tempDir.resolve("word-uploads");
        
        // 设置配置参数
        try {
            Files.createDirectories(uploadDir);
            
            var uploadDirField = WordProcessor.class.getDeclaredField("uploadDir");
            uploadDirField.setAccessible(true);
            uploadDirField.set(wordProcessor, uploadDir.toString());
            
            var maxChunkSizeField = WordProcessor.class.getDeclaredField("maxChunkSize");
            maxChunkSizeField.setAccessible(true);
            maxChunkSizeField.setInt(wordProcessor, 1000);
            
            var overlapSizeField = WordProcessor.class.getDeclaredField("overlapSize");
            overlapSizeField.setAccessible(true);
            overlapSizeField.setInt(wordProcessor, 200);
        } catch (Exception e) {
            fail("Failed to setup test: " + e.getMessage());
        }
    }

    @Test
    void testProcessWordFile() throws IOException {
        // 创建测试文档
        byte[] docContent = createWordDocument(
            "这是一个测试文档的内容。",
            "包含多个段落。",
            "用于测试文档处理功能。"
        );

        MultipartFile mockFile = new MockMultipartFile(
            "test.docx",
            "test.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            docContent
        );

        // 测试文件处理
        String filePath = wordProcessor.processWordFile(mockFile);
        assertNotNull(filePath, "处理后的文件路径不应为空");
        assertTrue(Files.exists(Path.of(filePath)), "文件应该被保存到磁盘");
    }

    @Test
    void testProcessEmptyFile() {
        MultipartFile emptyFile = new MockMultipartFile(
            "empty.docx",
            "empty.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            new byte[0]
        );

        assertThrows(IllegalArgumentException.class,
            () -> wordProcessor.processWordFile(emptyFile),
            "处理空文件应该抛出异常"
        );
    }

    @Test
    void testProcessInvalidFileType() {
        MultipartFile invalidFile = new MockMultipartFile(
            "test.txt",
            "test.txt",
            "text/plain",
            "测试内容".getBytes()
        );

        assertThrows(IllegalArgumentException.class,
            () -> wordProcessor.processWordFile(invalidFile),
            "处理非Word文件应该抛出异常"
        );
    }

    @Test
    void testChunkWordDocument() throws IOException {
        // 创建测试文档
        byte[] docContent = createWordDocument(
            "第一段落测试内容",
            "第二段落测试内容",
            "第三段落测试内容，这是一个较长的段落，包含更多的文本内容用于测试分块功能。",
            "第四段落测试内容。"
        );
            
        Path testFile = uploadDir.resolve("test_document.docx");
        Files.write(testFile, docContent);

        // 测试文档分块
        List<WordProcessor.WordChunkInfo> chunks = wordProcessor.chunkWordDocument(testFile.toString());
        
        assertNotNull(chunks, "分块结果不应为空");
        assertFalse(chunks.isEmpty(), "应该至少有一个文档块");
        
        // 验证块的属性
        WordProcessor.WordChunkInfo firstChunk = chunks.get(0);
        assertNotNull(firstChunk.getContent(), "块内容不应为空");
        assertNotNull(firstChunk.getStartPosition(), "起始位置不应为空");
        assertNotNull(firstChunk.getEndPosition(), "结束位置不应为空");
        
        // 验证块的内容
        assertTrue(firstChunk.getContent().contains("测试内容"), "块应包含原始文本内容");
    }

    private byte[] createWordDocument(String... paragraphs) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            for (String content : paragraphs) {
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setText(content);
            }
            
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        }
    }
} 