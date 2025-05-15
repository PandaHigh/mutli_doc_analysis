package com.example.multidoc.test;

import com.example.multidoc.model.AnalysisTask;
import com.example.multidoc.model.FieldRule;
import com.example.multidoc.model.RuleValidationResult;
import com.example.multidoc.service.AnalysisService;
import com.example.multidoc.service.RuleValidationService;
import com.example.multidoc.util.ExcelProcessor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class TestExcelToMd2 implements CommandLineRunner {

    private final ExcelProcessor excelProcessor;
    private final RuleValidationService validationService;
    private final AnalysisService analysisService;

    public TestExcelToMd2(ExcelProcessor excelProcessor, 
                         RuleValidationService validationService,
                         AnalysisService analysisService) {
        this.excelProcessor = excelProcessor;
        this.validationService = validationService;
        this.analysisService = analysisService;
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
        Path mdPath = Paths.get("./test/excel-validate/output2.md");
        Files.createDirectories(mdPath.getParent());
        
        // 使用ExcelProcessor转换为Markdown
        String mdContent = excelProcessor.convertExcelToMarkdown(excelPath);
        
        // 写入文件
        Files.writeString(mdPath, mdContent);
        
        System.out.println("Excel转换为Markdown成功！");
        System.out.println("输出文件: " + mdPath.toAbsolutePath());
        
        // 测试规则验证功能
        try {
            // 获取测试任务ID
            String taskId = "6cc117a6-4a29-4557-a311-414ee840cc2b";
            
            // 验证任务是否存在
            AnalysisTask task = analysisService.getTaskById(taskId);
            System.out.println("找到任务: " + task.getTaskName());
            
            // 获取规则
            List<FieldRule> rules = analysisService.getFieldRules(task);
            System.out.println("找到规则数量: " + rules.size());
            
            // 创建MockMultipartFile
            File excelFile = new File(excelPath);
            FileInputStream input = new FileInputStream(excelFile);
            MultipartFile multipartFile = new MockMultipartFile(
                "excelFiles",
                excelFile.getName(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                input
            );
            
            // 调用验证服务
            CompletableFuture<RuleValidationResult> future = 
                validationService.validateRules(taskId, Arrays.asList(multipartFile));
            
            // 等待结果
            RuleValidationResult result = future.get();
            
            // 输出结果
            System.out.println("验证结果状态: " + result.getStatus());
            System.out.println("验证结果进度: " + result.getProgress() + "%");
            
            if (result.getStatus().equals("COMPLETED")) {
                System.out.println("验证结果JSON: " + result.getValidatedRules());
            } else if (result.getStatus().equals("FAILED")) {
                System.out.println("验证失败: " + result.getErrorMessage());
            }
            
        } catch (Exception e) {
            System.err.println("验证规则时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 