package com.example.multidoc.service;

import com.example.multidoc.model.ExcelField;
import com.example.multidoc.model.FieldRule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

@Service
public class AIService {
    
    private static final Logger logger = LoggerFactory.getLogger(AIService.class);
    
    @Autowired
    private ChatClient chatClient;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${app.field-batch-size:100}")
    private int fieldBatchSize;
    
    @Value("${app.batch-interval-ms:30000}")
    private int batchIntervalMs;


    
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
                String errorMessage = e.getMessage();
                boolean shouldRetry = true;
                long retryDelay = currentDelay;
                
                // 分析异常类型，决定是否需要重试以及如何重试
                if (e.getMessage() != null) {
                    // 处理特定类型的错误
                    if (e.getMessage().contains("text/html") || (e.getCause() != null && e.getCause().toString().contains("JsonParseException"))) {
                        logger.warn("检测到HTML响应或JSON解析错误，这可能是API服务暂时性问题 (尝试 {}/{})", attempt, maxRetries);
                        retryDelay = Math.min(currentDelay * 2, maxDelay); // 对于这类错误使用更长的延迟
                    } else if (e.getMessage().contains("429") || e.getMessage().contains("too many requests")) {
                        logger.warn("检测到频率限制错误，将增加重试延迟 (尝试 {}/{})", attempt, maxRetries);
                        retryDelay = Math.min(currentDelay * 3, maxDelay * 2); // 对于频率限制使用更长的延迟
                    } else if (e.getMessage().contains("500") || e.getMessage().contains("503") || e.getMessage().contains("server error")) {
                        logger.warn("检测到服务器错误，服务可能暂时不可用 (尝试 {}/{})", attempt, maxRetries);
                        retryDelay = Math.min(currentDelay * 2, maxDelay);
                    } else if (e.getMessage().contains("timeout") || e.getMessage().contains("timed out")) {
                        logger.warn("检测到请求超时，将增加请求超时时间 (尝试 {}/{})", attempt, maxRetries);
                        retryDelay = Math.min(currentDelay * 2, maxDelay);
                    } else if (e.getMessage().contains("AI服务返回的内容被截断")) {
                        logger.warn("检测到内容截断，尝试简化请求 (尝试 {}/{})", attempt, maxRetries);
                        // 可以考虑简化提示内容或减少token数量，但这需要更复杂的逻辑
                    }
                }
                
                logger.warn("AI服务调用失败 (尝试 {}/{}): {}", attempt, maxRetries, errorMessage);
                
                if (attempt == maxRetries) {
                    logger.error("AI服务调用失败，已达到最大重试次数", e);
                    throw new RuntimeException("AI服务调用失败，已达到最大重试次数: " + errorMessage, e);
                }
                
                try {
                    // 使用根据错误类型调整的退避策略
                    logger.info("等待 {} 毫秒后进行第 {} 次重试", retryDelay, attempt + 1);
                    Thread.sleep(retryDelay);
                    currentDelay = retryDelay;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试过程中断", ie);
                }
            }
        }
        
        throw new RuntimeException("AI服务调用失败");
    }


    /**
     * 批量对字段进行分类，支持大量字段的批次处理
     * @param fields 需要分类的字段列表
     * @return 包含所有字段分类的JSON结果
     */
    public JsonNode categorizeFields(List<ExcelField> fields) {
        return categorizeFields(fields, null);
    }
    
    /**
     * 批量对字段进行分类，支持大量字段的批次处理，并通过回调函数报告进度
     * @param fields 需要分类的字段列表
     * @param progressCallback 进度回调函数，参数为当前批次和总批次数
     * @return 包含所有字段分类的JSON结果
     */
    public JsonNode categorizeFields(List<ExcelField> fields, BiConsumer<Integer, Integer> progressCallback) {
        // 如果字段数量较少，直接处理
        if (fields.size() <= fieldBatchSize) {
            JsonNode result = categorizeSingleBatch(fields, null);
            if (progressCallback != null) {
                progressCallback.accept(1, 1);
            }
            return result;
        }
        
        int totalBatches = (int)Math.ceil((double)fields.size()/fieldBatchSize);
        logger.info("字段数量较大({}个)，启用批次处理，批次大小: {}, 批次间隔: {}毫秒, 总批次: {}", 
            fields.size(), fieldBatchSize, batchIntervalMs, totalBatches);
        
        // 累积的分类结果
        ObjectNode accumulatedResult = objectMapper.createObjectNode();
        ArrayNode categoriesArray = objectMapper.createArrayNode();
        accumulatedResult.set("categories", categoriesArray);
        
        // 分批处理
        for (int i = 0; i < fields.size(); i += fieldBatchSize) {
            int end = Math.min(i + fieldBatchSize, fields.size());
            List<ExcelField> batch = fields.subList(i, end);
            int currentBatch = (i/fieldBatchSize) + 1;
            
            logger.info("处理批次 {}/{}, 字段数: {}", 
                       currentBatch, totalBatches, batch.size());
            
            // 将当前批次的字段与已有的分类信息一起传递给AI
            JsonNode batchResult = categorizeSingleBatch(batch, accumulatedResult);
            
            // 合并新的分类结果到累积结果中
            updateAccumulatedResult(accumulatedResult, batchResult);
            
            // 报告进度
            if (progressCallback != null) {
                progressCallback.accept(currentBatch, totalBatches);
            }
            
            // 添加批次间延迟，避免过快调用大模型
            if (i + fieldBatchSize < fields.size()) {
                try {
                    logger.info("批次处理完成，等待 {} 毫秒后处理下一批次...", batchIntervalMs);
                    Thread.sleep(batchIntervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("批次间延迟被中断", e);
                }
            }
        }
        
        // 返回累积的结果
        return accumulatedResult;
    }

    /**
     * 处理单个批次的字段分类，并考虑已有的分类
     * @param fields 当前批次的字段
     * @param existingCategories 已有的分类结果（可以为null，表示第一个批次）
     * @return 当前批次的分类结果
     */
    private JsonNode categorizeSingleBatch(List<ExcelField> fields, JsonNode existingCategories) {
        String systemPrompt = "你是一个专业的数据分析专家，能够根据字段的名称、描述和表名对字段进行分类，分类后的字段将用于提取字段规则发现其中的数据校验规则。你的任务是将字段分成多个有意义的类别，每个类别至少包含2个相关字段以上，尽可能细分类别。一个字段可以属于多个不同的类别。";
        
        StringBuilder fieldsInfo = new StringBuilder();
        for (ExcelField field : fields) {
            fieldsInfo.append(String.format(
                "-字段名：%s\n 表名：%s\n\n",
                field.getFieldName(),
                field.getTableName() != null ? field.getTableName() : "未指定"
            ));
        }
        
        StringBuilder existingCategoriesInfo = new StringBuilder();
        if (existingCategories != null && existingCategories.has("categories")) {
            existingCategoriesInfo.append("已有的分类：\n");
            for (JsonNode category : existingCategories.get("categories")) {
                existingCategoriesInfo.append(String.format(
                    "- %s: %s\n",
                    category.get("categoryName").asText(),
                    category.get("description").asText()
                ));
            }
        }
        
        String userPrompt;
        if (existingCategories != null && existingCategories.has("categories") && 
            existingCategories.get("categories").size() > 0) {
            userPrompt = String.format(
                "请对以下所有字段进行分类。分类应该反映字段的业务含义和用途。\n\n" +
                "字段列表：\n%s\n\n" +
                "我们已经有一些现有的分类，你可以将新字段分配到这些已有的分类中，如果合适的话：\n%s\n\n" +
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
                "1. 尽可能利用已有的分类，将新字段分配到适合的已有分类中\n" +
                "2. 只有当现有分类都不适合时，才创建新的分类\n" +
                "3. 分类名称应简洁明了（不超过20个字符）\n" +
                "4. 分类名称应准确反映字段的业务含义\n" +
                "5. 使用中文分类名称\n" +
                "6. 每个分类应包含2个以上相关字段\n" +
                "7. 同一个字段可以属于多个不同的分类\n" +
                "8. 分类应该基于字段的业务含义和用途，而不是简单的名称相似\n" +
                "9. 为每个分类提供简短的描述，说明该分类的用途和特点\n" +
                "10. 剔除无法用于规则提取的分类，不要返回无法用于规则提取的分类\n" +
                "11. 如果字段属于多个分类，应该在多个分类中都包含该字段\n"+
                "12. 不同表的字段可以属于同一个分类\n",
                fieldsInfo.toString(), existingCategoriesInfo.toString()
            );
        } else {
            // 第一个批次，没有已有分类
            userPrompt = String.format(
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
                fieldsInfo.toString()
            );
        }
        
        String response = executePrompt(systemPrompt, userPrompt);
        
        try {
            JsonNode result = objectMapper.readTree(response);
            // 验证返回的JSON格式是否正确
            if (!result.has("categories") || !result.get("categories").isArray()) {
                throw new RuntimeException("AI返回的JSON格式不正确：缺少categories数组");
            }
            
            return result;
        } catch (Exception e) {
            logger.error("解析AI返回的JSON失败", e);
            throw new RuntimeException("字段分类失败：无法解析返回结果", e);
        }
    }

    /**
     * 更新累积的分类结果
     * @param accumulatedResult 当前累积的分类结果
     * @param newResult 新一批次的分类结果
     */
    private void updateAccumulatedResult(ObjectNode accumulatedResult, JsonNode newResult) {
        if (!newResult.has("categories") || !newResult.get("categories").isArray()) {
            return;
        }
        
        ArrayNode accumulatedCategories = (ArrayNode) accumulatedResult.get("categories");
        Map<String, ObjectNode> categoryMap = new HashMap<>();
        
        // 先将已有分类放入映射中
        for (JsonNode category : accumulatedCategories) {
            String categoryName = category.get("categoryName").asText();
            categoryMap.put(categoryName, (ObjectNode) category);
        }
        
        // 处理新的分类
        for (JsonNode newCategory : newResult.get("categories")) {
            String categoryName = newCategory.get("categoryName").asText();
            
            if (categoryMap.containsKey(categoryName)) {
                // 更新已有分类
                ObjectNode existingCategory = categoryMap.get(categoryName);
                ArrayNode existingFields = (ArrayNode) existingCategory.get("fields");
                
                // 创建字段唯一标识符集合
                Set<String> existingFieldKeys = new HashSet<>();
                for (JsonNode field : existingFields) {
                    String fieldKey = field.get("fieldName").asText() + 
                                      ":" + field.get("tableName").asText();
                    existingFieldKeys.add(fieldKey);
                }
                
                // 添加新字段，避免重复
                for (JsonNode field : newCategory.get("fields")) {
                    String fieldKey = field.get("fieldName").asText() + 
                                      ":" + field.get("tableName").asText();
                    if (!existingFieldKeys.contains(fieldKey)) {
                        existingFields.add(field);
                    }
                }
            } else {
                // 添加新分类
                accumulatedCategories.add(newCategory);
                categoryMap.put(categoryName, (ObjectNode) newCategory);
            }
        }
        
        logger.info("更新后累积分类数: {}", accumulatedCategories.size());
    }

    public JsonNode extractRules(String prompt, List<ExcelField> fields) {
        String systemPrompt = "你是一个具有丰富金融行业经验的数据分析专家，熟悉银行报表体系和监管要求，擅长挖掘数据之间的内在关联和潜在规则。你能够不仅识别明确的数据规则，更能发现填报说明中未明确描述但行业内通行的隐含规则。请确保返回的JSON格式正确，字段名称需保持原样不做更改。";
        
        StringBuilder fieldsText = new StringBuilder();
        for (ExcelField field : fields) {
            fieldsText.append(String.format(
                "- %s (表名: %s)\n",
                field.getFieldName(),
                field.getTableName() != null ? field.getTableName() : "未指定"
            ));
        }
        
        String userPrompt = 
            "请根据以下银行报表填报背景知识和报表模板相关字段，深入挖掘跨表字段之间的数据校验规则，并区分为两类：\n\n" +
            "1. 显式规则(EXPLICIT)：填报背景中已明确说明的数据校验规则\n" +
            "2. 隐式规则(IMPLICIT)：填报背景中未明确说明，但根据业务逻辑、行业常识、数学关系等可以推断出的潜在数据校验规则\n\n" +
            "【核心要求】本次分析的首要目标是挖掘跨表字段之间的数据校验逻辑，尤其是不同报表间数据项的一致性、勾稽关系和业务逻辑关联。请重点关注：\n\n" +
            "1. 跨表数据一致性：同一业务指标在不同报表中的数值必须保持一致\n" +
            "2. 跨表勾稽关系：不同报表间存在的加总、分解或衍生关系\n" +
            "3. 跨表业务逻辑：基于业务流程或监管要求导致的跨表关联规则\n" +
            "4. 跨表时序关系：同一指标在不同报表的时间序列上应保持的关系\n\n" +
            "隐式规则的类型可能包括但不限于：\n" +
            "- 业务上必然存在的数值关系（如总量必须等于各分项之和）\n" +
            "- 时间序列上的逻辑关系（如开始时间必须早于结束时间）\n" +
            "- 跨表字段之间的一致性要求（如不同报表中相同业务指标的数值必须完全一致）\n" +
            "- 根据行业特性推断的合理性规则（如某些比率或数值必须在特定区间内）\n" +
            "- 字段填报的依赖关系（如A字段有值则B字段必须有值）\n\n" +
            "跨表规则示例：\n" +
            "1. '资产负债表-贷款总额'必须等于'贷款明细表-各项贷款之和'\n" +
            "2. '利润表-利息收入'应与'利息收入明细表-利息收入合计'保持一致\n" +
            "3. '资本充足率报表-风险加权资产'应等于各类资产乘以对应风险权重后的总和\n" +
            "4. '贷款质量统计表-新增不良贷款'应等于本期'不良贷款总额'减去上期'不良贷款总额'再加上本期'不良贷款处置金额'\n" +
            "5. '大额风险暴露表'中客户授信总额不应超过'资本充足率表'中一级资本的25%\n\n" +
            "银行报表填报场景下的其他重要隐式规则：\n" +
            "1. 资产负债表中，'资产合计'必须等于'负债合计'加'所有者权益合计'（会计恒等式）\n" +
            "2. '贷款损失准备金'应该与'不良贷款'保持一定比例关系（拨备覆盖率要求）\n" +
            "3. '流动性比率'通常应大于某个监管要求的阈值（如25%%）\n" +
            "4. '资本充足率'必须高于监管最低要求（通常为8%%）\n" +
            "5. '净利润'应等于'营业收入'减'营业支出'再减'税费'\n\n" +
            "相关字段列表：\n" + fieldsText.toString() + "\n\n" +
            "填报背景知识：\n" + prompt + "\n\n" +
            "请返回JSON格式的分析结果，包含以下字段：\n" +
            "1. rules: 数组，每个元素包含：\n" +
            "   - fields: 字段名数组（必填，至少包含两个字段名，返回格式为表名-字段名）\n" +
            "   - type: 规则类型（必填，必须是 EXPLICIT 或 IMPLICIT）\n" +
            "   - content: 规则内容（必填，详细描述规则的具体要求和逻辑）\n" +
            "   - confidence: 置信度（必填，0-1之间的数值，表示规则的可靠性）\n" +
            "   - reasoning: 推理过程（必填，说明如何得出该规则的思考过程，特别是对跨表隐式规则要详细阐述推导逻辑）\n" +
            "   - isInterTable: 布尔值（必填，标识是否为跨表规则）\n\n" +
            "示例返回格式：\n" +
            "{\n" +
            "  \"rules\": [\n" +
            "    {\n" +
            "      \"fields\": [\"资产负债表-贷款总额\", \"贷款明细表-各项贷款合计\"],\n" +
            "      \"type\": \"IMPLICIT\",\n" +
            "      \"content\": \"资产负债表中的贷款总额必须等于贷款明细表中各项贷款合计\",\n" +
            "      \"confidence\": 0.95,\n" +
            "      \"reasoning\": \"虽然填报说明未明确提及，但根据会计准则和报表一致性原则，总表中的汇总项应等于明细表中相应项目的合计值\",\n" +
            "      \"isInterTable\": true\n" +
            "    },\n" +
            "    {\n" +
            "      \"fields\": [\"资产负债表-资产合计\", \"资产负债表-负债合计\", \"资产负债表-所有者权益合计\"],\n" +
            "      \"type\": \"IMPLICIT\",\n" +
            "      \"content\": \"资产合计必须等于负债合计加所有者权益合计\",\n" +
            "      \"confidence\": 0.98,\n" +
            "      \"reasoning\": \"虽然填报说明未明确提及，但这是会计恒等式，是银行财务报表的基本平衡关系\",\n" +
            "      \"isInterTable\": false\n" +
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

    /**
     * 验证规则并发现新规则
     * @param rulesJson 现有规则的JSON字符串
     * @param mdContent MD格式的Excel内容
     * @return 验证后的规则和新发现的规则
     */
    public JsonNode validateRules(String rulesJson, String mdContent) {
        try {
            String systemPrompt = "你是一个数据分析和规则验证专家，擅长从数据中验证规则并发现新的规则。";
            
            String userPrompt = String.format(
                "我需要你验证一组现有规则，并从新数据中发现更多规则，请确保返回的JSON格式正确，字段名称需保持原样不做更改，原有的规则都需要验证并返回。以下是现有规则:\n\n" +
                "%s\n\n" +
                "以下是新的Excel数据内容（Markdown格式）:\n\n" +
                "%s\n\n" +
                "请执行以下任务:\n" +
                "1. 验证每个现有规则在新数据中是否成立，如果不成立请说明原因\n" +
                "2. 从新数据中发现可能存在的新规则\n" +
                "3. 将结果返回为JSON格式\n\n" +
                "返回的JSON格式如下:\n" +
                "{\n" +
                "  \"validatedRules\": [\n" +
                "    {\n" +
                "      \"ruleId\": \"原规则ID\",\n" +
                "      \"ruleContent\": \"规则内容\",\n" +
                "      \"isValid\": true/false,\n" +
                "      \"reason\": \"不成立的原因（如果不成立）\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"newRules\": [\n" +
                "    {\n" +
                "      \"ruleType\": \"EXPLICIT或IMPLICIT\",\n" +
                "      \"ruleContent\": \"新规则内容\",\n" +
                "      \"confidence\": 0.8, // 置信度0-1\n" +
                "      \"fields\": [\"相关字段1\", \"相关字段2\"]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"summary\": \"整体分析总结\"\n" +
                "}",
                rulesJson, mdContent
            );
            
            logger.info("调用AI进行规则验证，规则数量: {}", rulesJson.length() > 100 ? "长文本" : rulesJson);
            String response = executePrompt(systemPrompt, userPrompt);
            
            try {
                JsonNode result = objectMapper.readTree(response);
                // 验证返回的JSON格式是否正确
                if (!result.has("validatedRules") || !result.get("validatedRules").isArray() || 
                    !result.has("newRules") || !result.get("newRules").isArray()) {
                    throw new RuntimeException("AI返回的JSON格式不正确：缺少validatedRules或newRules数组");
                }
                
                // 记录验证结果
                logger.info("规则验证完成，已验证规则数: {}, 新发现规则数: {}", 
                           result.get("validatedRules").size(), 
                           result.get("newRules").size());
                
                return result;
            } catch (Exception e) {
                logger.error("解析AI返回的JSON失败", e);
                throw new RuntimeException("规则验证失败：无法解析返回结果", e);
            }
        } catch (Exception e) {
            logger.error("调用AI进行规则验证失败", e);
            throw new RuntimeException("规则验证失败: " + e.getMessage(), e);
        }
    }
} 