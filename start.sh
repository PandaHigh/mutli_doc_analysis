#!/bin/bash
cd /app/multi-doc-analysis
nohup java -jar multi-doc-analysis.jar --spring.profiles.active=prod --spring.config.location=file:/app/multi-doc-analysis/config/application-prod.properties > /app/multi-doc-analysis/logs/app.log 2>&1 &
echo $! > /app/multi-doc-analysis/app.pid
