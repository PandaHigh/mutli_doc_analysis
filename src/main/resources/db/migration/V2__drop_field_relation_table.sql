-- Drop foreign key constraints first
ALTER TABLE field_relation DROP CONSTRAINT IF EXISTS fk_field_relation_task;
ALTER TABLE field_relation DROP CONSTRAINT IF EXISTS fk_field_relation_source_field;
ALTER TABLE field_relation DROP CONSTRAINT IF EXISTS fk_field_relation_target_field;

-- Drop the table
DROP TABLE IF EXISTS field_relation; 