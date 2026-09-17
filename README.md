# RentAgent —— 基于 AI 智能体的房屋租赁系统

课程设计 / 团队开发实践项目：把租赁业务全流程与大模型智能体深度融合的租房平台——**"对话即服务"**。

| 端 | 技术栈 |
| -- | ------ |
| 前端（final） | React 18 + TypeScript + Vite 5 + Ant Design 5 + ECharts（`frontend/`，一套代码两种分发：**浏览器 Web 版** + **Electron 桌面端**） |
| 后端 | Spring Boot 3.x + MyBatis-Plus + LangChain4j + 自研 LLM 协议适配器（`server/`） |
| 数据 | MySQL 8（18 张业务表）+ Redis Stack（缓存 / 向量检索预留） |
| AI | **模型无关的 LLM 网关**：三协议适配（OpenAI Chat Completions / Anthropic Messages / OpenAI Responses），端点、模型、Key 全部可配置，换模型只改配置；当前演示通道经聚合网关接入 `deepseek/deepseek-v4.1-flash`。**AI 能力必须接真实模型**：未配置模型或模型调用失败时统一返回 4001 并提示，不做任何规则/模拟兜底 |
| 设计文档 | `doc/`（需求定义说明书、需求分析矩阵、概要设计说明 v1.5、数据库设计简介 v1.2 等） |
| 演示原型 | `doc/02.需求分析/prototype/`（React 高保真原型，规则模拟 AI，无需后端） |

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
# 方式二：环境变量直配——任意兼容端点 + 任意模型（协议默认 OpenAI Chat Completions）
AI_API_KEY=你的Key \
AI_BASE_URL=https://你的端点/v1 \
AI_MODEL=你的模型名 mvn spring-boot:run
# 方式三：切命名后端（yml 里预置了聚合网关 / DeepSeek / Claude / OpenAI 四条通道）
AI_BACKEND=commandcode-chat AGGREGATOR_API_KEY=你的Key mvn spring-boot:run
# 方式四：不配 Key —— AI 能力会明确报 4001（本系统不使用模拟/规则兜底，其余功能正常）
mvn spring-boot:run
```

换模型只改 `AI_MODEL`（或命名后端的 `model`），不改任何代码；协议不同时改 `AI_PROTOCOL`
（`openai-chat-completions` / `anthropic-messages` / `openai-responses`）。通道清单与变量名见
`server/src/main/resources/application.yml` 的 `ai:` 段。

启动后验证：`curl http://localhost:8080/api/v1/ai/engine` → `{"engine":"openai-chat-completions:deepseek/deepseek-v4.1-flash"}`
（实际显示当前生效的 `协议:模型`，未配 Key 时为 `rule-engine`）。

接口文档（Swagger）：<http://localhost:8080/swagger-ui.html>

### 3. 启动前端（端口 5173）

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 <http://localhost:5173>。

> 请使用 `localhost` 而非 `127.0.0.1` 访问：浏览器把两者视为不同来源，后端的跨域白名单
> （`CORS_ORIGINS`，默认已含 localhost/127.0.0.1 的 5173 与 4173）之外会被拒绝，表现为登录无反应。
> 如需改白名单：`CORS_ORIGINS=http://your-host:5173 mvn spring-boot:run`。

### 3.1 桌面端（Electron 外壳，与 Web 版同一份页面）

```bash
cd frontend
npm install
npm run build:web          # 构建 Web 产物 frontend/dist（tsc --noEmit && vite build）
npm run start:desktop      # 用原生窗口打开 dist（内部会先构建一遍）
npm run dev:desktop        # 开发模式：窗口加载 Vite 开发服务器，改代码即时生效
npm run dist:desktop       # 打包安装包 → frontend/release/（AppImage + deb）
```

- 桌面端**只包装 Web 版页面**：加载同一份 `dist`，不新增界面与业务功能。唯一补充是把后端地址
  注入页面（`file://` 下相对路径取不到后端），由 `electron/preload.cjs` 交给 `src/runtime.ts`。
- 后端地址默认 `http://localhost:8080`，可在启动时覆盖：`RENTAGENT_API_BASE=http://<host>:8080 ./RentAgent-1.0.0.AppImage`
  或 `./RentAgent-1.0.0.AppImage --api-base=http://<host>:8080`。
- 打包配置见 `frontend/electron-builder.yml`：只把 `dist/` 与 `electron/` 打进 asar，不复制 `node_modules`
  （前端依赖已被 Vite 打进 `dist`）。Windows/macOS 安装包需在对应系统上执行 `npx electron-builder --win|--mac`。
- 桌面端以 `file://` 加载，不发 `Origin` 头，因此不受后端跨域白名单限制。

### 4. 演示账号（种子数据自动写入，密码统一 `123456`）

| 账号 | 角色 | 演示点 |
| ---- | ---- | ------ |
| xiaochen | 租客 | AI 找房、收藏、预约、签约、订单、评价 |
| wanglandlord | 房东（未实名） | 实名认证拦截演示 |
| lilandlord | 房东（已实名） | 房源发布、AI 定价建议、AI 填充 |
| admin | 管理员 | 审核工作台、AI 虚假房源检测、用户/举报管理、AI 对话审计（全站会话与工具调用轨迹）、数据看板 |

注册验证码固定 `246810`（演示环境）。

## 冒烟测试

```bash
bash scripts/smoke.sh   # 覆盖 实名拦截→发布→审核→预约冲突→签约→账单→评价限制→AI 分析→看板→AI 对话审计（含越权检查）
```

## 前端两种版本的关系

| | Web 版 | 桌面端 |
| -- | ------ | ------ |
| 页面代码 | `frontend/src/`（同一份） | 同左，无任何分叉 |
| 构建产物 | `frontend/dist`（`base: './'` 相对基址） | 同上，直接复用 |
| 运行载体 | 浏览器；Nginx 托管静态资源并反代 `/api` | Electron 窗口；页面以 `file://` 加载 |
| 后端地址来源 | 同源相对路径（开发期由 Vite 代理） | `electron/preload.cjs` 注入的绝对地址 |
| 追加文件 | — | `frontend/electron/`（窗口壳）、`frontend/build/`（图标）、`electron-builder.yml` |
| 打包 | `npm run build:web` | `npm run dist:desktop` → AppImage / deb |

两版实测结论（2026-09-17）：浏览器版经 `npm run preview` 登录并浏览房源正常；桌面端登录、房源列表、
AI 对话 SSE 流式输出均正常，AppImage 安装包启动后同样正常。

## 文档索引

见 `doc/README.md`。阶段目录：`doc/01.需求定义/`（含《需求定义说明书》.docx/.pdf）、
`doc/02.需求分析/`（需求分析矩阵、原型与截图）、`doc/03.概要设计/`（《概要设计》.docx/.pdf、
《数据库表》.xlsx/.xls、ER 图）；`doc/04.编码/`、`doc/05.集成测试/`、`doc/06.技术调查/`
与 `doc/最终答辩成果物/` 待补。
