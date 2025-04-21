-- 完全重建数据库表结构，不保留数据
-- 禁用外键检查
SET FOREIGN_KEY_CHECKS = 0;

-- 删除现有表
DROP TABLE IF EXISTS word_files;
DROP TABLE IF EXISTS excel_files;
DROP TABLE IF EXISTS field_rule;
DROP TABLE IF EXISTS field_relation;
DROP TABLE IF EXISTS excel_field;
DROP TABLE IF EXISTS word_chunk;
DROP TABLE IF EXISTS analysis_result;
DROP TABLE IF EXISTS analysis_task;
DROP TABLE IF EXISTS file_storage_config;
DROP TABLE IF EXISTS large_text_storage;

-- 创建新表结构 - AnalysisTask
CREATE TABLE analysis_task (
    id CHAR(36) PRIMARY KEY,
    task_name VARCHAR(255) NOT NULL,
    created_time DATETIME NOT NULL,
    status VARCHAR(20) NOT NULL,
    result_file_path VARCHAR(255),
    chunk_size INT,
    INDEX idx_task_status (status),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建 AnalysisResult 表
CREATE TABLE analysis_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id CHAR(36) NOT NULL,
    completed_time DATETIME NOT NULL,
    result_json LONGTEXT,
    summary TEXT,
    error_message TEXT,
    INDEX idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建 WordChunk 表
CREATE TABLE word_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id CHAR(36) NOT NULL,
    source_file VARCHAR(255) NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    start_position INT NOT NULL,
    end_position INT NOT NULL,
    metadata VARCHAR(1000),
    INDEX idx_task_id (task_id),
    INDEX idx_chunk_index (chunk_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建 ExcelField 表
CREATE TABLE IF NOT EXISTS excel_field (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    table_name VARCHAR(255) NOT NULL,
    field_name VARCHAR(255) NOT NULL,
    field_type VARCHAR(50),
    description VARCHAR(1000),
    FOREIGN KEY (task_id) REFERENCES analysis_task(id),
    UNIQUE KEY uk_task_table_field (task_id, table_name, field_name)
);

-- 创建 FieldRelation 表
CREATE TABLE field_relation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id CHAR(36) NOT NULL,
    source_field_id BIGINT NOT NULL,
    target_field_id BIGINT NOT NULL,
    relation_score DOUBLE NOT NULL,
    relation_description TEXT,
    INDEX idx_task_id (task_id),
    INDEX idx_source_field (source_field_id),
    INDEX idx_target_field (target_field_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建 FieldRule 表
CREATE TABLE field_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id CHAR(36) NOT NULL,
    field_id BIGINT NOT NULL,
    field_name VARCHAR(255),
    rule_type VARCHAR(20) NOT NULL,
    rule_content TEXT NOT NULL,
    rule TEXT,
    priority INT NOT NULL DEFAULT 1,
    INDEX idx_task_id (task_id),
    INDEX idx_field_id (field_id),
    INDEX idx_rule_type (rule_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建辅助表
CREATE TABLE word_files (
    task_id CHAR(36) NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    INDEX idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE excel_files (
    task_id CHAR(36) NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    INDEX idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建配置表
CREATE TABLE file_storage_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    word_upload_path VARCHAR(255) NOT NULL,
    excel_upload_path VARCHAR(255) NOT NULL,
    max_file_size BIGINT NOT NULL DEFAULT 10485760,
    allowed_word_extensions VARCHAR(255) NOT NULL DEFAULT '.doc,.docx',
    allowed_excel_extensions VARCHAR(255) NOT NULL DEFAULT '.xls,.xlsx',
    chunk_size INT NOT NULL DEFAULT 500,
    chunk_overlap INT NOT NULL DEFAULT 100,
    max_categories INT NOT NULL DEFAULT 5
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建大文本存储表
CREATE TABLE large_text_storage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reference_type VARCHAR(50) NOT NULL,
    reference_id BIGINT NOT NULL,
    text_content LONGTEXT NOT NULL,
    created_time DATETIME NOT NULL,
    INDEX idx_reference (reference_type, reference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 添加外键约束
ALTER TABLE analysis_result
    ADD CONSTRAINT fk_result_task
    FOREIGN KEY (task_id)
    REFERENCES analysis_task (id)
    ON DELETE CASCADE;

ALTER TABLE word_chunk
    ADD CONSTRAINT fk_chunk_task
    FOREIGN KEY (task_id)
    REFERENCES analysis_task (id)
    ON DELETE CASCADE;

ALTER TABLE excel_field
    ADD CONSTRAINT fk_field_task
    FOREIGN KEY (task_id)
    REFERENCES analysis_task (id)
    ON DELETE CASCADE;

ALTER TABLE field_relation
    ADD CONSTRAINT fk_relation_task
    FOREIGN KEY (task_id)
    REFERENCES analysis_task (id)
    ON DELETE CASCADE;

ALTER TABLE field_relation
    ADD CONSTRAINT fk_relation_source_field
    FOREIGN KEY (source_field_id)
    REFERENCES excel_field (id)
    ON DELETE CASCADE;

ALTER TABLE field_relation
    ADD CONSTRAINT fk_relation_target_field
    FOREIGN KEY (target_field_id)
    REFERENCES excel_field (id)
    ON DELETE CASCADE;

ALTER TABLE field_rule
    ADD CONSTRAINT fk_rule_task
    FOREIGN KEY (task_id)
    REFERENCES analysis_task (id)
    ON DELETE CASCADE;

ALTER TABLE field_rule
    ADD CONSTRAINT fk_rule_field
    FOREIGN KEY (field_id)
    REFERENCES excel_field (id)
    ON DELETE CASCADE;

ALTER TABLE word_files
    ADD CONSTRAINT fk_word_files_task
    FOREIGN KEY (task_id)
    REFERENCES analysis_task (id)
    ON DELETE CASCADE;

ALTER TABLE excel_files
    ADD CONSTRAINT fk_excel_files_task
    FOREIGN KEY (task_id)
    REFERENCES analysis_task (id)
    ON DELETE CASCADE;

-- 初始化文件存储配置
INSERT INTO file_storage_config (
    word_upload_path, 
    excel_upload_path, 
    max_file_size,
    allowed_word_extensions,
    allowed_excel_extensions,
    chunk_size,
    chunk_overlap,
    max_categories
) VALUES (
    './uploads/word',
    './uploads/excel',
    10485760,
    '.doc,.docx',
    '.xls,.xlsx',
    500,
    100,
    5
);

-- 启用外键检查
SET FOREIGN_KEY_CHECKS = 1; 