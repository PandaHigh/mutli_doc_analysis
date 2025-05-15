package com.example.multidoc.test;

import com.example.multidoc.util.ExcelProcessor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class TestExcelToMd implements CommandLineRunner {

    private final ExcelProcessor excelProcessor;

    public TestExcelToMd(ExcelProcessor excelProcessor) {
        this.excelProcessor = excelProcessor;
    }

    @Override
    public void run(String... args) throws Exception {
        // 测试Excel文件路径
        String excelPath = "./uploads/excel/f8002717-93da-4b09-afa3-369fc569e70a.xlsx";
        
        // 确保文件存在
        if (!Files.exists(Paths.get(excelPath))) {
            System.out.println("测试文件不存在: " + excelPath);
            return;
        }
        
        // 输出目录
        Path mdPath = Paths.get("./test/excel-validate/output.md");
        Files.createDirectories(mdPath.getParent());
        
        // 使用ExcelProcessor转换为Markdown
        String mdContent = excelProcessor.convertExcelToMarkdown(excelPath);
        
        // 写入文件
        try (FileWriter writer = new FileWriter(mdPath.toFile())) {
            writer.write(mdContent);
        }
        
        System.out.println("Excel转换为Markdown成功！");
        System.out.println("输出文件: " + mdPath.toAbsolutePath());
    }
} 