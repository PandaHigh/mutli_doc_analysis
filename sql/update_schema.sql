-- 更新数据库表结构，使其与实体类保持一致
-- 禁用外键检查
SET FOREIGN_KEY_CHECKS = 0;

-- 备份现有数据（如果需要）
-- 创建临时表来保存可能需要的数据
CREATE TABLE IF NOT EXISTS temp_analysis_task SELECT * FROM analysis_task;
CREATE TABLE IF NOT EXISTS temp_analysis_result SELECT * FROM analysis_result;
CREATE TABLE IF NOT EXISTS temp_excel_field SELECT * FROM excel_field;
CREATE TABLE IF NOT EXISTS temp_field_relation SELECT * FROM field_relation;
CREATE TABLE IF NOT EXISTS temp_field_rule SELECT * FROM field_rule;
CREATE TABLE IF NOT EXISTS temp_word_files SELECT * FROM word_files;
CREATE TABLE IF NOT EXISTS temp_excel_files SELECT * FROM excel_files;

-- 修改AnalysisTask表
-- 首先，删除旧表
DROP TABLE IF EXISTS analysis_task;
-- 重新创建符合实体类的表
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

-- 修改AnalysisResult表
DROP TABLE IF EXISTS analysis_result;
CREATE TABLE analysis_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id CHAR(36) NOT NULL,
    completed_time DATETIME NOT NULL,
    result_json LONGTEXT,
    summary TEXT,
    error_message TEXT,
    INDEX idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 修改ExcelField表
DROP TABLE IF EXISTS excel_field;
CREATE TABLE excel_field (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    table_name VARCHAR(255) NOT NULL,
    field_name VARCHAR(255) NOT NULL,
    field_type VARCHAR(50),
    category VARCHAR(255),
    description VARCHAR(1000),
    rules TEXT,
    INDEX idx_task_id (task_id),
    INDEX idx_field_name (field_name),
    UNIQUE KEY uk_task_table_field (task_id, table_name, field_name),
    FOREIGN KEY (task_id) REFERENCES analysis_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 修改FieldRelation表
DROP TABLE IF EXISTS field_relation;
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

-- 修改辅助表结构
DROP TABLE IF EXISTS word_files;
CREATE TABLE word_files (
    task_id CHAR(36) NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    INDEX idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS excel_files;
CREATE TABLE excel_files (
    task_id CHAR(36) NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    INDEX idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 尝试迁移数据（如果数据格式兼容）
-- 注意：这里可能需要根据实际情况修改
INSERT INTO analysis_task (id, task_name, created_time, status, result_file_path, chunk_size)
SELECT UUID(), task_name, created_time, status, result_file_path, NULL
FROM temp_analysis_task;

-- 获取新旧ID的映射关系
CREATE TEMPORARY TABLE id_mapping (
    old_id BIGINT,
    new_id CHAR(36)
);

INSERT INTO id_mapping (old_id, new_id)
SELECT t1.id, t2.id
FROM temp_analysis_task t1
JOIN analysis_task t2 ON t1.task_name = t2.task_name AND t1.created_time = t2.created_time;

-- 恢复辅助表数据
INSERT INTO word_files (task_id, file_path)
SELECT m.new_id, w.file_path
FROM temp_word_files w
JOIN id_mapping m ON w.task_id = m.old_id;

INSERT INTO excel_files (task_id, file_path)
SELECT m.new_id, e.file_path
FROM temp_excel_files e
JOIN id_mapping m ON e.task_id = m.old_id;

-- 根据需要继续恢复其他表的数据
-- 这里省略具体的恢复步骤，因为需要根据实际情况调整

-- 删除临时表
DROP TABLE IF EXISTS temp_analysis_task;
DROP TABLE IF EXISTS temp_analysis_result;
DROP TABLE IF EXISTS temp_excel_field;
DROP TABLE IF EXISTS temp_field_relation;
DROP TABLE IF EXISTS temp_field_rule;
DROP TABLE IF EXISTS temp_word_files;
DROP TABLE IF EXISTS temp_excel_files;
DROP TABLE IF EXISTS id_mapping;

-- 启用外键检查
SET FOREIGN_KEY_CHECKS = 1; 