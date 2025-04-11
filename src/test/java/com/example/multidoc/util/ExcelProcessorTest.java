package com.example.multidoc.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExcelProcessorTest {

    private ExcelProcessor excelProcessor;
    private String testFilePath;
    
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        excelProcessor = new ExcelProcessor();
        
        // 创建测试文件路径
        testFilePath = tempDir.resolve("test.xlsx").toString();
        
        // 创建测试Excel文件
        createTestExcelFile(testFilePath);
    }

    @Test
    void testConvertExcelToMarkdown() {
        String markdown = excelProcessor.convertExcelToMarkdown(testFilePath);
        assertNotNull(markdown);
        assertFalse(markdown.isEmpty());
        assertTrue(markdown.contains("Test Sheet"));
        assertTrue(markdown.contains("Name"));
        assertTrue(markdown.contains("Age"));
    }

    @Test
    void testExtractFieldHeaders() {
        List<ExcelProcessor.ElementInfo> elements = excelProcessor.extractFields(testFilePath);
        
        assertNotNull(elements);
        assertFalse(elements.isEmpty());
        
        // 验证标题行
        ExcelProcessor.ElementInfo nameHeader = elements.stream()
                .filter(e -> e.getRowIndex() == 0 && e.getColumnIndex() == 0)
                .findFirst()
                .orElse(null);
        assertNotNull(nameHeader);
        assertEquals("Name", nameHeader.getValue());
        assertEquals("Test Sheet", nameHeader.getSheetName());
        
        ExcelProcessor.ElementInfo ageHeader = elements.stream()
                .filter(e -> e.getRowIndex() == 0 && e.getColumnIndex() == 1)
                .findFirst()
                .orElse(null);
        assertNotNull(ageHeader);
        assertEquals("Age", ageHeader.getValue());
        assertEquals("Test Sheet", ageHeader.getSheetName());
        
        // 验证描述行
        ExcelProcessor.ElementInfo nameDesc = elements.stream()
                .filter(e -> e.getRowIndex() == 1 && e.getColumnIndex() == 0)
                .findFirst()
                .orElse(null);
        assertNotNull(nameDesc);
        assertEquals("User's full name", nameDesc.getValue());
        
        ExcelProcessor.ElementInfo ageDesc = elements.stream()
                .filter(e -> e.getRowIndex() == 1 && e.getColumnIndex() == 1)
                .findFirst()
                .orElse(null);
        assertNotNull(ageDesc);
        assertEquals("User's age in years", ageDesc.getValue());
        
        // 验证数据行
        ExcelProcessor.ElementInfo nameData = elements.stream()
                .filter(e -> e.getRowIndex() == 2 && e.getColumnIndex() == 0)
                .findFirst()
                .orElse(null);
        assertNotNull(nameData);
        assertEquals("John Doe", nameData.getValue());
        
        ExcelProcessor.ElementInfo ageData = elements.stream()
                .filter(e -> e.getRowIndex() == 2 && e.getColumnIndex() == 1)
                .findFirst()
                .orElse(null);
        assertNotNull(ageData);
        assertEquals("30.0", ageData.getValue());
    }

    private void createTestExcelFile(String filePath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Test Sheet");
            
            // 创建标题行
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Name");
            headerRow.createCell(1).setCellValue("Age");
            
            // 创建描述行
            Row descRow = sheet.createRow(1);
            descRow.createCell(0).setCellValue("User's full name");
            descRow.createCell(1).setCellValue("User's age in years");
            
            // 创建数据行
            Row dataRow = sheet.createRow(2);
            dataRow.createCell(0).setCellValue("John Doe");
            dataRow.createCell(1).setCellValue(30);
            
            // 保存文件
            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                workbook.write(fileOut);
            }
        }
    }
} 