package com.example.multidoc.service;

import com.example.multidoc.model.ExcelField;
import com.example.multidoc.model.WordSentence;
import com.example.multidoc.model.FieldSentenceRelation;
import com.example.multidoc.repository.FieldSentenceRelationRepository;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.persistence.EntityManager;

import java.io.IOException;
import java.util.*;

@Service
public class LuceneService {

    private static final Logger logger = LoggerFactory.getLogger(LuceneService.class);
    private static final int TOP_K_RESULTS = 5; // 每个字段返回的相关句子数量

    @Autowired
    private EntityManager entityManager;
    
    @Autowired
    private FieldSentenceRelationRepository relationRepository;

    /**
     * 为给定的Excel字段和Word句子计算相关性
     * @param fields Excel字段列表
     * @param sentences Word句子列表
     * @return 字段和相关句子的映射
     */
    @Transactional
    public Map<ExcelField, List<WordSentence>> calculateRelevance(List<ExcelField> fields, List<WordSentence> sentences) {
        Map<ExcelField, List<WordSentence>> result = new HashMap<>();
        
        try {
            // 创建Lucene索引
            Directory directory = createIndex(sentences);
            
            // 为每个字段搜索相关句子
            for (ExcelField field : fields) {
                // 重新加载实体以确保在事务中
                ExcelField managedField = entityManager.merge(field);
                List<WordSentence> relevantSentences = searchRelevantSentences(directory, managedField, sentences, TOP_K_RESULTS);
                
                // 创建字段和句子之间的关系记录
                for (WordSentence sentence : relevantSentences) {
                    WordSentence managedSentence = entityManager.merge(sentence);
                    
                    // 创建新的关系记录
                    FieldSentenceRelation relation = new FieldSentenceRelation();
                    relation.setFieldId(managedField.getId());
                    relation.setSentenceId(managedSentence.getId());
                    relation.setFieldName(managedField.getFieldName());
                    relation.setFieldType(managedField.getFieldType());
                    relation.setFieldDescription(managedField.getDescription());
                    relation.setSentenceContent(managedSentence.getContent());
                    relation.setSourceFile(managedSentence.getSourceFile());
                    relation.setRelevanceScore(1.0f); // 这里可以根据实际相关性评分设置
                    
                    relationRepository.save(relation);
                }
                
                result.put(managedField, relevantSentences);
            }
            
            // 关闭索引
            directory.close();
            
        } catch (Exception e) {
            logger.error("计算相关性时出错", e);
            throw new RuntimeException("计算相关性失败: " + e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * 创建Lucene索引
     */
    private Directory createIndex(List<WordSentence> sentences) throws IOException {
        Directory directory = new ByteBuffersDirectory();
        Analyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        
        // 配置索引写入器
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        config.setRAMBufferSizeMB(256.0);
        
        IndexWriter writer = new IndexWriter(directory, config);
        
        // 为每个句子创建文档
        for (WordSentence sentence : sentences) {
            Document doc = new Document();
            
            // 添加ID字段
            doc.add(new StringField("id", String.valueOf(sentence.getId()), Field.Store.YES));
            
            // 预处理内容，移除特殊字符
            String content = sentence.getContent();
            if (content != null) {
                // 移除所有特殊字符，只保留字母、数字、空格和基本标点
                content = content.replaceAll("[^\\p{L}\\p{N}\\p{P}\\s]", " ");
                // 移除多余的空格
                content = content.replaceAll("\\s+", " ").trim();
                
                // 添加处理后的内容
                doc.add(new TextField("content", content, Field.Store.YES));
            }
            
            writer.addDocument(doc);
        }
        
        writer.close();
        return directory;
    }
    
    /**
     * 搜索与给定字段相关的句子
     */
    private List<WordSentence> searchRelevantSentences(Directory directory, ExcelField field, List<WordSentence> sentences, int topK) 
            throws IOException, ParseException {
        
        Map<Long, WordSentence> sentenceMap = sentences.stream()
                .collect(HashMap::new, (map, sentence) -> map.put(sentence.getId(), sentence), HashMap::putAll);
        
        // 预处理字段名称和描述
        String fieldName = preprocessText(field.getFieldName());
        String description = preprocessText(field.getDescription());
        
        if ((fieldName == null || fieldName.isEmpty()) && (description == null || description.isEmpty())) {
            return new ArrayList<>();
        }
        
        // 执行查询
        IndexReader reader = DirectoryReader.open(directory);
        IndexSearcher searcher = new IndexSearcher(reader);
        Analyzer analyzer = new StandardAnalyzer();
        
        // 使用BooleanQuery来组合多个查询条件
        BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
        
        // 添加字段名称查询
        if (fieldName != null && !fieldName.isEmpty()) {
            Query nameQuery = new QueryParser("content", analyzer).parse(QueryParser.escape(fieldName));
            queryBuilder.add(nameQuery, BooleanClause.Occur.SHOULD);
        }
        
        // 添加描述查询
        if (description != null && !description.isEmpty()) {
            Query descQuery = new QueryParser("content", analyzer).parse(QueryParser.escape(description));
            queryBuilder.add(descQuery, BooleanClause.Occur.SHOULD);
        }
        
        // 执行查询
        TopDocs results = searcher.search(queryBuilder.build(), topK);
        
        // 转换结果
        List<WordSentence> relevantSentences = new ArrayList<>();
        for (ScoreDoc scoreDoc : results.scoreDocs) {
            Document doc = searcher.doc(scoreDoc.doc);
            String idStr = doc.get("id");
            if (idStr != null) {
                try {
                    Long id = Long.parseLong(idStr);
                    WordSentence sentence = sentenceMap.get(id);
                    if (sentence != null) {
                        relevantSentences.add(sentence);
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Invalid sentence ID format: {}", idStr);
                }
            }
        }
        
        reader.close();
        return relevantSentences;
    }
    
    /**
     * 预处理文本，移除或替换特殊字符
     */
    private String preprocessText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        // 1. 替换全角字符为半角字符
        text = text.replace('（', '(')
                   .replace('）', ')')
                   .replace('，', ',')
                   .replace('。', '.')
                   .replace('：', ':')
                   .replace('；', ';')
                   .replace('？', '?')
                   .replace('！', '!')
                   .replace('＋', '+')
                   .replace('－', '-')
                   .replace('×', '*')
                   .replace('÷', '/')
                   .replace('％', '%')
                   .replace('＜', '<')
                   .replace('＞', '>')
                   .replace('＝', '=')
                   .replace('＆', '&')
                   .replace('｜', '|')
                   .replace('～', '~')
                   .replace('＄', '$')
                   .replace('＃', '#')
                   .replace('＠', '@')
                   .replace('＾', '^')
                   .replace('｛', '{')
                   .replace('｝', '}')
                   .replace('［', '[')
                   .replace('］', ']')
                   .replace('＼', '\\')
                   .replace('．', '.');
        
        // 2. 移除或替换不支持的特殊字符
        text = text.replaceAll("[^\\p{L}\\p{N}\\p{P}\\s]", " ")  // 只保留字母、数字、标点和空格
                   .replaceAll("\\s+", " ")                       // 合并多个空格
                   .trim();                                       // 移除首尾空格
        
        // 3. 处理数学表达式中的特殊字符
        text = text.replace("*", "")
                   .replace("/", " ")
                   .replace("%", "percent")
                   .replace("(", " ")
                   .replace(")", " ")
                   .replace("[", " ")
                   .replace("]", " ")
                   .replace("{", " ")
                   .replace("}", " ")
                   .replace("×", "x")
                   .replace("÷", "divided by")
                   .replace("=", "equals")
                   .replace("+", "plus")
                   .replace("-", "minus");
        
        return text;
    }
} 