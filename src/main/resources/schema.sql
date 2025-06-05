-- 删除已存在的数据库并重新创建
DROP DATABASE IF EXISTS multidoc;
CREATE DATABASE multidoc CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE multidoc;

-- 分析任务表
CREATE TABLE analysis_tasks (
    id VARCHAR(36) PRIMARY KEY,
    task_name VARCHAR(255) NOT NULL,
    created_time TIMESTAMP NOT NULL,
    completed_time TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    progress INT DEFAULT 0,
    last_completed_step VARCHAR(50) DEFAULT 'start',
    chunk_size INT DEFAULT 5000,
    word_file_paths TEXT,
    excel_file_paths TEXT
);

-- Excel字段表
CREATE TABLE excel_fields (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL,
    table_name VARCHAR(255),
    field_name VARCHAR(255) NOT NULL,
    field_type VARCHAR(50),
    description TEXT,
    category VARCHAR(100),
    UNIQUE KEY unique_field (task_id, table_name, field_name)
);

-- Word句子表
CREATE TABLE word_sentences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL,
    sentence_index INT NOT NULL,
    content TEXT NOT NULL,
    source_file VARCHAR(255),
    start_position INT NOT NULL,
    end_position INT NOT NULL
);

-- 字段与句子关联表
CREATE TABLE field_sentence_relation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    field_id BIGINT NOT NULL,
    sentence_id BIGINT NOT NULL,
    field_name VARCHAR(255) NOT NULL,
    field_type VARCHAR(50),
    field_description TEXT,
    sentence_content TEXT NOT NULL,
    source_file VARCHAR(255),
    relevance_score FLOAT DEFAULT 0.0,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 字段规则表
CREATE TABLE field_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL,
    field_names TEXT NOT NULL,
    rule_type VARCHAR(20) NOT NULL,
    rule_content TEXT NOT NULL,
    confidence FLOAT,
    category VARCHAR(255),
    is_cross_table BOOLEAN DEFAULT FALSE,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 分析结果表
CREATE TABLE analysis_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL,
    completed_time TIMESTAMP,
    result_text LONGTEXT,
    summary_text LONGTEXT,
    field_count INTEGER,
    error_message LONGTEXT
);


-- 任务日志表
CREATE TABLE task_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL,
    log_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    log_level VARCHAR(50) NOT NULL,
    message TEXT NOT NULL
);

-- 文件存储配置表
CREATE TABLE file_storage_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(50) NOT NULL,
    config_value VARCHAR(255) NOT NULL,
    UNIQUE KEY unique_config_key (config_key)
);


-- 插入默认配置
INSERT INTO file_storage_configs (config_key, config_value) VALUES 
('word_upload_path', 'uploads/word'),
('excel_upload_path', 'uploads/excel'),
('result_export_path', 'exports'); 

-- 创建规则验证结果表
CREATE TABLE IF NOT EXISTS rule_validation_results (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    progress INT DEFAULT 0,
    validated_rules LONGTEXT,
    error_message LONGTEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 创建文档报送范围表
CREATE TABLE document_scopes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    scope_content TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 外键约束
    CONSTRAINT fk_document_scopes_task FOREIGN KEY (task_id) REFERENCES analysis_tasks(id) ON DELETE CASCADE
); 