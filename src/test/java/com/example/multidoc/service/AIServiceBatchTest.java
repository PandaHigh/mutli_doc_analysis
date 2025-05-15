package com.example.multidoc.service;

import com.example.multidoc.model.ExcelField;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
public class AIServiceBatchTest {

    private static final Logger logger = LoggerFactory.getLogger(AIServiceBatchTest.class);

    @Mock
    private ChatClient chatClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AIService aiService;
    
    @BeforeEach
    public void setup() {
        // 设置测试用的批量大小为50
        ReflectionTestUtils.setField(aiService, "fieldBatchSize", 50);
    }

    /**
     * 测试批次处理功能
     * 创建大量模拟字段，验证批次处理功能是否正常工作
     */
    @Test
    public void testBatchProcessing() throws Exception {
        // 创建大量模拟字段
        List<ExcelField> fields = createMockFields(120); // 120个字段，超过测试设置的批次大小50
        
        logger.info("开始测试批次处理功能，字段总数: {}, 批次大小: {}", 
                   fields.size(), 
                   ReflectionTestUtils.getField(aiService, "fieldBatchSize"));
        
        // 模拟ChatClient返回响应
        mockChatClientResponse();
        
        // 调用批处理方法
        JsonNode result = aiService.categorizeFields(fields);
        
        // 验证结果
        assertNotNull(result, "结果不应为空");
        assertTrue(result.has("categories"), "结果应包含categories字段");
        assertTrue(result.get("categories").isArray(), "categories应为数组");
        
        // 输出分类信息
        logger.info("批处理完成，结果中分类数: {}", result.get("categories").size());
        for (JsonNode category : result.get("categories")) {
            logger.info("分类: {}, 描述: {}, 字段数: {}", 
                category.get("categoryName").asText(),
                category.get("description").asText(),
                category.get("fields").size());
        }
    }
    
    /**
     * 模拟ChatClient返回响应
     */
    private void mockChatClientResponse() {
        // 创建模拟的ChatResponse
        ChatResponse mockResponse = Mockito.mock(ChatResponse.class);
        Generation mockGeneration = Mockito.mock(Generation.class);
        AssistantMessage mockOutput = new AssistantMessage(createMockCategoryJson());
        
        when(mockGeneration.getOutput()).thenReturn(mockOutput);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(chatClient.call(any(Prompt.class))).thenReturn(mockResponse);
    }
    
    /**
     * 创建模拟的分类JSON响应
     */
    private String createMockCategoryJson() {
        try {
            ObjectNode rootNode = objectMapper.createObjectNode();
            ArrayNode categoriesArray = objectMapper.createArrayNode();
            
            // 创建几个模拟分类
            String[] categoryNames = {"资产类", "负债类", "收入类", "支出类", "现金流量类"};
            String[] descriptions = {
                "资产负债表中的资产项目", 
                "资产负债表中的负债项目",
                "利润表中的收入项目",
                "利润表中的支出项目",
                "现金流量表中的现金流项目"
            };
            
            for (int i = 0; i < categoryNames.length; i++) {
                ObjectNode category = objectMapper.createObjectNode();
                category.put("categoryName", categoryNames[i]);
                category.put("description", descriptions[i]);
                
                ArrayNode fieldsArray = objectMapper.createArrayNode();
                // 每个分类添加3个模拟字段
                for (int j = 0; j < 3; j++) {
                    ObjectNode field = objectMapper.createObjectNode();
                    field.put("fieldName", "模拟字段" + (i * 3 + j));
                    field.put("tableName", "模拟表" + i);
                    fieldsArray.add(field);
                }
                
                category.set("fields", fieldsArray);
                categoriesArray.add(category);
            }
            
            rootNode.set("categories", categoriesArray);
            return objectMapper.writeValueAsString(rootNode);
        } catch (Exception e) {
            logger.error("创建模拟JSON失败", e);
            return "{\"categories\":[]}";
        }
    }
    
    /**
     * 创建模拟字段用于测试
     */
    private List<ExcelField> createMockFields(int count) {
        List<ExcelField> fields = new ArrayList<>();
        
        // 创建财务相关字段
        String[] tables = {"资产负债表", "利润表", "现金流量表", "资本充足率报表", "贷款质量统计表"};
        
        // 资产负债表字段
        String[] assetFields = {
            "资产总计", "负债总计", "所有者权益", "贷款总额", "存款总额", 
            "现金及存放中央银行款项", "存放同业款项", "拆出资金", "交易性金融资产",
            "债权投资", "其他债权投资", "长期股权投资", "固定资产", "无形资产",
            "递延所得税资产", "其他资产", "同业及其他金融机构存放款项",
            "拆入资金", "衍生金融负债", "应付债券", "应付职工薪酬",
            "应交税费", "预计负债", "递延所得税负债", "其他负债"
        };
        
        // 利润表字段
        String[] incomeFields = {
            "营业收入", "利息净收入", "利息收入", "利息支出", "手续费及佣金净收入",
            "手续费及佣金收入", "手续费及佣金支出", "投资收益", "其他收益",
            "资产处置收益", "营业支出", "税金及附加", "业务及管理费",
            "信用减值损失", "营业利润", "营业外收入", "营业外支出",
            "利润总额", "所得税费用", "净利润"
        };
        
        // 现金流量表字段
        String[] cashFlowFields = {
            "经营活动现金流入", "经营活动现金流出", "经营活动产生的现金流量净额",
            "投资活动现金流入", "投资活动现金流出", "投资活动产生的现金流量净额",
            "筹资活动现金流入", "筹资活动现金流出", "筹资活动产生的现金流量净额",
            "现金及现金等价物净增加额", "期初现金及现金等价物余额", "期末现金及现金等价物余额"
        };
        
        // 资本充足率报表字段
        String[] capitalFields = {
            "核心一级资本", "其他一级资本", "二级资本", "总资本净额",
            "风险加权资产", "核心一级资本充足率", "一级资本充足率", "资本充足率",
            "储备资本要求", "逆周期资本要求", "附加资本要求", "总杠杆率"
        };
        
        // 贷款质量统计表字段
        String[] loanFields = {
            "正常贷款", "关注贷款", "次级贷款", "可疑贷款", "损失贷款",
            "不良贷款合计", "贷款拨备率", "不良贷款率", "拨备覆盖率",
            "行业投向", "客户类型", "期限结构", "担保方式", "区域分布"
        };
        
        // 组合所有字段
        int fieldAdded = 0;
        int tableIndex = 0;
        int fieldIndex = 0;
        
        while (fieldAdded < count) {
            String tableName = tables[tableIndex % tables.length];
            String[] fieldArray;
            
            switch (tableName) {
                case "资产负债表":
                    fieldArray = assetFields;
                    break;
                case "利润表":
                    fieldArray = incomeFields;
                    break;
                case "现金流量表":
                    fieldArray = cashFlowFields;
                    break;
                case "资本充足率报表":
                    fieldArray = capitalFields;
                    break;
                case "贷款质量统计表":
                    fieldArray = loanFields;
                    break;
                default:
                    fieldArray = assetFields;
            }
            
            String fieldName = fieldArray[fieldIndex % fieldArray.length];
            // 添加一些变化，避免重复
            if (fieldAdded > fieldArray.length) {
                fieldName = fieldName + "-" + (fieldAdded / fieldArray.length);
            }
            
            ExcelField field = new ExcelField();
            field.setFieldName(fieldName);
            field.setTableName(tableName);
            field.setDescription("测试字段-" + fieldAdded);
            
            fields.add(field);
            
            fieldAdded++;
            fieldIndex++;
            
            // 每添加10个字段，切换到下一个表
            if (fieldIndex % 10 == 0) {
                tableIndex++;
            }
        }
        
        return fields;
    }
} 