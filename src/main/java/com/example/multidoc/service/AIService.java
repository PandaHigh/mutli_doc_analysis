package com.example.multidoc.service;

import com.example.multidoc.model.ExcelField;
import com.example.multidoc.model.FieldRule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
     * 批量对字段进行分类
     * @param fields 需要分类的字段列表
     * @return 包含所有字段分类的JSON结果
     */
    public JsonNode categorizeFields(List<ExcelField> fields) {
        String systemPrompt = "你是一个专业的数据分析专家，能够根据字段的名称、描述和表名对字段进行分类，分类后的字段将用于提取字段规则发现其中的数据校验规则。你的任务是将字段分成多个有意义的类别，每个类别至少包含2个相关字段以上，尽可能细分类别。一个字段可以属于多个不同的类别。";
        
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
            "4. 每个分类应包含2个以上相关字段\n" +
            "5. 同一个字段可以属于多个不同的分类\n" +
            "6. 分类应该基于字段的业务含义和用途，而不是简单的名称相似，尽可能细分类别\n" +
            "7. 为每个分类提供简短的描述，说明该分类的用途和特点\n" +
            "8. 剔除无法用于规则提取的分类，不要返回无法用于规则提取的分类\n" +
            "9. 如果字段属于多个分类，应该在多个分类中都包含该字段\n"+
            "10. 不同表的字段可以属于同一个分类\n",
             fieldsInfo
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
        String systemPrompt = "你是一个具有丰富金融行业经验的数据分析专家，熟悉银行报表体系和监管要求，擅长挖掘数据之间的内在关联和潜在规则。你能够不仅识别明确的数据规则，更能发现填报说明中未明确描述但行业内通行的隐含规则。请确保返回的JSON格式正确，字段名称需保持原样不做更改。";
        
        StringBuilder fieldsText = new StringBuilder();
        for (ExcelField field : fields) {
            fieldsText.append(String.format(
                "- %s (表名: %s): %s\n",
                field.getFieldName(),
                field.getTableName() != null ? field.getTableName() : "未指定",
                field.getDescription() != null ? field.getDescription() : "无描述"
            ));
        }
        
        String userPrompt = 
            "请根据以下银行报表填报背景知识和报表模板相关字段，深入挖掘字段之间的数据校验规则，并区分为两类：\n\n" +
            "1. 显式规则(EXPLICIT)：填报背景中已明确说明的数据校验规则\n" +
            "2. 隐式规则(IMPLICIT)：填报背景中未明确说明，但根据业务逻辑、行业常识、数学关系等可以推断出的潜在数据校验规则\n\n" +
            "【重要】本次分析的核心目标是挖掘隐式规则，这些规则是银行报表填报背景中未明确说明，但对数据质量和业务逻辑至关重要的校验关系。\n\n" +
            "隐式规则的类型可能包括但不限于：\n" +
            "- 业务上必然存在的数值关系（如总量必须等于各分项之和）\n" +
            "- 时间序列上的逻辑关系（如开始时间必须早于结束时间）\n" +
            "- 跨表字段之间的一致性要求（如不同表中相同业务指标的数值应保持一致）\n" +
            "- 根据行业特性推断的合理性规则（如某些比率或数值必须在特定区间内）\n" +
            "- 字段填报的依赖关系（如A字段有值则B字段必须有值）\n\n" +
            "银行报表填报场景下的隐式规则示例：\n" +
            "1. 资产负债表中，'资产合计'必须等于'负债合计'加'所有者权益合计'（会计恒等式）\n" +
            "2. '贷款损失准备金'应该与'不良贷款'保持一定比例关系（拨备覆盖率要求）\n" +
            "3. '流动性比率'通常应大于某个监管要求的阈值（如25%%）\n" +
            "4. '资本充足率'必须高于监管最低要求（通常为8%%）\n" +
            "5. '各项贷款余额'的环比增长率不应显著偏离行业平均水平\n" +
            "6. '利息收入'和'贷款余额'应保持合理的比例关系\n" +
            "7. '同业拆借资产'和'同业拆借负债'在报表间的交叉验证\n" +
            "8. '表外业务'与相应的'风险资产'应有合理关联\n" +
            "9. '净利润'应等于'营业收入'减'营业支出'再减'税费'\n\n" +
            "相关字段列表：\n" + fieldsText.toString() + "\n\n" +
            "填报背景知识：\n" + prompt + "\n\n" +
            "请返回JSON格式的分析结果，包含以下字段：\n" +
            "1. rules: 数组，每个元素包含：\n" +
            "   - fields: 字段名数组（必填，至少包含两个字段名，返回格式为表名-字段名）\n" +
            "   - type: 规则类型（必填，必须是 EXPLICIT 或 IMPLICIT）\n" +
            "   - content: 规则内容（必填，详细描述规则的具体要求和逻辑）\n" +
            "   - confidence: 置信度（必填，0-1之间的数值，表示规则的可靠性）\n" +
            "   - reasoning: 推理过程（必填，说明如何得出该规则的思考过程，特别是对隐式规则要详细阐述推导逻辑）\n\n" +
            "示例返回格式：\n" +
            "{\n" +
            "  \"rules\": [\n" +
            "    {\n" +
            "      \"fields\": [\"资产负债表-资产合计\", \"资产负债表-负债合计\", \"资产负债表-所有者权益合计\"],\n" +
            "      \"type\": \"IMPLICIT\",\n" +
            "      \"content\": \"资产合计必须等于负债合计加所有者权益合计\",\n" +
            "      \"confidence\": 0.98,\n" +
            "      \"reasoning\": \"虽然填报说明未明确提及，但这是会计恒等式，是银行财务报表的基本平衡关系\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"fields\": [\"贷款质量统计表-贷款损失准备金\", \"贷款质量统计表-不良贷款总额\"],\n" +
            "      \"type\": \"IMPLICIT\",\n" +
            "      \"content\": \"贷款损失准备金应不低于不良贷款总额的150%%（拨备覆盖率要求）\",\n" +
            "      \"confidence\": 0.90,\n" +
            "      \"reasoning\": \"基于银行业监管规定，拨备覆盖率（贷款损失准备金/不良贷款总额）通常要求不低于150%%，虽未在填报说明中提及，但这是行业通行的监管要求\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";
        
        String response = executePrompt(systemPrompt, userPrompt);
        
        try {
            return objectMapper.readTree(response);
        } catch (Exception e) {
            logger.error("解析AI返回的JSON失败", e);
            throw new RuntimeException("规则提取失败：无法解析返回结果", e);
        }
    }
} 