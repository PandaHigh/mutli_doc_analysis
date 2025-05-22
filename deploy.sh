#!/bin/bash

# 定义颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

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
echo -e "${GREEN}开始部署到远程服务器 ${SERVER_IP}...${NC}"

# 检查是否可以连接服务器
echo -e "${YELLOW}检查服务器连接状态...${NC}"
if ! ssh -q -o BatchMode=yes -o ConnectTimeout=5 ${SERVER_USER}@${SERVER_IP} exit; then
    echo -e "${RED}无法连接到服务器 ${SERVER_IP}，请检查网络连接和SSH配置${NC}"
    exit 1
fi
echo -e "${GREEN}服务器连接正常${NC}"

# 重新构建项目
echo -e "${YELLOW}重新构建项目...${NC}"
if ! ./mvnw clean package -DskipTests; then
    echo -e "${RED}构建失败，请检查错误信息${NC}"
    exit 1
fi

# 检查构建是否成功
if [ ! -f "${JAR_FILE}" ]; then
    echo -e "${RED}构建失败，JAR文件不存在: ${JAR_FILE}${NC}"
    exit 1
fi
echo -e "${GREEN}构建成功: $(ls -la ${JAR_FILE})${NC}"

# 创建必要的目录结构
echo -e "${YELLOW}在远程服务器上创建必要的目录...${NC}"
ssh ${SERVER_USER}@${SERVER_IP} "mkdir -p ${REMOTE_DIR} ${BACKUP_DIR} ${LOG_DIR} ${UPLOAD_DIR}/word ${UPLOAD_DIR}/excel ${CONFIG_DIR}"

# 检查配置文件
CONFIG_FILE="./src/main/resources/application-prod.properties"
if [ ! -f "${CONFIG_FILE}" ]; then
    echo -e "${RED}配置文件不存在: ${CONFIG_FILE}${NC}"
    echo -e "${YELLOW}使用默认配置文件...${NC}"
    CONFIG_FILE="./src/main/resources/application.properties"
fi

# 复制配置文件
echo -e "${YELLOW}复制生产环境配置文件...${NC}"
scp ${CONFIG_FILE} ${SERVER_USER}@${SERVER_IP}:${CONFIG_DIR}/application-prod.properties

# 复制SQL脚本
echo -e "${YELLOW}复制SQL脚本...${NC}"
if [ -d "./sql" ]; then
    scp -r ./sql ${SERVER_USER}@${SERVER_IP}:${REMOTE_DIR}/
else
    echo -e "${YELLOW}SQL目录不存在，跳过...${NC}"
fi

# 上传数据库初始化脚本
echo -e "${YELLOW}上传数据库初始化脚本...${NC}"
if [ -f "./rebuild-database.sh" ]; then
    scp ./rebuild-database.sh ${SERVER_USER}@${SERVER_IP}:${REMOTE_DIR}/
    ssh ${SERVER_USER}@${SERVER_IP} "chmod +x ${REMOTE_DIR}/rebuild-database.sh"
else
    echo -e "${YELLOW}数据库初始化脚本不存在，跳过...${NC}"
fi

# 检查远程服务器上是否有正在运行的应用程序
echo -e "${YELLOW}检查远程服务器上是否有正在运行的应用程序...${NC}"
RUNNING_PID=$(ssh ${SERVER_USER}@${SERVER_IP} "ps -ef | grep ${APP_NAME} | grep -v grep | awk '{print \$2}'")
if [ ! -z "${RUNNING_PID}" ]; then
    echo -e "${YELLOW}停止远程服务器上正在运行的应用程序，PID: ${RUNNING_PID}${NC}"
    ssh ${SERVER_USER}@${SERVER_IP} "kill -15 ${RUNNING_PID} || kill -9 ${RUNNING_PID}"
    echo -e "${YELLOW}等待应用程序停止...${NC}"
    sleep 10
    
    # 再次检查进程是否已经停止
    RUNNING_PID=$(ssh ${SERVER_USER}@${SERVER_IP} "ps -ef | grep ${APP_NAME} | grep -v grep | awk '{print \$2}'")
    if [ ! -z "${RUNNING_PID}" ]; then
        echo -e "${RED}无法停止应用程序，PID: ${RUNNING_PID}${NC}"
        echo -e "${RED}尝试强制终止...${NC}"
        ssh ${SERVER_USER}@${SERVER_IP} "kill -9 ${RUNNING_PID}"
        sleep 5
    fi
fi

# 备份当前的JAR文件（如果存在）
echo -e "${YELLOW}备份当前的JAR文件（如果存在）...${NC}"
TIMESTAMP=$(date +%Y%m%d%H%M%S)
ssh ${SERVER_USER}@${SERVER_IP} "if [ -f ${REMOTE_DIR}/${APP_NAME}.jar ]; then cp ${REMOTE_DIR}/${APP_NAME}.jar ${BACKUP_DIR}/${APP_NAME}-${TIMESTAMP}.jar; fi"

# 上传新的JAR文件
echo -e "${YELLOW}上传新的JAR文件...${NC}"
scp ${JAR_FILE} ${SERVER_USER}@${SERVER_IP}:${REMOTE_DIR}/${APP_NAME}.jar

# 创建启动脚本
echo -e "${YELLOW}创建启动脚本...${NC}"
cat > start.sh << EOF
#!/bin/bash
cd ${REMOTE_DIR}
nohup java -jar ${APP_NAME}.jar --spring.profiles.active=prod --spring.config.location=file:${CONFIG_DIR}/application-prod.properties > ${LOG_DIR}/app.log 2>&1 &
echo \$! > ${REMOTE_DIR}/app.pid
EOF

# 上传启动脚本
echo -e "${YELLOW}上传启动脚本...${NC}"
chmod +x start.sh
scp start.sh ${SERVER_USER}@${SERVER_IP}:${REMOTE_DIR}/

# 启动应用程序
echo -e "${YELLOW}启动应用程序...${NC}"
ssh ${SERVER_USER}@${SERVER_IP} "cd ${REMOTE_DIR} && chmod +x start.sh && ./start.sh"

# 检查应用程序是否成功启动
echo -e "${YELLOW}等待应用程序启动...${NC}"
sleep 15
RUNNING_PID=$(ssh ${SERVER_USER}@${SERVER_IP} "ps -ef | grep ${APP_NAME} | grep -v grep | awk '{print \$2}'")
if [ ! -z "${RUNNING_PID}" ]; then
    echo -e "${GREEN}应用程序成功启动，PID: ${RUNNING_PID}${NC}"
    echo -e "${GREEN}应用程序访问地址: http://${SERVER_IP}:8090${NC}"
    
    # 查看日志头部，确认启动状态
    echo -e "${YELLOW}查看应用程序启动日志:${NC}"
    ssh ${SERVER_USER}@${SERVER_IP} "tail -n 20 ${LOG_DIR}/app.log"
else
    echo -e "${RED}应用程序启动失败，请检查日志文件${NC}"
    echo -e "${YELLOW}最近的日志内容:${NC}"
    ssh ${SERVER_USER}@${SERVER_IP} "tail -n 50 ${LOG_DIR}/app.log"
    exit 1
fi

echo -e "${GREEN}部署完成！${NC}" 