-- 备份所有表数据
CREATE TABLE IF NOT EXISTS backup_analysis_task SELECT * FROM analysis_task;
CREATE TABLE IF NOT EXISTS backup_analysis_result SELECT * FROM analysis_result;
CREATE TABLE IF NOT EXISTS backup_excel_field SELECT * FROM excel_field;
CREATE TABLE IF NOT EXISTS backup_field_relation SELECT * FROM field_relation;
CREATE TABLE IF NOT EXISTS backup_field_rule SELECT * FROM field_rule;
CREATE TABLE IF NOT EXISTS backup_word_files SELECT * FROM word_files;
CREATE TABLE IF NOT EXISTS backup_excel_files SELECT * FROM excel_files;

-- 查看备份数据
SELECT COUNT(*) AS task_count FROM backup_analysis_task;
SELECT COUNT(*) AS result_count FROM backup_analysis_result;
SELECT COUNT(*) AS field_count FROM backup_excel_field;
SELECT COUNT(*) AS relation_count FROM backup_field_relation;
SELECT COUNT(*) AS rule_count FROM backup_field_rule;
SELECT COUNT(*) AS word_files_count FROM backup_word_files;
SELECT COUNT(*) AS excel_files_count FROM backup_excel_files;

-- 如果需要恢复备份：
/*
INSERT INTO analysis_task SELECT * FROM backup_analysis_task;
INSERT INTO analysis_result SELECT * FROM backup_analysis_result;
INSERT INTO excel_field SELECT * FROM backup_excel_field;
INSERT INTO field_relation SELECT * FROM backup_field_relation;
INSERT INTO field_rule SELECT * FROM backup_field_rule;
INSERT INTO word_files SELECT * FROM backup_word_files;
INSERT INTO excel_files SELECT * FROM backup_excel_files;
*/ 