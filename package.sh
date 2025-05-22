#!/bin/bash

# 定义颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

# 定义变量
APP_NAME=multi-doc-analysis
JAR_FILE=./target/${APP_NAME}-0.0.1-SNAPSHOT.jar
RELEASE_DIR=./release
VERSION=$(date +%Y%m%d%H%M)

# 显示提示
echo -e "${GREEN}开始打包应用程序...${NC}"

# 重新构建项目
echo -e "${YELLOW}构建项目...${NC}"
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

# 创建发布目录
echo -e "${YELLOW}创建发布目录...${NC}"
mkdir -p ${RELEASE_DIR}/${VERSION}/{bin,config,sql,logs,uploads/{word,excel}}

# 复制JAR文件
echo -e "${YELLOW}复制JAR文件...${NC}"
cp ${JAR_FILE} ${RELEASE_DIR}/${VERSION}/bin/${APP_NAME}.jar

# 复制配置文件
echo -e "${YELLOW}复制配置文件...${NC}"
if [ -f "./src/main/resources/application-prod.properties" ]; then
    cp ./src/main/resources/application-prod.properties ${RELEASE_DIR}/${VERSION}/config/
fi
cp ./src/main/resources/application.properties ${RELEASE_DIR}/${VERSION}/config/

# 复制SQL文件
echo -e "${YELLOW}复制SQL文件...${NC}"
if [ -d "./sql" ]; then
    cp -r ./sql/* ${RELEASE_DIR}/${VERSION}/sql/
fi

# 复制脚本文件
echo -e "${YELLOW}创建启动脚本...${NC}"
cat > ${RELEASE_DIR}/${VERSION}/bin/start.sh << EOF
#!/bin/bash
cd \$(dirname \$0)/..
mkdir -p logs uploads/word uploads/excel
nohup java -jar bin/${APP_NAME}.jar --spring.config.location=file:./config/application.properties > logs/app.log 2>&1 &
echo \$! > app.pid
echo "应用程序已启动，PID: \$(cat app.pid)"
EOF
chmod +x ${RELEASE_DIR}/${VERSION}/bin/start.sh

# 创建停止脚本
echo -e "${YELLOW}创建停止脚本...${NC}"
cat > ${RELEASE_DIR}/${VERSION}/bin/stop.sh << EOF
#!/bin/bash
cd \$(dirname \$0)/..
if [ -f app.pid ]; then
    PID=\$(cat app.pid)
    if ps -p \$PID > /dev/null; then
        echo "正在停止应用程序，PID: \$PID"
        kill \$PID
        sleep 5
        if ps -p \$PID > /dev/null; then
            echo "应用程序未能正常停止，正在强制终止..."
            kill -9 \$PID
        fi
        rm app.pid
        echo "应用程序已停止"
    else
        echo "应用程序未运行"
        rm app.pid
    fi
else
    echo "PID文件不存在，应用程序可能未运行"
fi
EOF
chmod +x ${RELEASE_DIR}/${VERSION}/bin/stop.sh

# 创建README文件
echo -e "${YELLOW}创建README文件...${NC}"
cat > ${RELEASE_DIR}/${VERSION}/README.md << EOF
# 多文档关联分析系统

版本: ${VERSION}

## 目录结构

- bin/: 启动和停止脚本
- config/: 配置文件
- logs/: 日志文件
- sql/: 数据库脚本
- uploads/: 上传文件目录

## 启动方法

\`\`\`bash
cd bin
./start.sh
\`\`\`

## 停止方法

\`\`\`bash
cd bin
./stop.sh
\`\`\`

## 配置文件

主配置文件位于 \`config/application.properties\`，可根据需要修改。

## 数据库配置

数据库脚本位于 \`sql/\` 目录下，请根据需要执行。
EOF

# 创建归档文件
echo -e "${YELLOW}创建归档文件...${NC}"
cd ${RELEASE_DIR}
tar -czf ${APP_NAME}-${VERSION}.tar.gz ${VERSION}
cd ..

echo -e "${GREEN}打包完成！${NC}"
echo -e "${GREEN}发布包: ${RELEASE_DIR}/${APP_NAME}-${VERSION}.tar.gz${NC}"
echo -e "${GREEN}发布目录: ${RELEASE_DIR}/${VERSION}${NC}" 