package com.example.multidoc.service;

import com.example.multidoc.model.ExcelField;
import com.example.multidoc.model.FieldRelation;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AIService {
    
    private static final Logger logger = LoggerFactory.getLogger(AIService.class);
    
    @Autowired
    private ChatClient chatClient;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Value("${ai.service.url}")
    private String aiServiceUrl;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 分析Excel字段结构
     * @param markdownTable Excel表格的Markdown表示
     * @return 字段名称和描述的解析结果
     */
    public String analyzeExcelStructure(String markdownTable) {
        String systemPrompt = "你是一个专业的数据分析助手，擅长分析表格结构。请分析以下Excel表格的结构(Markdown格式)，提取所有的列标题和对应的描述。";
        String userPrompt = "请列出以下Excel表格中所有的列标题及其描述：\n\n" + markdownTable;
        
        return executePrompt(systemPrompt, userPrompt);
    }
    
    /**
     * 从Word文档块中提取与特定字段相关的文本
     * @param fieldName 字段名称
     * @param fieldDescription 字段描述
     * @param wordChunks Word文档块列表
     * @return 与字段相关的文本
     */
    public String extractRelevantText(String fieldName, String fieldDescription, List<String> wordChunks) {
        String systemPrompt = "你是一个专业的文本分析助手，能够从大量文本中提取与特定主题相关的内容。";
        
        logger.info("开始为字段 '{}' 从 {} 个文档块中提取相关文本", fieldName, wordChunks.size());
        
        // 使用线程安全的StringBuilder来收集结果
        StringBuilder relevantTextBuilder = new StringBuilder();
        
        // 使用并行流处理文档块
        wordChunks.parallelStream().forEach(chunk -> {
            String userPrompt = String.format(
                "请从以下文本中找出与'%s'字段相关的所有描述、规则、指导和说明。如果字段描述为：'%s'。" +
                "仅返回相关文本，如果没有相关内容，请返回'无相关内容'。\n\n%s", 
                fieldName, fieldDescription, chunk
            );
            
            String response = executePrompt(systemPrompt, userPrompt);
            
            if (response != null && !response.isEmpty() && !response.contains("无相关内容")) {
                // 使用synchronized块来确保线程安全
                synchronized (relevantTextBuilder) {
                    relevantTextBuilder.append(response).append("\n\n");
                }
            }
        });
        
        String result = relevantTextBuilder.toString().trim();
        logger.info("字段 '{}' 提取完成，找到 {} 个相关内容", fieldName, result.isEmpty() ? 0 : result.split("\n\n").length);
        
        return result;
    }
    
    /**
     * 评估字段之间的相关性
     * @param sourceField 源字段
     * @param targetField 目标字段
     * @param sourceFieldText 源字段相关文本
     * @param targetFieldText 目标字段相关文本
     * @return 相关性评分JSON
     */
    public String evaluateFieldsRelation(ExcelField sourceField, ExcelField targetField, 
                                       String sourceFieldText, String targetFieldText) {
        String systemPrompt = "你是一个专业的数据关系分析专家，能够分析不同数据字段之间的关系。请评估以下两个字段之间的相关性，并给出0-1之间的相关性评分。";
        
        String userPrompt = String.format(
            "请分析以下两个字段之间的相关性：\n\n" +
            "字段1：%s\n描述：%s\n相关文本：%s\n\n" +
            "字段2：%s\n描述：%s\n相关文本：%s\n\n" +
            "请以JSON格式返回结果，包括相关性评分(0-1)和关系描述。格式如下：\n" +
            "{\n  \"score\": 0.75,\n  \"description\": \"这两个字段有较强的相关性，因为...\"\n}",
            sourceField.getFieldName(), sourceField.getDescription(), sourceFieldText,
            targetField.getFieldName(), targetField.getDescription(), targetFieldText
        );
        
        return executePrompt(systemPrompt, userPrompt);
    }
    
    /**
     * 提取字段规则
     * @param field 字段信息
     * @param fieldText 字段相关文本
     * @param relatedFields 相关字段列表
     * @return 字段规则JSON
     */
    public String extractFieldRules(ExcelField field, String fieldText, List<FieldRelation> relatedFields) {
        String systemPrompt = "你是一个专业的规则提取专家，能够从文本中识别出显式和隐含的规则。";
        
        StringBuilder relatedFieldsText = new StringBuilder();
        for (FieldRelation relation : relatedFields) {
            ExcelField relatedField = relation.getTargetField();
            relatedFieldsText.append(String.format(
                "相关字段: %s (相关性: %.2f)\n描述: %s\n\n",
                relatedField.getFieldName(), relation.getRelationScore(), relatedField.getDescription()
            ));
        }
        
        String userPrompt = String.format(
            "请从以下文本中提取关于'%s'字段的显式规则和隐含规则。\n\n" +
            "字段描述: %s\n\n" +
            "相关字段信息:\n%s\n\n" +
            "字段相关文本:\n%s\n\n" +
            "请以JSON格式返回结果，分别列出显式规则和隐含规则。格式如下:\n" +
            "{\n  \"explicitRules\": [\n    {\"rule\": \"规则1\", \"priority\": 3},\n    {\"rule\": \"规则2\", \"priority\": 2}\n  ],\n" +
            "  \"implicitRules\": [\n    {\"rule\": \"隐含规则1\", \"priority\": 2},\n    {\"rule\": \"隐含规则2\", \"priority\": 1}\n  ]\n}",
            field.getFieldName(), field.getDescription(), relatedFieldsText.toString(), fieldText
        );
        
        return executePrompt(systemPrompt, userPrompt);
    }
    
    /**
     * 编译和汇总规则
     * @param fieldRulesMap 字段规则映射
     * @return 汇总后的规则JSON
     */
    public String compileRules(Map<String, List<FieldRule>> fieldRulesMap) {
        String systemPrompt = "你是一个专业的规则整合专家，能够合并和优化各种规则。";
        
        // 使用线程安全的StringBuilder来收集结果
        StringBuilder rulesText = new StringBuilder();
        
        // 使用并行流处理规则
        fieldRulesMap.entrySet().parallelStream().forEach(entry -> {
            String fieldName = entry.getKey();
            List<FieldRule> rules = entry.getValue();
            
            // 使用synchronized块来确保线程安全
            synchronized (rulesText) {
                rulesText.append("字段: ").append(fieldName).append("\n");
                rulesText.append("规则:\n");
                
                for (FieldRule rule : rules) {
                    rulesText.append(String.format(
                        "- %s规则 (优先级: %d): %s\n",
                        rule.getRuleType() == FieldRule.RuleType.EXPLICIT ? "显式" : "隐含",
                        rule.getPriority(),
                        rule.getRuleContent()
                    ));
                }
                
                rulesText.append("\n");
            }
        });
        
        String userPrompt = String.format(
            "请合并和优化以下字段规则，解决冲突，去除重复，并按照规则的重要性排序。\n\n%s\n\n" +
            "请以JSON格式返回结果，按字段组织规则，并包含规则类型和优先级。",
            rulesText.toString()
        );
        
        return executePrompt(systemPrompt, userPrompt);
    }
    
    /**
     * 生成最终分析报告
     * @param taskName 任务名称
     * @param fields Excel字段列表
     * @param relations 字段关系列表
     * @param rules 字段规则列表
     * @return 分析报告JSON
     */
    public String generateAnalysisReport(String taskName, List<ExcelField> fields, 
                                        List<FieldRelation> relations, List<FieldRule> rules) {
        String systemPrompt = "你是一个专业的报告生成专家，能够将复杂的分析结果整理成清晰的报告。";
        
        StringBuilder fieldsText = new StringBuilder();
        for (ExcelField field : fields) {
            fieldsText.append(String.format(
                "- %s: %s\n",
                field.getFieldName(), field.getDescription()
            ));
        }
        
        StringBuilder relationsText = new StringBuilder();
        for (FieldRelation relation : relations) {
            relationsText.append(String.format(
                "- %s -> %s: 相关性 %.2f\n",
                relation.getSourceField().getFieldName(), 
                relation.getTargetField().getFieldName(),
                relation.getRelationScore()
            ));
        }
        
        StringBuilder rulesText = new StringBuilder();
        for (FieldRule rule : rules) {
            rulesText.append(String.format(
                "- %s - %s规则 (优先级: %d): %s\n",
                rule.getField().getFieldName(),
                rule.getRuleType() == FieldRule.RuleType.EXPLICIT ? "显式" : "隐含",
                rule.getPriority(),
                rule.getRuleContent()
            ));
        }
        
        String userPrompt = String.format(
            "请为分析任务'%s'生成一份详细的分析报告，包括以下内容:\n\n" +
            "1. Excel字段概况:\n%s\n\n" +
            "2. 字段间关系分析:\n%s\n\n" +
            "3. 字段规则总结:\n%s\n\n" +
            "请以JSON格式返回报告，包括摘要和详细分析。",
            taskName, fieldsText.toString(), relationsText.toString(), rulesText.toString()
        );
        
        return executePrompt(systemPrompt, userPrompt);
    }
    
    /**
     * 对Excel字段进行分类
     * @param fields Excel字段列表
     * @return 分类结果JSON
     */
    public String classifyExcelFields(List<ExcelField> fields) {
        String systemPrompt = "你是一个专业的数据分类专家，能够根据字段名称和描述对Excel字段进行合理的分类。";
        
        StringBuilder fieldsText = new StringBuilder();
        for (ExcelField field : fields) {
            fieldsText.append(String.format(
                "- %s: %s\n",
                field.getFieldName(), field.getDescription()
            ));
        }
        
        String userPrompt = String.format(
            "请对以下Excel字段进行分类，每个分类应该包含相关的字段。\n\n" +
            "字段列表:\n%s\n\n" +
            "请以JSON格式返回分类结果，格式如下：\n" +
            "{\n" +
            "  \"categories\": [\n" +
            "    {\n" +
            "      \"categoryName\": \"分类名称\",\n" +
            "      \"description\": \"分类描述\",\n" +
            "      \"fields\": [\"字段1\", \"字段2\", ...]\n" +
            "    },\n" +
            "    ...\n" +
            "  ]\n" +
            "}",
            fieldsText.toString()
        );
        
        return executePrompt(systemPrompt, userPrompt);
    }
    
    /**
     * 评估分类相关性
     */
    public String evaluateCategoryRelevance(JsonNode category, List<String> wordChunks) {
        try {
            String categoryName = category.get("categoryName").asText();
            String categoryDescription = category.get("description").asText();
            JsonNode fields = category.get("fields");

            // 构建提示
            StringBuilder prompt = new StringBuilder();
            prompt.append("请评估以下分类与文档内容的相关性：\n\n");
            prompt.append("分类名称：").append(categoryName).append("\n");
            prompt.append("分类描述：").append(categoryDescription).append("\n");
            prompt.append("包含字段：\n");
            for (JsonNode field : fields) {
                prompt.append("- ").append(field.get("fieldName").asText()).append("\n");
            }
            prompt.append("\n文档内容：\n");
            for (String chunk : wordChunks) {
                prompt.append(chunk).append("\n");
            }
            prompt.append("\n请评估该分类与文档内容的相关性，并返回JSON格式的结果：\n");
            prompt.append("{\n");
            prompt.append("  \"score\": 相关性分数（0-1之间）,\n");
            prompt.append("  \"relevantText\": \"相关文本内容\"\n");
            prompt.append("}");

            // 调用AI服务
            String response = callAIService(prompt.toString());
            
            // 验证JSON格式
            objectMapper.readTree(response);
            
            return response;
        } catch (Exception e) {
            logger.error("评估分类相关性失败", e);
            throw new RuntimeException("评估分类相关性失败: " + e.getMessage(), e);
        }
    }

    /**
     * 提取分类规则
     */
    public String extractCategoryRules(JsonNode category, String relevantText) {
        try {
            String categoryName = category.get("categoryName").asText();
            String categoryDescription = category.get("description").asText();
            JsonNode fields = category.get("fields");

            // 构建提示
            StringBuilder prompt = new StringBuilder();
            prompt.append("请根据以下信息提取分类规则：\n\n");
            prompt.append("分类名称：").append(categoryName).append("\n");
            prompt.append("分类描述：").append(categoryDescription).append("\n");
            prompt.append("包含字段：\n");
            for (JsonNode field : fields) {
                prompt.append("- ").append(field.get("fieldName").asText()).append("\n");
            }
            prompt.append("\n相关文本：\n").append(relevantText).append("\n");
            prompt.append("\n请提取该分类的规则，并返回JSON格式的结果：\n");
            prompt.append("{\n");
            prompt.append("  \"rules\": [\n");
            prompt.append("    {\n");
            prompt.append("      \"type\": \"规则类型（显式规则/隐含规则）\",\n");
            prompt.append("      \"content\": \"规则内容\",\n");
            prompt.append("      \"priority\": 优先级（1-3）\n");
            prompt.append("    }\n");
            prompt.append("  ]\n");
            prompt.append("}");

            // 调用AI服务
            String response = callAIService(prompt.toString());
            
            // 验证JSON格式
            objectMapper.readTree(response);
            
            return response;
        } catch (Exception e) {
            logger.error("提取分类规则失败", e);
            throw new RuntimeException("提取分类规则失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 执行提示并获取响应
     */
    private String executePrompt(String systemPrompt, String userPrompt) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(userPrompt));
        
        Prompt prompt = new Prompt(messages);
        ChatResponse response = chatClient.call(prompt);
        String content = response.getResult().getOutput().getContent();
        
        // 处理可能的 Markdown 格式的 JSON
        if (content.startsWith("```json")) {
            content = content.substring(content.indexOf("{"), content.lastIndexOf("}") + 1);
        }
        
        return content;
    }

    /**
     * 调用AI服务
     */
    private String callAIService(String prompt) {
        try {
            // 构建请求
            Map<String, Object> request = new HashMap<>();
            request.put("prompt", prompt);
            request.put("max_tokens", 2000);
            request.put("temperature", 0.7);

            // 发送请求
            String response = restTemplate.postForObject(
                aiServiceUrl,
                request,
                String.class
            );

            if (response == null || response.trim().isEmpty()) {
                throw new RuntimeException("AI service returned empty response");
            }

            return response;
        } catch (Exception e) {
            logger.error("调用AI服务失败", e);
            throw new RuntimeException("调用AI服务失败: " + e.getMessage(), e);
        }
    }
} 