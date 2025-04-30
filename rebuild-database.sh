#!/bin/bash

# 数据库连接信息
DB_USER="root"
DB_PASS="769954602"
DB_NAME="multidoc"
SCHEMA_FILE="src/main/resources/schema.sql"

echo "开始重建数据库..."

# 如果密码为空，则不使用 -p 参数
if [ -z "$DB_PASS" ]; then
  MYSQL_CMD="mysql -u$DB_USER"
else
  MYSQL_CMD="mysql -u$DB_USER -p$DB_PASS"
fi

# 尝试删除并重建数据库
echo "删除旧数据库..."
$MYSQL_CMD -e "DROP DATABASE IF EXISTS $DB_NAME;"
if [ $? -ne 0 ]; then
  echo "错误：无法删除数据库。请检查数据库连接信息。"
  exit 1
fi

echo "创建新数据库..."
$MYSQL_CMD -e "CREATE DATABASE $DB_NAME CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
if [ $? -ne 0 ]; then
  echo "错误：无法创建数据库。请检查数据库连接信息。"
  exit 1
fi

# 执行 schema.sql 文件
echo "应用模式文件..."
$MYSQL_CMD $DB_NAME < $SCHEMA_FILE
if [ $? -ne 0 ]; then
  echo "错误：无法应用模式文件。请检查 $SCHEMA_FILE 文件是否存在且格式正确。"
  exit 1
fi

echo "数据库重建完成！"

# 创建上传目录
mkdir -p uploads/word uploads/excel exports
echo "创建上传目录完成！"

echo "数据库 '$DB_NAME' 已成功重建，架构已应用，项目就绪。" 