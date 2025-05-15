package com.example.multidoc.test;

import com.example.multidoc.model.AnalysisTask;
import com.example.multidoc.model.FieldRule;
import com.example.multidoc.service.AnalysisService;
import com.example.multidoc.util.ExcelProcessor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Component
public class TestExcelToMd2 implements CommandLineRunner {

    private final ExcelProcessor excelProcessor;
    private final AnalysisService analysisService;

    public TestExcelToMd2(ExcelProcessor excelProcessor, 
                         AnalysisService analysisService) {
        this.excelProcessor = excelProcessor;
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
        
        // 测试任务和规则信息
        try {
            // 获取测试任务ID
            String taskId = "6cc117a6-4a29-4557-a311-414ee840cc2b";
            
            // 验证任务是否存在
            AnalysisTask task = analysisService.getTaskById(taskId);
            System.out.println("找到任务: " + task.getTaskName());
            
            // 获取规则
            List<FieldRule> rules = analysisService.getFieldRules(task);
            System.out.println("找到规则数量: " + rules.size());
            
            // 输出部分规则信息
            if (!rules.isEmpty()) {
                int count = Math.min(5, rules.size());
                System.out.println("前" + count + "条规则示例:");
                for (int i = 0; i < count; i++) {
                    FieldRule rule = rules.get(i);
                    System.out.println((i+1) + ". " + rule.getRuleType() + ": " + rule.getRuleContent());
                }
            }
            
        } catch (Exception e) {
            System.err.println("获取任务和规则信息时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 