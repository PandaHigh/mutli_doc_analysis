package com.example.multidoc.service;

import com.example.multidoc.model.ExcelField;
import com.example.multidoc.model.FieldRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AIService {
    
    private static final Logger logger = LoggerFactory.getLogger(AIService.class);
    
    @Autowired
    private ChatClient chatClient;
    
    private final ObjectMapper objectMapper = new ObjectMapper();


    /**
     * 编译和汇总规则
     * @param rules 字段规则列表
     * @return 汇总后的规则文本
     */
    public String compileRules(List<FieldRule> rules) {
        String systemPrompt = "你是一个专业的规则整合专家，能够合并和优化各种规则。";
        
        StringBuilder rulesText = new StringBuilder();
        
        // 直接处理所有规则，不按字段名分组
        for (FieldRule rule : rules) {
            rulesText.append(String.format(
                "- 字段: %s\n" +
                "  类型: %s\n" +
                "  规则: %s\n\n",
                    rule.getFieldNames(),
                rule.getRuleType() == FieldRule.RuleType.EXPLICIT ? "显式" : "隐含",
                rule.getRuleContent()
            ));
        }
        
        String userPrompt = String.format(
            "请合并和优化以下规则，解决冲突，去除重复。\n\n%s\n\n" +
            "请以清晰的文本格式返回结果，包含以下内容：\n" +
            "1. 每个规则需要包含：\n" +
            "   - 字段名\n" +
            "   - 规则类型（显式或隐含）\n" +
            "   - 规则内容\n\n" +
            "请确保规则清晰易读，格式统一。",
            rulesText.toString()
        );
        
        logger.info("开始编译和汇总规则，共 {} 条规则", rules.size());
        String result = executePrompt(systemPrompt, userPrompt);
        logger.info("规则编译完成，返回结果长度: {}", result.length());
        return result;
    }
    
    /**
     * 生成最终分析报告
     * @param taskName 任务名称
     * @param fields 生成最终分析报告
     * @param rules 字段规则列表
     * @return 分析报告JSON
     */
    public String generateAnalysisReport(String taskName, List<ExcelField> fields, List<FieldRule> rules) {
        String systemPrompt = "你是一个专业的报告生成专家，能够将复杂的分析结果整理成清晰的报告。";
        
        StringBuilder fieldsText = new StringBuilder();
        for (ExcelField field : fields) {
            fieldsText.append(String.format(
                "- %s: %s\n",
                field.getFieldName(), field.getDescription()
            ));
        }
        
        StringBuilder rulesText = new StringBuilder();
        for (FieldRule rule : rules) {
            String fieldNamesStr;
            try {
                String[] fieldNames = objectMapper.readValue(rule.getFieldNames(), String[].class);
                fieldNamesStr = String.join(", ", fieldNames);
            } catch (Exception e) {
                logger.warn("Failed to parse field names: {}, using raw value", rule.getFieldNames());
                fieldNamesStr = rule.getFieldNames();
            }
            
            rulesText.append(String.format(
                "- %s - %s规则: %s\n",
                fieldNamesStr,
                rule.getRuleType() == FieldRule.RuleType.EXPLICIT ? "显式" : "隐含",
                rule.getRuleContent()
            ));
        }
        
        String userPrompt = String.format(
            "请为报表分析任务'%s'生成一份详细的分析报告，包括以下内容:\n\n" +
            "1. 总体概述：\n" +
            "   - 分析任务的基本情况\n" +
            "   - 处理的文件数量和类型\n" +
            "   - 主要发现和结论\n\n" +
            "2. 字段分析：\n%s\n\n" +
            "3. 规则分析：\n%s\n\n" +
            "4. 需要注意的问题：\n\n" +
            "请以清晰、专业的语言撰写报告，确保内容完整、结构清晰。",
            taskName, fieldsText.toString(), rulesText.toString()
        );
        
        logger.info("开始生成分析报告，任务: {}, 字段数: {}, 规则数: {}", 
            taskName, fields.size(), rules.size());
            
        // 调用AI生成报告
        String report = executePrompt(systemPrompt, userPrompt);
        
        // 将报告转换为JSON格式
        try {
            Map<String, Object> reportJson = new HashMap<>();
            reportJson.put("summary", report);
            
            return objectMapper.writeValueAsString(reportJson);
        } catch (Exception e) {
            logger.error("Failed to convert report to JSON", e);
            return "{\"summary\": \"" + report + "\"}";
        }
    }


    
    /**
     * 执行提示并获取响应
     */
    public String executePrompt(String systemPrompt, String userPrompt) {
        int maxRetries = 5;
        long initialDelay = 5000; // 5秒
        long maxDelay = 60000; // 60秒
        long currentDelay = initialDelay;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                logger.debug("调用AI服务 (尝试 {}/{}), 系统提示长度: {}, 用户提示长度: {}", 
                    attempt, maxRetries, systemPrompt.length(), userPrompt.length());
                
                List<Message> messages = new ArrayList<>();
                messages.add(new SystemMessage(systemPrompt));
                messages.add(new UserMessage(userPrompt));
                
                Prompt prompt = new Prompt(messages);
                ChatResponse response = chatClient.call(prompt);
                
                if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                    throw new RuntimeException("AI服务返回空响应");
                }
                
                String content = response.getResult().getOutput().getContent();
                
                if (content == null || content.trim().isEmpty()) {
                    throw new RuntimeException("AI服务返回空内容");
                }

                // 检查响应内容是否被截断
                if (content.contains("[truncated") || content.endsWith("...")) {
                    logger.warn("AI服务返回的内容可能被截断，尝试重新获取完整响应");
                    throw new RuntimeException("AI服务返回的内容被截断");
                }
                
                // 处理可能的 Markdown 格式的 JSON
                if (content.startsWith("```json")) {
                    content = content.substring(content.indexOf("{"), content.lastIndexOf("}") + 1);
                } else if (content.startsWith("```")) {
                    content = content.substring(content.indexOf("\n") + 1, content.lastIndexOf("```"));
                }

                
                logger.debug("AI服务调用成功，返回内容长度: {}", content.length());
                return content;
            } catch (Exception e) {
                logger.warn("AI服务调用失败 (尝试 {}/{}): {}", attempt, maxRetries, e.getMessage());
                
                if (attempt == maxRetries) {
                    logger.error("AI服务调用失败，已达到最大重试次数", e);
                    throw new RuntimeException("AI服务调用失败，已达到最大重试次数: " + e.getMessage(), e);
                }
                
                try {
                    // 使用指数退避策略
                    Thread.sleep(currentDelay);
                    currentDelay = Math.min(currentDelay * 2, maxDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试过程中断", ie);
                }
            }
        }
        
        throw new RuntimeException("AI服务调用失败");
    }

    /**
     * 分析文本块内容
     */
    public String analyzeChunkContent(String content, List<ExcelField> fields) {
        String systemPrompt = "你是一个专业的数据分析专家，能够从文本中提取字段规则。请确保返回的JSON格式正确，字段名称中的特殊字符（如点号、中文字符）需要正确处理。";
        
        StringBuilder fieldsText = new StringBuilder();
        for (ExcelField field : fields) {
            fieldsText.append(String.format(
                "- %s (表名: %s): %s\n",
                field.getFieldName(),
                field.getTableName() != null ? field.getTableName() : "未指定",
                field.getDescription() != null ? field.getDescription() : "无描述"
            ));
        }
        
        String userPrompt = String.format(
            "请分析以下文本内容，提取字段的规则，最主要的目标是发现字段之间的隐式规则。注意：\n" +
            "1. 字段名称需要保持原样，不需要转义或替换\n" +
            "2. 确保返回的JSON格式正确\n" +
            "3. 规则类型必须是 EXPLICIT 或 IMPLICIT\n" +
            "4. 每个规则必须包含字段名集合、规则类型和规则内容\n" +
            "5. 可以包含置信度和表名（可选）\n" +
            "6. 一个规则可以关联多个相关字段\n\n" +
            "可用字段列表：\n%s\n\n" +
            "文本内容：\n%s\n\n" +
            "请返回JSON格式的分析结果，包含以下字段：\n" +
            "1. rules: 数组，每个元素包含：\n" +
            "   - fields: 字段名数组（必填，至少包含一个字段名）\n" +
            "   - type: 规则类型（必填，必须是 EXPLICIT 或 IMPLICIT）\n" +
            "   - content: 规则内容（必填）\n" +
            "   - confidence: 置信度（可选，0-1之间的数值）\n" +
            "   - tableName: 表名（可选）\n\n" +
            "示例返回格式：\n" +
            "{\n" +
            "  \"rules\": [\n" +
            "    {\n" +
            "      \"fields\": [\"字段名1\", \"字段名2\"],\n" +
            "      \"type\": \"EXPLICIT\",\n" +
            "      \"content\": \"规则内容\",\n" +
            "      \"confidence\": 0.95,\n" +
            "      \"tableName\": \"表名\"\n" +
            "    }\n" +
            "  ]\n" +
            "}",
            fieldsText.toString(), content
        );
        
        return executePrompt(systemPrompt, userPrompt);
    }

    public String categorizeField(String context) {
        List<ExcelField> dummyField = new ArrayList<>();
        ExcelField field = new ExcelField();
        field.setDescription(context);
        dummyField.add(field);
        
        try {
            JsonNode result = categorizeFields(dummyField);
            if (result.has("categories") && result.get("categories").size() > 0) {
                return result.get("categories").get(0).get("category").asText();
            }
            return "未分类";
        } catch (Exception e) {
            logger.error("字段分类失败", e);
            return "未分类";
        }
    }

    /**
     * 批量对字段进行分类
     * @param fields 需要分类的字段列表
     * @return 包含所有字段分类的JSON结果
     */
    public JsonNode categorizeFields(List<ExcelField> fields) {
        String systemPrompt = "你是一个专业的数据分析专家，能够根据字段的名称、描述和表名对字段进行分类。你的任务是将字段分成多个有意义的类别，每个类别包含3-6个相关字段。一个字段可以属于多个不同的类别。";
        
        StringBuilder fieldsInfo = new StringBuilder();
        for (ExcelField field : fields) {
            fieldsInfo.append(String.format(
                "- 字段名：%s\n  表名：%s\n  描述：%s\n\n",
                field.getFieldName(),
                field.getTableName() != null ? field.getTableName() : "未指定",
                field.getDescription() != null ? field.getDescription() : "无描述"
            ));
        }
        
        String userPrompt = String.format(
            "请对以下所有字段进行分类。分类应该反映字段的业务含义和用途。\n\n" +
            "字段列表：\n%s\n" +
            "请以JSON格式返回分类结果，格式如下：\n" +
            "{\n" +
            "  \"categories\": [\n" +
            "    {\n" +
            "      \"categoryName\": \"分类名称\",\n" +
            "      \"description\": \"分类描述\",\n" +
            "      \"fields\": [\n" +
            "        {\n" +
            "          \"fieldName\": \"字段名\",\n" +
            "          \"tableName\": \"表名\"\n" +
            "        }\n" +
            "      ]\n" +
            "    }\n" +
            "  ]\n" +
            "}\n\n" +
            "要求：\n" +
            "1. 分类名称应简洁明了（不超过20个字符）\n" +
            "2. 分类名称应准确反映字段的业务含义\n" +
            "3. 使用中文分类名称\n" +
            "4. 每个分类应包含3-6个相关字段\n" +
            "5. 同一个字段可以属于多个不同的分类\n" +
            "6. 分类应该基于字段的业务含义和用途，而不是简单的名称相似\n" +
            "7. 为每个分类提供简短的描述，说明该分类的用途和特点\n" +
            "8. 确保每个字段至少属于一个分类\n" +
            "9. 如果字段属于多个分类，应该在多个分类中都包含该字段\n",
            fieldsInfo.toString()
        );
        
        String response = executePrompt(systemPrompt, userPrompt);
        
        try {
            JsonNode result = objectMapper.readTree(response);
            // 验证返回的JSON格式是否正确
            if (!result.has("categories") || !result.get("categories").isArray()) {
                throw new RuntimeException("AI返回的JSON格式不正确：缺少categories数组");
            }
            
            // 记录分类结果
            logger.info("字段分类完成，共{}个分类", result.get("categories").size());
            for (JsonNode category : result.get("categories")) {
                logger.debug("分类：{}，描述：{}，包含{}个字段",
                    category.get("categoryName").asText(),
                    category.get("description").asText(),
                    category.get("fields").size());
            }
            
            return result;
        } catch (Exception e) {
            logger.error("解析AI返回的JSON失败", e);
            throw new RuntimeException("字段分类失败：无法解析返回结果", e);
        }
    }

    public JsonNode extractRules(String prompt, List<ExcelField> fields) {
        String systemPrompt = "你是一个专业的数据分析专家，能够从文本中提取字段规则。请确保返回的JSON格式正确，字段名称中的特殊字符（如点号、中文字符）需要正确处理。";
        
        StringBuilder fieldsText = new StringBuilder();
        for (ExcelField field : fields) {
            fieldsText.append(String.format(
                "- %s (表名: %s): %s\n",
                field.getFieldName(),
                field.getTableName() != null ? field.getTableName() : "未指定",
                field.getDescription() != null ? field.getDescription() : "无描述"
            ));
        }
        
        String userPrompt = String.format(
            "请分析以下文本内容，提取字段的规则，最主要的目标是发现字段之间的隐式规则，主要是发现数据校验规则，可以是等于、大于、小于、区间等数学关系。注意：\n" +
            "1. 字段名称需要保持原样，不需要转义或替换\n" +
            "2. 确保返回的JSON格式正确\n" +
            "3. 规则类型必须是 EXPLICIT 或 IMPLICIT\n" +
            "4. 每个规则必须包含字段名集合、规则类型和规则内容\n" +
            "5. 可以包含置信度和表名（可选）\n" +
            "6. 一个规则可以关联多个相关字段\n\n" +
            "可用字段列表：\n%s\n\n" +
            "文本内容：\n%s\n\n" +
            "请返回JSON格式的分析结果，包含以下字段：\n" +
            "1. rules: 数组，每个元素包含：\n" +
            "   - fields: 字段名数组（必填，至少包含一个字段名）\n" +
            "   - type: 规则类型（必填，必须是 EXPLICIT 或 IMPLICIT）\n" +
            "   - content: 规则内容（必填）\n" +
            "   - confidence: 置信度（可选，0-1之间的数值）\n" +
            "   - tableName: 表名（可选）\n\n" +
            "示例返回格式：\n" +
            "{\n" +
            "  \"rules\": [\n" +
            "    {\n" +
            "      \"fields\": [\"字段名1\", \"字段名2\"],\n" +
            "      \"type\": \"EXPLICIT\",\n" +
            "      \"content\": \"规则内容\",\n" +
            "      \"confidence\": 0.95,\n" +
            "      \"tableName\": \"表名\"\n" +
            "    }\n" +
            "  ]\n" +
            "}",
            fieldsText.toString(), prompt
        );
        
        String response = executePrompt(systemPrompt, userPrompt);
        
        try {
            return objectMapper.readTree(response);
        } catch (Exception e) {
            logger.error("解析AI返回的JSON失败", e);
            throw new RuntimeException("规则提取失败：无法解析返回结果", e);
        }
    }
} 