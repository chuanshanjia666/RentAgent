#!/usr/bin/env bash
# 本地开发启动脚本
# 默认启用大工 AI 网关（DeepSeek-V4-Flash-0731-W8A8），亦可在管理员控制台（UI）配置并持久化至数据库。
cd "$(dirname "$0")"
mvn -q spring-boot:run
