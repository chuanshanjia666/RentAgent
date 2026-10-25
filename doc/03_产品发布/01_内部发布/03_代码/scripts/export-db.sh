#!/usr/bin/env bash
# RentAgent 数据库导出脚本
# 用途：把 rentagent 库（结构 + 数据）导出为 .sql 交付物，满足提交要求「mysql 数据导出」。
# 前置：MySQL 已运行且种子数据已写入（docker compose up -d mysql 后，后端至少启动一次）。
set -euo pipefail

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-rentagent}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-rentagent123}"
DB_NAME="${DB_NAME:-rentagent}"

# 优先使用 docker 容器内的 mysqldump，回退到本机 mysqldump
STAMP=$(date +%Y%m%d-%H%M%S)
OUT="rentagent-data-${STAMP}.sql"

if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -q 'rentagent-mysql'; then
  echo "[1/3] 通过 docker 容器 rentagent-mysql 导出..."
  docker exec rentagent-mysql mysqldump \
    -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" \
    --single-transaction --routines --triggers --set-gtid-purged=OFF \
    "${DB_NAME}" > "${OUT}"
else
  echo "[1/3] 通过本机 mysqldump 导出..."
  mysqldump \
    -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" \
    -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" \
    --single-transaction --routines --triggers --set-gtid-purged=OFF \
    "${DB_NAME}" > "${OUT}"
fi

echo "[2/3] 导出完成：${OUT}"
echo "[3/3] 大小：$(du -h "${OUT}" | cut -f1)，行数：$(wc -l < "${OUT}")"

# 校验：应包含 19 张表
TABLES=$(grep -c 'CREATE TABLE' "${OUT}")
echo "       表数量：${TABLES}（预期 19）"
