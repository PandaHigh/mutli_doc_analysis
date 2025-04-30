@echo off
setlocal

REM 数据库连接信息
set DB_USER=root
set DB_PASS=769954602
set DB_NAME=multidoc
set SCHEMA_FILE=src\main\resources\schema.sql

echo 开始重建数据库...

REM 如果密码为空，则不使用 -p 参数
if "%DB_PASS%"=="" (
  set MYSQL_CMD=mysql -u%DB_USER%
) else (
  set MYSQL_CMD=mysql -u%DB_USER% -p%DB_PASS%
)

echo 删除旧数据库...
%MYSQL_CMD% -e "DROP DATABASE IF EXISTS %DB_NAME%;"
if %ERRORLEVEL% NEQ 0 (
  echo 错误：无法删除数据库。请检查数据库连接信息。
  exit /b 1
)

echo 创建新数据库...
%MYSQL_CMD% -e "CREATE DATABASE %DB_NAME% CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
if %ERRORLEVEL% NEQ 0 (
  echo 错误：无法创建数据库。请检查数据库连接信息。
  exit /b 1
)

echo 应用模式文件...
%MYSQL_CMD% %DB_NAME% < %SCHEMA_FILE%
if %ERRORLEVEL% NEQ 0 (
  echo 错误：无法应用模式文件。请检查 %SCHEMA_FILE% 文件是否存在且格式正确。
  exit /b 1
)

echo 数据库重建完成！

REM 创建上传目录
mkdir uploads\word uploads\excel exports 2>nul
echo 创建上传目录完成！

echo 数据库 '%DB_NAME%' 已成功重建，架构已应用，项目就绪。

endlocal 