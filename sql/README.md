# 数据库表结构更新指南

这些SQL脚本用于使数据库表结构与项目实体类保持一致。由于不需要保留现有数据，我们提供了一个简化的重建方案。

## 脚本说明

1. `clean_schema.sql` - 删除所有现有表并重新创建符合实体类的表结构
2. `backup_and_restore.sql` - 备份数据库中的当前数据（可选）

## 使用步骤

### 1. 备份数据库（可选）

如果以防万一想要备份当前数据库：

```bash
mysqldump -u [username] -p [database_name] > database_backup.sql
```

### 2. 执行表结构重建

直接执行干净的表结构重建脚本：

```bash
mysql -u [username] -p [database_name] < sql/clean_schema.sql
```

### 3. 验证表结构

验证表结构是否更新成功：

```sql
SHOW TABLES;
DESCRIBE analysis_task;
DESCRIBE analysis_result;
DESCRIBE excel_field;
DESCRIBE field_relation;
DESCRIBE field_rule;
DESCRIBE word_files;
DESCRIBE excel_files;
```

## 表结构变更说明

### 主要变更

1. 将 `analysis_task` 表的 ID 从 BIGINT 改为 CHAR(36)，使用UUID
2. 在 `excel_field` 表中添加了字段类型和类别字段:
   - `field_type` VARCHAR(255)
   - `category` VARCHAR(255) 
   - `rules` TEXT
3. 在 `field_rule` 表中添加了字段：
   - `field_name` VARCHAR(255)
   - `rule` TEXT
4. 在 `field_relation` 表中添加了字段：
   - `relation_description` TEXT
5. 修改字段类型：
   - `error_message` 从 VARCHAR(1000) 改为 TEXT
   - `related_text` 从 TEXT 改为 LONGTEXT

### 添加的外键约束

所有表都添加了外键约束，保证数据完整性：
- `analysis_result` -> `analysis_task`
- `word_chunk` -> `analysis_task`
- `excel_field` -> `analysis_task`
- `field_relation` -> `analysis_task`, `excel_field`(两个外键)
- `field_rule` -> `analysis_task`, `excel_field`
- `word_files` -> `analysis_task`
- `excel_files` -> `analysis_task`

## 开发流程调整

由于将 `analysis_task` 表的 ID 从 BIGINT 改为 CHAR(36)，相关代码已经进行了相应修改：

1. 实体类 `AnalysisTask` 中的ID已更改为String类型
2. 各种Repository中的泛型参数已更新
3. 所有Service和Controller中的方法签名已更新，使用String而非Long作为ID参数

这保证了系统能够正常运行，即使数据库结构发生了变化。 