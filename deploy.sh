#!/bin/bash

# 定义变量
SERVER_IP=110.41.138.56
SERVER_USER=root
APP_NAME=multi-doc-analysis
JAR_FILE=./target/${APP_NAME}-0.0.1-SNAPSHOT.jar
REMOTE_DIR=/app/${APP_NAME}
BACKUP_DIR=${REMOTE_DIR}/backup
LOG_DIR=${REMOTE_DIR}/logs
UPLOAD_DIR=${REMOTE_DIR}/uploads
CONFIG_DIR=${REMOTE_DIR}/config

# 显示提示
echo "开始部署到远程服务器 ${SERVER_IP}..."

# 重新构建项目
echo "重新构建项目..."
./mvnw clean package -DskipTests

# 检查构建是否成功
if [ ! -f "${JAR_FILE}" ]; then
    echo "构建失败，JAR文件不存在: ${JAR_FILE}"
    exit 1
fi

# 创建必要的目录结构
echo "在远程服务器上创建必要的目录..."
ssh ${SERVER_USER}@${SERVER_IP} "mkdir -p ${REMOTE_DIR} ${BACKUP_DIR} ${LOG_DIR} ${UPLOAD_DIR}/word ${UPLOAD_DIR}/excel ${CONFIG_DIR}"

# 复制配置文件
echo "复制生产环境配置文件..."
scp ./src/main/resources/application-prod.properties ${SERVER_USER}@${SERVER_IP}:${CONFIG_DIR}/

# 复制SQL脚本
echo "复制SQL脚本..."
scp -r ./sql ${SERVER_USER}@${SERVER_IP}:${REMOTE_DIR}/

# 上传数据库初始化脚本
echo "上传数据库初始化脚本..."
scp ./rebuild-database.sh ${SERVER_USER}@${SERVER_IP}:${REMOTE_DIR}/

# 检查远程服务器上是否有正在运行的应用程序
echo "检查远程服务器上是否有正在运行的应用程序..."
RUNNING_PID=$(ssh ${SERVER_USER}@${SERVER_IP} "ps -ef | grep ${APP_NAME} | grep -v grep | awk '{print \$2}'")
if [ ! -z "${RUNNING_PID}" ]; then
    echo "停止远程服务器上正在运行的应用程序，PID: ${RUNNING_PID}"
    ssh ${SERVER_USER}@${SERVER_IP} "kill -15 ${RUNNING_PID} || kill -9 ${RUNNING_PID}"
    echo "等待应用程序停止..."
    sleep 5
fi

# 备份当前的JAR文件（如果存在）
echo "备份当前的JAR文件（如果存在）..."
TIMESTAMP=$(date +%Y%m%d%H%M%S)
ssh ${SERVER_USER}@${SERVER_IP} "if [ -f ${REMOTE_DIR}/${APP_NAME}.jar ]; then cp ${REMOTE_DIR}/${APP_NAME}.jar ${BACKUP_DIR}/${APP_NAME}-${TIMESTAMP}.jar; fi"

# 上传新的JAR文件
echo "上传新的JAR文件..."
scp ${JAR_FILE} ${SERVER_USER}@${SERVER_IP}:${REMOTE_DIR}/${APP_NAME}.jar

# 创建启动脚本
echo "创建启动脚本..."
cat > start.sh << EOF
#!/bin/bash
cd ${REMOTE_DIR}
nohup java -jar ${APP_NAME}.jar --spring.profiles.active=prod --spring.config.location=file:${CONFIG_DIR}/application-prod.properties > ${LOG_DIR}/app.log 2>&1 &
echo \$! > ${REMOTE_DIR}/app.pid
EOF

# 上传启动脚本
echo "上传启动脚本..."
chmod +x start.sh
scp start.sh ${SERVER_USER}@${SERVER_IP}:${REMOTE_DIR}/

# 启动应用程序
echo "启动应用程序..."
ssh ${SERVER_USER}@${SERVER_IP} "cd ${REMOTE_DIR} && chmod +x start.sh && ./start.sh"

# 检查应用程序是否成功启动
echo "等待应用程序启动..."
sleep 10
RUNNING_PID=$(ssh ${SERVER_USER}@${SERVER_IP} "ps -ef | grep ${APP_NAME} | grep -v grep | awk '{print \$2}'")
if [ ! -z "${RUNNING_PID}" ]; then
    echo "应用程序成功启动，PID: ${RUNNING_PID}"
    echo "应用程序访问地址: http://${SERVER_IP}:8090"
else
    echo "应用程序启动失败，请检查日志文件: ${LOG_DIR}/app.log"
    ssh ${SERVER_USER}@${SERVER_IP} "tail -n 50 ${LOG_DIR}/app.log"
fi

echo "部署完成！" 