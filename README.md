# 多文档关联分析系统

一个基于大模型的超大文本分析系统，用于分析Word文档与Excel表格之间的关联，提取规则，而无需向量数据库。

## 项目概述

该系统利用大模型能力完成对超大文本word与表格excel之间的关联分析，其中word文本是用于指导excel填表的背景知识与指引，excel是报表模板。系统支持上传多个Word文档和多个Excel表格一起分析。

主要功能包括：

1. **提取Excel字段结构**：将Excel模板转换为markdown，使用LLM提取字段名称和描述
2. **处理Word文档**：
   - 以句子为单位切分文档
   - 保存句子及其源文件信息到数据库
   - 使用Apache Lucene评估Excel字段与Word句子之间的相关性
3. **字段分类与关联分析**：
   - 使用大模型将Excel字段分类为N类（默认15类）
   - 对每个分类，提取所有字段相关性排名最高的文本句子
   - 对单个分类中的所有文本进行去重处理
4. **规则提取与分析**：
   - 分析提取显式规则和隐含规则
   - 识别字段间的依赖关系
   - 提取数据格式和数值范围限制
5. **编译和汇总规则**：整理规则，解决冲突，生成结构化数据

## 技术栈

- **后端**：Java 17, Spring Boot 3.2.x, Spring AI
- **数据库**：MySQL
- **文档处理**：Apache POI, Docx4j
- **文本搜索**：Apache Lucene
- **前端**：HTML, CSS, JavaScript, Bootstrap 5
- **模板引擎**：Thymeleaf

## 安装要求

- Java 17+
- Maven 3.6+
- MySQL 8.0+
- 有效的OpenAI API密钥

## 快速开始

1. 克隆项目

```bash
git clone https://github.com/yourusername/multi-doc-analysis.git
cd multi-doc-analysis
```

2. 配置数据库和OpenAI API

编辑`src/main/resources/application.properties`文件，设置数据库连接信息和OpenAI API密钥：

```properties
# 数据库配置
spring.datasource.url=jdbc:mysql://localhost:3306/multidoc?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8
spring.datasource.username=你的用户名
spring.datasource.password=你的密码

# OpenAI配置
spring.ai.openai.api-key=你的OpenAI-API密钥
```

3. 运行项目

```bash
mvn spring-boot:run
```

4. 访问系统

打开浏览器，访问 http://localhost:8080

## 使用流程

1. 访问系统首页，点击"新建任务"
2. 输入任务名称，上传Word文档和Excel表格
3. 点击"开始分析"，系统将在后台处理文档
4. 处理完成后，查看任务详情页面获取分析结果

## 主要组件

- **文档处理服务**：负责解析Word和Excel文档
- **分析服务**：协调整个分析流程，调用AI模型
- **AI服务**：与大模型交互，分析文本关系和提取规则
- **Web界面**：提供直观的用户交互界面

## 注意事项

- 文档处理可能会消耗大量资源，尤其是大型文档
- AI分析可能需要一定时间，系统会在后台处理并自动刷新结果
- 当前支持的文档格式：.docx (Word)，.xlsx (Excel)

## 许可证

[MIT](LICENSE) 