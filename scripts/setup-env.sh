#!/usr/bin/env bash
# ==============================================================================
# RentAgent 全环境一键搭建与管理脚本
#
# 功能说明：
#   1. 启动基础设施：MySQL 8 (3306) 与 Redis 7 (6379) 容器，等待健康就绪
#   2. 编译并启动后端：Spring Boot 3 (8080)，遵循防首启竞态就绪探测
#   3. 启动前端服务：React 18 + Vite 开发服务器 (5173)
#   4. 提供 status / stop / infra / backend / frontend / logs 等子命令
#
# 用法：
#   ./scripts/setup-env.sh             # 一键搭建并启动所有运行环境 (默认)
#   ./scripts/setup-env.sh infra       # 仅启动并就绪 MySQL + Redis 基础设施
#   ./scripts/setup-env.sh backend     # 仅编译并启动后端服务
#   ./scripts/setup-env.sh frontend    # 仅启动前端服务
#   ./scripts/setup-env.sh status      # 查看当前全环境运行状态
#   ./scripts/setup-env.sh stop        # 停止后端与前端后台进程
#   ./scripts/setup-env.sh stop --all  # 停止后端、前端进程及 MySQL/Redis 容器
#   ./scripts/setup-env.sh logs backend/frontend # 实时查看日志
# ==============================================================================

set -eo pipefail

# 路径锚定：无论从哪里调用，均唯一定位项目根目录 (位于 scripts/ 的上一级)
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER_DIR="${PROJECT_ROOT}/server"
FRONTEND_DIR="${PROJECT_ROOT}/frontend"

PID_DIR="${PROJECT_ROOT}/.run"
mkdir -p "${PID_DIR}"

BACKEND_PID_FILE="${PID_DIR}/backend.pid"
FRONTEND_PID_FILE="${PID_DIR}/frontend.pid"
BACKEND_LOG="${PID_DIR}/backend.log"
FRONTEND_LOG="${PID_DIR}/frontend.log"

# 彩色控制
CLR_RESET='\033[0m'
CLR_RED='\033[31m'
CLR_GREEN='\033[32m'
CLR_YELLOW='\033[33m'
CLR_BLUE='\033[34m'
CLR_CYAN='\033[36m'
CLR_BOLD='\033[1m'
CLR_DIM='\033[2m'

print_banner() {
  printf "\n${CLR_BOLD}${CLR_CYAN}"
  printf "┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓\n"
  printf "┃                   RentAgent 运行环境一键部署管理工具                 ┃\n"
  printf "┃             AI 智能体房屋租赁系统 · 全套服务编排与生命周期维护       ┃\n"
  printf "┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛\n"
  printf "${CLR_RESET}\n"
}

log_phase() {
  printf "\n${CLR_BOLD}${CLR_BLUE}======================================================================${CLR_RESET}\n"
  printf "${CLR_BOLD}${CLR_BLUE}  [%s] %s${CLR_RESET}\n" "$1" "$2"
  printf "${CLR_BOLD}${CLR_BLUE}======================================================================${CLR_RESET}\n"
}

log_action() {
  printf "\n${CLR_BOLD}${CLR_CYAN}▶ %s${CLR_RESET}\n" "$1"
}

log_detail() {
  printf "  ${CLR_DIM}• %s${CLR_RESET}\n" "$1"
}

log_sub() {
  printf "    %s\n" "$1"
}

log_ok() {
  printf "  ${CLR_GREEN}✓ %s${CLR_RESET}\n" "$1"
}

log_warn() {
  printf "  ${CLR_YELLOW}⚠ %s${CLR_RESET}\n" "$1"
}

log_err() {
  printf "  ${CLR_RED}✗ %s${CLR_RESET}\n" "$1"
}

# 检查 PID 对应进程是否在运行
is_pid_running() {
  local pid="$1"
  [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null
}

# 1. 基础设施启动与健康等待
start_infra() {
  log_phase "阶段 1/3" "启动中间件基础设施 (MySQL 8 + Redis 7)"
  cd "${PROJECT_ROOT}"

  # 镜像加速拉取函数：优先尝试国内高速镜像源，避免官方 Docker Hub 下载缓慢或超时
  ensure_image() {
    local target_image="$1"
    local mirror_path="$2"
    local mirrors=("${DOCKER_MIRROR:-docker.m.daocloud.io}" "dockerpull.cn" "dockerproxy.1panel.live")

    if docker image inspect "$target_image" >/dev/null 2>&1; then
      log_ok "本地已存在镜像: ${target_image} (跳过下载)"
      return 0
    fi

    log_action "本地未检测到镜像 ${target_image}，正在使用国内高速镜像源下载..."
    local pulled=0

    for mirror in "${mirrors[@]}"; do
      [[ -z "$mirror" ]] && continue
      log_detail "正在尝试镜像源: ${mirror}/${mirror_path} ..."
      if docker pull "${mirror}/${mirror_path}"; then
        docker tag "${mirror}/${mirror_path}" "${target_image}"
        log_ok "成功从 ${mirror} 加速下载并标记为 ${target_image}"
        pulled=1
        break
      else
        log_warn "镜像源 ${mirror} 响应失败，尝试下一个备选源..."
      fi
    done

    if (( pulled == 0 )); then
      log_warn "国内加速源均未成功，尝试直接拉取官方源: ${target_image}..."
      if ! docker pull "${target_image}"; then
        log_err "拉取镜像 ${target_image} 失败，请检查网络！"
        return 1
      fi
    fi
  }

  log_action "1.1 检查并加速拉取基础镜像..."
  ensure_image "mysql:8.0" "library/mysql:8.0"
  ensure_image "redis/redis-stack-server:latest" "redis/redis-stack-server:latest"

  log_action "1.2 正在启动 Docker 容器..."
  log_detail "执行命令: docker compose up -d mysql redis"
  log_detail "规避设计: 严格显式指定 mysql redis，防止裸 compose 连带拉起未配置的 server 容器"

  docker compose up -d mysql redis

  log_action "1.3 等待基础设施健康检查通过 (HealthCheck)..."
  log_detail "MySQL 8 容器: rentagent-mysql (端口 3306，包含 01_schema.sql 19 张核心表)"
  log_detail "Redis 7 容器: rentagent-redis (端口 6379，用于验证码、限流与会话缓存)"

  local max_wait=60
  local waited=0
  local mysql_ok=0
  local redis_ok=0
  local attempt=1

  while (( waited < max_wait )); do
    local m_status="stopped"
    local r_status="stopped"

    m_status=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}starting{{end}}' rentagent-mysql 2>/dev/null || true)
    m_status="${m_status:-starting}"

    r_status=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}starting{{end}}' rentagent-redis 2>/dev/null || true)
    r_status="${r_status:-starting}"

    if [[ "$m_status" == "healthy" ]]; then
      mysql_ok=1
    fi
    if [[ "$r_status" == "healthy" ]]; then
      redis_ok=1
    fi

    # 打印每次探测的详细状态
    printf "  ${CLR_DIM}[探测 #%02d | 已等待 %02ds]${CLR_RESET} MySQL: " "$attempt" "$waited"
    if (( mysql_ok == 1 )); then
      printf "${CLR_GREEN}[healthy - 运行就绪]${CLR_RESET}  |  Redis: "
    else
      printf "${CLR_YELLOW}[%s - 正在初始化/检查中]${CLR_RESET}  |  Redis: " "$m_status"
    fi

    if (( redis_ok == 1 )); then
      printf "${CLR_GREEN}[healthy - 运行就绪]${CLR_RESET}\n"
    else
      printf "${CLR_YELLOW}[%s - 正在启动中]${CLR_RESET}\n" "$r_status"
    fi

    if (( mysql_ok == 1 && redis_ok == 1 )); then
      break
    fi

    sleep 2
    waited=$((waited + 2))
    attempt=$((attempt + 1))
  done

  if (( mysql_ok == 0 || redis_ok == 0 )); then
    log_err "基础设施启动超时 (已等待 ${max_wait}s)！"
    log_detail "排查建议: 可执行 'docker compose logs mysql' 或 'docker compose logs redis' 查看容器日志"
    return 1
  fi

  log_ok "基础设施全部就绪：MySQL 8 (3306) 与 Redis 7 (6379) 均达到健康状态！"
}

# 2. 编译与启动后端
start_backend() {
  log_phase "阶段 2/3" "编译并启动后端服务 (Spring Boot 3 + LangChain4j)"
  cd "${SERVER_DIR}"

  # 检查是否已在运行
  if [[ -f "${BACKEND_PID_FILE}" ]]; then
    local old_pid
    old_pid=$(cat "${BACKEND_PID_FILE}")
    if is_pid_running "${old_pid}"; then
      log_warn "后端服务已在运行中 (PID: ${old_pid})，跳过重复启动。"
      return 0
    fi
  fi

  log_action "2.1 增量编译后端工程..."
  log_detail "工作目录: server/"
  log_detail "执行命令: mvn -q compile"
  log_detail "规避设计: 改动后必须重新编译，避免 spring-boot:run 直接执行旧的 target class"

  local compile_start
  compile_start=$(date +%s)
  mvn -q compile
  local compile_end
  compile_end=$(date +%s)
  local compile_duration=$((compile_end - compile_start))
  log_ok "后端工程编译成功 (耗时: ${compile_duration}s)"

  log_action "2.2 派生后端服务进程 (后台执行)..."
  log_detail "启动命令: nohup mvn spring-boot:run > ${BACKEND_LOG} 2>&1 &"
  log_detail "日志重定向: ${BACKEND_LOG}"

  nohup mvn spring-boot:run > "${BACKEND_LOG}" 2>&1 &
  local new_pid=$!
  echo "${new_pid}" > "${BACKEND_PID_FILE}"
  log_ok "后端 Spring Boot 进程已启动 (PID: ${new_pid})"

  log_action "2.3 探测后端服务与种子数据就绪状态 (防首启竞态)..."
  log_detail "规避设计 1: DataInitializer 是 CommandLineRunner + @Transactional，Web 端口会先于种子数据提交就绪"
  log_detail "规避设计 2: 绝不可使用登录接口探测，若因种子数据未提交导致登录失败 5 次将触发 Redis 锁号 10 分钟"
  log_detail "就绪判据: 轮询公开接口 GET http://localhost:8080/api/v1/houses?size=1 直到 total >= 1"

  local max_wait=90
  local waited=0
  local ready=0
  local attempt=1

  while (( waited < max_wait )); do
    if ! is_pid_running "${new_pid}"; then
      log_err "后端进程 (PID: ${new_pid}) 意外退出，启动中断！"
      log_detail "请检查日志文件: ${BACKEND_LOG}"
      log_detail "最后 20 行日志如下："
      printf "\n${CLR_DIM}"
      tail -n 20 "${BACKEND_LOG}" || true
      printf "${CLR_RESET}\n"
      return 1
    fi

    local response
    response=$(curl -s --connect-timeout 2 "http://localhost:8080/api/v1/houses?size=1" 2>/dev/null || true)

    if [[ -n "$response" ]] && [[ "$response" =~ \"total\":([1-9][0-9]*) ]]; then
      local total="${BASH_REMATCH[1]}"
      ready=1
      printf "  ${CLR_DIM}[探测 #%02d | 已等待 %02ds]${CLR_RESET} %s\n" \
        "$attempt" "$waited" "${CLR_GREEN}✓ 房源数据已成功就绪 (查询到初始房源总数: ${total} 套)${CLR_RESET}"
      break
    elif [[ -n "$response" ]] && [[ "$response" =~ \"total\":0 ]]; then
      printf "  ${CLR_DIM}[探测 #%02d | 已等待 %02ds]${CLR_RESET} %s\n" \
        "$attempt" "$waited" "${CLR_YELLOW}Web 端口已响应，但种子数据正在灌库提交中 (total: 0)...${CLR_RESET}"
    else
      printf "  ${CLR_DIM}[探测 #%02d | 已等待 %02ds]${CLR_RESET} %s\n" \
        "$attempt" "$waited" "${CLR_DIM}Spring Boot 正在初始化 ApplicationContext 与 MyBatis 连接池...${CLR_RESET}"
    fi

    sleep 2
    waited=$((waited + 2))
    attempt=$((attempt + 1))
  done

  if (( ready == 1 )); then
    log_ok "后端服务启动并完成数据种子提交！(地址: http://localhost:8080/api/v1)"
  else
    log_err "后端启动或数据初始化超时 (等待 ${max_wait}s)！"
    log_detail "最后 25 行后端日志如下："
    printf "\n${CLR_DIM}"
    tail -n 25 "${BACKEND_LOG}" || true
    printf "${CLR_RESET}\n"
    return 1
  fi
}

# 3. 启动前端
start_frontend() {
  log_phase "阶段 3/3" "启动前端开发服务器 (React 18 + Vite 5)"
  cd "${FRONTEND_DIR}"

  if [[ -f "${FRONTEND_PID_FILE}" ]]; then
    local old_pid
    old_pid=$(cat "${FRONTEND_PID_FILE}")
    if is_pid_running "${old_pid}"; then
      log_warn "前端服务已在运行中 (PID: ${old_pid})，跳过重复启动。"
      return 0
    fi
  fi

  log_action "3.1 派生前端 Vite 开发服务器 (后台执行)..."
  log_detail "工作目录: frontend/"
  log_detail "启动命令: nohup npm run dev > ${FRONTEND_LOG} 2>&1 &"
  log_detail "日志重定向: ${FRONTEND_LOG}"

  nohup npm run dev > "${FRONTEND_LOG}" 2>&1 &
  local new_pid=$!
  echo "${new_pid}" > "${FRONTEND_PID_FILE}"
  log_ok "前端 Vite 进程已启动 (PID: ${new_pid})"

  log_action "3.2 探测前端端口就绪 (http://localhost:5173)..."
  local max_wait=30
  local waited=0
  local ready=0
  local attempt=1

  while (( waited < max_wait )); do
    if ! is_pid_running "${new_pid}"; then
      log_err "前端进程 (PID: ${new_pid}) 意外退出，请查看日志: ${FRONTEND_LOG}"
      return 1
    fi

    if curl -s --connect-timeout 2 "http://localhost:5173" >/dev/null 2>&1; then
      ready=1
      printf "  ${CLR_DIM}[探测 #%02d | 已等待 %02ds]${CLR_RESET} %s\n" \
        "$attempt" "$waited" "${CLR_GREEN}✓ 前端 Vite 服务器已响应 (HTTP 200)${CLR_RESET}"
      break
    else
      printf "  ${CLR_DIM}[探测 #%02d | 已等待 %02ds]${CLR_RESET} %s\n" \
        "$attempt" "$waited" "${CLR_DIM}等待 Vite 构建资源并开启 5173 监听端口...${CLR_RESET}"
    fi

    sleep 1
    waited=$((waited + 1))
    attempt=$((attempt + 1))
  done

  if (( ready == 1 )); then
    log_ok "前端服务启动完成！(地址: http://localhost:5173)"
  else
    log_warn "前端就绪探测超时，请检查日志: ${FRONTEND_LOG}"
  fi
}

# 停止服务
stop_all() {
  local stop_docker="${1:-false}"
  log_phase "服务停止" "正在停止 RentAgent 运行环境"

  # 1. 停止前端
  log_action "停止前端服务 (React / Vite)..."
  if [[ -f "${FRONTEND_PID_FILE}" ]]; then
    local f_pid
    f_pid=$(cat "${FRONTEND_PID_FILE}")
    if is_pid_running "${f_pid}"; then
      log_detail "正在终止前端 PID: ${f_pid}..."
      kill "${f_pid}" 2>/dev/null || true
    fi
    rm -f "${FRONTEND_PID_FILE}"
    log_ok "前端服务进程已终止"
  else
    log_detail "未发现正在运行的前端 PID 记录文件"
  fi

  # 2. 停止后端
  log_action "停止后端服务 (Spring Boot)..."
  if [[ -f "${BACKEND_PID_FILE}" ]]; then
    local b_pid
    b_pid=$(cat "${BACKEND_PID_FILE}")
    if is_pid_running "${b_pid}"; then
      log_detail "正在终止后端 PID: ${b_pid}..."
      kill "${b_pid}" 2>/dev/null || true
    fi
    rm -f "${BACKEND_PID_FILE}"
  fi

  # 规避设计：pkill 匹配规则防自杀
  log_detail "执行安全进程匹配清理 (规避模式: pkill -f 'spring-boot[:]run')..."
  pkill -f 'spring-boot[:]run' 2>/dev/null || true
  log_ok "后端服务进程已终止"

  # 3. Docker 容器管理
  if [[ "$stop_docker" == "true" ]]; then
    log_action "停止 Docker 基础设施容器..."
    cd "${PROJECT_ROOT}"
    docker compose stop mysql redis
    log_ok "MySQL 与 Redis 容器已停止"
  else
    log_detail "提示: MySQL 8 与 Redis 7 容器仍保持后台运行（保持数据热态）"
    log_detail "若需要一并停止容器，请执行: ./scripts/setup-env.sh stop --all"
  fi
}

# 状态检测
show_status() {
  log_phase "环境巡检" "RentAgent 各组件实时状态检测"

  # MySQL
  local m_status
  m_status=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}stopped{{end}}' rentagent-mysql 2>/dev/null || true)
  m_status="${m_status:-not running}"
  printf "  1. MySQL 8 数据库:    "
  if [[ "$m_status" == "healthy" ]]; then
    printf "${CLR_GREEN}[运行中 - healthy]${CLR_RESET}  端口: 3306 (容器名: rentagent-mysql)\n"
  else
    printf "${CLR_RED}[未就绪 - %s]${CLR_RESET}   (提示: 可使用 ./scripts/setup-env.sh infra 启动)\n" "$m_status"
  fi

  # Redis
  local r_status
  r_status=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}stopped{{end}}' rentagent-redis 2>/dev/null || true)
  r_status="${r_status:-not running}"
  printf "  2. Redis 7 缓存:      "
  if [[ "$r_status" == "healthy" ]]; then
    printf "${CLR_GREEN}[运行中 - healthy]${CLR_RESET}  端口: 6379 (容器名: rentagent-redis)\n"
  else
    printf "${CLR_RED}[未就绪 - %s]${CLR_RESET}   (提示: 可使用 ./scripts/setup-env.sh infra 启动)\n" "$r_status"
  fi

  # 后端
  local b_pid=""
  if [[ -f "${BACKEND_PID_FILE}" ]]; then
    b_pid=$(cat "${BACKEND_PID_FILE}")
  fi
  printf "  3. 后端 Spring Boot:  "
  if is_pid_running "${b_pid}"; then
    local b_probe
    b_probe=$(curl -s --connect-timeout 2 "http://localhost:8080/api/v1/houses?size=1" 2>/dev/null || true)
    if [[ -n "$b_probe" ]] && [[ "$b_probe" =~ \"code\":0 ]]; then
      printf "${CLR_GREEN}[运行中 - 接口正常]${CLR_RESET}  端口: 8080 (PID: %s)\n" "${b_pid}"
    else
      printf "${CLR_YELLOW}[进程活跃 - 初始化中]${CLR_RESET} 端口: 8080 (PID: %s)\n" "${b_pid}"
    fi
  else
    printf "${CLR_RED}[未运行]${CLR_RESET}            (提示: 可使用 ./scripts/setup-env.sh backend 启动)\n"
  fi

  # 前端
  local f_pid=""
  if [[ -f "${FRONTEND_PID_FILE}" ]]; then
    f_pid=$(cat "${FRONTEND_PID_FILE}")
  fi
  printf "  4. 前端 Vite/React:   "
  if is_pid_running "${f_pid}"; then
    if curl -s --connect-timeout 2 "http://localhost:5173" >/dev/null 2>&1; then
      printf "${CLR_GREEN}[运行中 - 正常监听]${CLR_RESET}  端口: 5173 (PID: %s)\n" "${f_pid}"
    else
      printf "${CLR_YELLOW}[进程活跃 - 启动中]${CLR_RESET}   端口: 5173 (PID: %s)\n" "${f_pid}"
    fi
  else
    printf "${CLR_RED}[未运行]${CLR_RESET}            (提示: 可使用 ./scripts/setup-env.sh frontend 启动)\n"
  fi
  printf "\n"
}

# 打印引导摘要
print_summary() {
  printf "\n${CLR_BOLD}${CLR_GREEN}"
  printf "┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓\n"
  printf "┃                   🎉 RentAgent 全套运行环境搭建就绪！                ┃\n"
  printf "┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛\n"
  printf "${CLR_RESET}\n"

  printf "${CLR_BOLD}【服务访问导航】${CLR_RESET}\n"
  printf "  🌐 前端 Web 门户:    ${CLR_BOLD}${CLR_CYAN}http://localhost:5173${CLR_RESET}\n"
  printf "     ${CLR_DIM}※ 注意: 必须在浏览器中使用 localhost 访问（勿用 127.0.0.1，以符合后端 CORS 白名单）${CLR_RESET}\n"
  printf "  🔌 后端 RESTful API: ${CLR_BOLD}http://localhost:8080/api/v1${CLR_RESET}\n"
  printf "  📑 接口文档 (Swagger): ${CLR_BOLD}http://localhost:8080/swagger-ui.html${CLR_RESET}\n\n"

  printf "${CLR_BOLD}【预设演示账号（密码统一为 123456）】${CLR_RESET}\n"
  printf "  ┌───────────────┬───────────────┬────────────────────────────────────────────┐\n"
  printf "  │ 用户名        │ 角色          │ 核心业务演示点                             │\n"
  printf "  ├───────────────┼───────────────┼────────────────────────────────────────────┤\n"
  printf "  │ xiaochen      │ 租客          │ AI 找房对话、房源收藏、预约看房、在线签约  │\n"
  printf "  │ lilandlord    │ 房东 (已实名) │ 发布房源、AI 智能定价、AI 描述自动填充     │\n"
  printf "  │ wanglandlord  │ 房东 (未实名) │ 实名认证前置拦截闭环、认证材料提交         │\n"
  printf "  │ admin         │ 管理员        │ 实名与房源审核、AI 虚假房源检测、对话审计  │\n"
  printf "  └───────────────┴───────────────┴────────────────────────────────────────────┘\n\n"

  printf "${CLR_BOLD}【便捷运维管理指令】${CLR_RESET}\n"
  printf "  • 检查各服务状态:     ${CLR_CYAN}./scripts/setup-env.sh status${CLR_RESET}\n"
  printf "  • 实时查看后端日志:   ${CLR_CYAN}./scripts/setup-env.sh logs backend${CLR_RESET}\n"
  printf "  • 实时查看前端日志:   ${CLR_CYAN}./scripts/setup-env.sh logs frontend${CLR_RESET}\n"
  printf "  • 停止前后端应用:     ${CLR_CYAN}./scripts/setup-env.sh stop${CLR_RESET}\n"
  printf "  • 停止全部(含容器):   ${CLR_CYAN}./scripts/setup-env.sh stop --all${CLR_RESET}\n"
  printf "  • 运行集成冒烟测试:   ${CLR_CYAN}bash scripts/smoke.sh${CLR_RESET}\n\n"
}

# 实时日志
view_logs() {
  local target="${1:-backend}"
  if [[ "$target" == "frontend" ]]; then
    if [[ -f "${FRONTEND_LOG}" ]]; then
      log_action "正在追踪前端实时日志 (Ctrl+C 退出): ${FRONTEND_LOG}"
      tail -f "${FRONTEND_LOG}"
    else
      log_warn "未找到前端日志文件: ${FRONTEND_LOG}"
    fi
  else
    if [[ -f "${BACKEND_LOG}" ]]; then
      log_action "正在追踪后端实时日志 (Ctrl+C 退出): ${BACKEND_LOG}"
      tail -f "${BACKEND_LOG}"
    else
      log_warn "未找到后端日志文件: ${BACKEND_LOG}"
    fi
  fi
}

# 命令分发
print_banner

case "${1:-all}" in
  all|"")
    start_infra
    start_backend
    start_frontend
    print_summary
    ;;
  infra)
    start_infra
    log_detail "基础设施已就绪。你可以在 server/ 目录下执行 ./run-dev.sh，并在 frontend/ 目录下执行 npm run dev。"
    ;;
  backend)
    start_backend
    ;;
  frontend)
    start_frontend
    ;;
  stop)
    if [[ "${2:-}" == "--all" ]]; then
      stop_all true
    else
      stop_all false
    fi
    ;;
  restart)
    stop_all false
    start_backend
    start_frontend
    print_summary
    ;;
  status)
    show_status
    ;;
  logs)
    view_logs "${2:-backend}"
    ;;
  help|-h|--help)
    printf "用法: %s [all|infra|backend|frontend|stop|restart|status|logs|help]\n\n" "$0"
    printf "示例: ./scripts/setup-env.sh\n\n"
    printf "选项说明:\n"
    printf "  all (默认)        一键启动 MySQL、Redis、后端与前端全部运行环境\n"
    printf "  infra             仅启动并就绪 MySQL 8 与 Redis 7 容器\n"
    printf "  backend           仅增量编译并启动后端 Spring Boot 服务\n"
    printf "  frontend          仅启动前端 React/Vite 开发服务器\n"
    printf "  status            查看 MySQL/Redis/后端/前端各组件运行状态与端口\n"
    printf "  stop              优雅停止后端与前端后台进程\n"
    printf "  stop --all        停止前后端进程，并停止 MySQL 与 Redis 容器\n"
    printf "  restart           重启前后端应用服务\n"
    printf "  logs backend      实时查看后端控制台输出日志\n"
    printf "  logs frontend     实时查看前端控制台输出日志\n"
    ;;
  *)
    log_err "未知命令: $1"
    printf "可用命令: all (默认), infra, backend, frontend, stop, restart, status, logs\n"
    exit 1
    ;;
esac
