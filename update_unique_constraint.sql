-- 先删除旧的唯一约束
ALTER TABLE multidoc.excel_fields 
DROP INDEX unique_field;

-- 创建新的包含category字段的唯一约束
ALTER TABLE multidoc.excel_fields 
ADD CONSTRAINT unique_field UNIQUE (task_id, table_name, field_name, category); 