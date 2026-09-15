# RentAgent —— 基于 AI 智能体的房屋租赁系统

课程设计 / 团队开发实践项目：把租赁业务全流程与大模型智能体深度融合的租房平台——**"对话即服务"**。

| 端 | 技术栈 |
| -- | ------ |
| 前端（final） | React 18 + TypeScript + Vite 5 + Ant Design 5 + ECharts（`frontend/`，`npm run build` = `tsc --noEmit && vite build`） |
| 后端 | Spring Boot 3.x + MyBatis-Plus + LangChain4j + 自研 LLM 协议适配器（`server/`） |
| 数据 | MySQL 8（18 张业务表）+ Redis Stack（缓存 / 向量检索预留） |
| AI | LLM 网关三协议后端：Anthropic Messages（当前启用，智谱 GLM-5.3-flash 兼容端点）/ OpenAI Chat Completions / OpenAI Responses；无 Key 自动降级内置规则引擎 |
| 设计文档 | `doc/`（需求分析矩阵、概要设计说明 v1.3、数据库设计简介 v1.1 等） |
| 演示原型 | `prototype/`（React 高保真原型，规则模拟 AI，无需后端） |

## 快速启动

### 1. 基础设施（MySQL + Redis）

```bash
docker compose up -d mysql redis
# 首次启动自动建库建表（docker/mysql-init/01_schema.sql，18 张表）
```

> 国内环境拉不动 Docker Hub 时，可走镜像站后重打标签：
> `docker pull docker.m.daocloud.io/library/mysql:8.0 && docker tag docker.m.daocloud.io/library/mysql:8.0 mysql:8.0`

### 2. 启动后端（端口 8080）

```bash
cd server
# 方式一：本地开发脚本（含个人 API Key，已被 .gitignore 排除，按需填入自己的 Key）
./run-dev.sh
# 方式二：环境变量直配
AI_PROTOCOL=anthropic-messages \
AI_BASE_URL=https://open.bigmodel.cn/api/anthropic \
AI_MODEL=glm-5.3-flash \
AI_API_KEY=你的Key mvn spring-boot:run
# 方式三：不配 Key —— 智能体自动降级为内置规则引擎，全功能可演示
mvn spring-boot:run
```

启动后验证：`curl http://localhost:8080/api/v1/ai/engine` → `{"engine":"anthropic-messages:glm-5.3-flash"}`（或 `rule-engine`）。

接口文档（Swagger）：<http://localhost:8080/swagger-ui.html>

### 3. 启动前端（端口 5173）

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 <http://localhost:5173>。

### 4. 演示账号（种子数据自动写入，密码统一 `123456`）

| 账号 | 角色 | 演示点 |
| ---- | ---- | ------ |
| xiaochen | 租客 | AI 找房、收藏、预约、签约、订单、评价 |
| wanglandlord | 房东（未实名） | 实名认证拦截演示 |
| lilandlord | 房东（已实名） | 房源发布、AI 定价建议、AI 填充 |
| admin | 管理员 | 审核工作台、AI 虚假房源检测、用户/举报管理、数据看板 |

注册验证码固定 `246810`（演示环境）。

## 冒烟测试

```bash
bash scripts/smoke.sh   # 覆盖 实名拦截→发布→审核→预约冲突→签约→账单→评价限制→AI 分析→看板
```

## 文档索引

见 `doc/` 目录：`01-项目技术背景介绍`、`02-项目最终确认书`、`03-需求分析矩阵`、`08-需求跟踪矩阵`、`doc/03.概要设计/概要设计说明`、`doc/03.概要设计/数据库设计简介`。
