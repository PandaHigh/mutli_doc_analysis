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