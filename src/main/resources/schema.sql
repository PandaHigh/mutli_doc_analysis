-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS multidoc CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE multidoc;

-- 禁用外键检查
SET FOREIGN_KEY_CHECKS = 0;

-- 删除表（如果已存在）
DROP TABLE IF EXISTS field_rule;
DROP TABLE IF EXISTS field_relation;
DROP TABLE IF EXISTS excel_field;
DROP TABLE IF EXISTS word_chunk;
DROP TABLE IF EXISTS analysis_result;
DROP TABLE IF EXISTS analysis_task;
DROP TABLE IF EXISTS file_storage_config;
DROP TABLE IF EXISTS large_text_storage;
DROP TABLE IF EXISTS word_files;
DROP TABLE IF EXISTS excel_files;

-- 分析任务表
CREATE TABLE IF NOT EXISTS analysis_task (
    id CHAR(36) PRIMARY KEY,
    task_name VARCHAR(255) NOT NULL,
    created_time DATETIME NOT NULL,
    status VARCHAR(200) NOT NULL,
    last_completed_step VARCHAR(50),
    progress INT NOT NULL DEFAULT 0,
    word_file_paths JSON,
    excel_file_paths JSON,
    result_file_path VARCHAR(255),
    chunk_size INT,
    INDEX idx_task_status (status),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- 分析结果表
CREATE TABLE analysis_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id CHAR(36) NOT NULL,
    completed_time DATETIME NOT NULL,
    result_json LONGTEXT,
    summary TEXT,
    error_message TEXT,
    field_count INT DEFAULT 0,
    INDEX idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Word文档块表
CREATE TABLE word_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id CHAR(36) NOT NULL,
    source_file VARCHAR(255) NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    start_position INT NOT NULL,
    end_position INT NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    metadata VARCHAR(1000),
    INDEX idx_task_id (task_id),
    INDEX idx_chunk_index (chunk_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Excel字段表
CREATE TABLE IF NOT EXISTS excel_field (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id CHAR(36) NOT NULL,
    table_name VARCHAR(255) NOT NULL,
    field_name VARCHAR(255) NOT NULL,
    field_type VARCHAR(50),
    description VARCHAR(1000),
    FOREIGN KEY (task_id) REFERENCES analysis_task(id),
    UNIQUE KEY uk_task_table_field (task_id, table_name, field_name)
);

-- 字段关系表
CREATE TABLE field_relation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id CHAR(36) NOT NULL,
    source_field_id BIGINT NOT NULL,
    target_field_id BIGINT NOT NULL,
    relation_score DOUBLE NOT NULL,
    relation_description TEXT,
    relation_type VARCHAR(255),
    confidence DOUBLE,
    INDEX idx_task_id (task_id),
    INDEX idx_source_field (source_field_id),
    INDEX idx_target_field (target_field_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 修改FieldRule表
DROP TABLE IF EXISTS field_rule;
CREATE TABLE field_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id CHAR(36) NOT NULL,
    field_names TEXT NOT NULL,
    rule_type VARCHAR(20) NOT NULL,
    rule_content TEXT NOT NULL,
    confidence DOUBLE,
    INDEX idx_task_id (task_id),
    INDEX idx_rule_type (rule_type),
    FOREIGN KEY (task_id) REFERENCES analysis_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 文件存储配置表
CREATE TABLE file_storage_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    word_upload_path VARCHAR(255) NOT NULL,
    excel_upload_path VARCHAR(255) NOT NULL,
    max_file_size BIGINT NOT NULL DEFAULT 10485760, -- 默认10MB
    allowed_word_extensions VARCHAR(255) NOT NULL DEFAULT '.doc,.docx',
    allowed_excel_extensions VARCHAR(255) NOT NULL DEFAULT '.xls,.xlsx',
    chunk_size INT NOT NULL DEFAULT 5000, -- 文本分块大小
    chunk_overlap INT NOT NULL DEFAULT 500, -- 分块重叠大小
    max_categories INT NOT NULL DEFAULT 5 -- Excel字段最大分类数量
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- 创建 word_files 表
CREATE TABLE word_files (
    task_id CHAR(36) NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    INDEX idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建 excel_files 表
CREATE TABLE excel_files (
    task_id CHAR(36) NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    INDEX idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 启用外键检查
SET FOREIGN_KEY_CHECKS = 1;

-- 插入默认的文件存储配置
INSERT INTO file_storage_config (word_upload_path, excel_upload_path, chunk_size, chunk_overlap, max_categories)
VALUES ('./uploads/word', './uploads/excel', 5000, 500, 5); 