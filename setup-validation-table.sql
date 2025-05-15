-- 创建规则验证结果表
CREATE TABLE IF NOT EXISTS rule_validation_results (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NULL,
    status VARCHAR(20) NOT NULL,
    progress INT DEFAULT 0,
    validated_rules LONGTEXT NULL,
    error_message LONGTEXT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4; 