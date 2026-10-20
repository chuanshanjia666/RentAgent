# RentAgent CI 流水线设计

> 项目名称：RentAgent —— 基于 AI 智能体的房屋租赁系统
> 文档版本：v1.14（v1.14 于 2026-10-20 支持大模型与词嵌入供应商完全解耦独立配置与 RAG 向量检索：前端与数据库分别支持独立管理 LLM 与 EMBEDDING 供应商；CI 环境变量增加 `AI_EMBEDDING_*` 配置并支持自动回落；无模型反向门禁实例置空嵌入凭据；后端单测基线 222 → **237 条**（新增 `EmbeddingGatewayTest` 5 条、`OpenAiEmbeddingClientTest` 2 条、`KbServiceTest` 6 条、`AiConfigServiceTest` 2 条）；集成冒烟测试覆盖双供应商独立配置与向量检索；关联《概要设计》v1.19）
> 文档版本：v1.13（v1.13 于 2026-10-17 调整 CI/CD 默认大模型通道为大工 AI 网关的 `DeepSeek-V4-Flash-0731-W8A8`（`openai-chat-completions` 兼容协议，端点 `http://aigw.dlut.edu.cn/v1`）；适配器支持根域名输入时自动规约补全 `/v1`；后端单测基线 221 → **222 条**（新增 `UT-OPENAI-18` 端点规范化测试）；`docker-compose.yml` 与 `application.yml` 同步注册 `dlut-deepseek` 命名后端；关联《概要设计》v1.18）
> 文档版本：v1.12（v1.12 于 2026-10-14 调整 CI/CD 默认大模型通道为 Opencode GO 的 `deepseek-v4.1-flash`（`openai-chat-completions` 兼容协议，端点 `https://opencode.ai/zen/go/v1`）；通过 `application.yml` 的 `headers` 与 CI 环境变量注入 OpenCode 所需的 `x-opencode-session` 请求头，代码零特判；后端单测基线保持 **221 条**；`docker-compose.yml` 与 `application.yml` 同步注册 `opencode-go` 命名后端；关联《概要设计》v1.17）
> 文档版本：v1.11（v1.11 于 2026-10-13 同步 AI 供应商动态配置入库与管理控制台功能：数据库表由 18 张增至 **19 张**（新增 `ai_provider_config` 表并同步 CI 建表校验）；后端单测基线 213 → **221 条**（新增 `AiConfigServiceTest` 8 项单测）；集成冒烟断言数 148 → **158 条**（新增非管理员 1007 拦截、配置概览、入库新增、带脱敏更新、删除未激活配置等断言）；精简 `run-dev.sh` 移除个人硬编码 Key；关联《数据库设计简介》v1.7、《单元测试用例设计》v1.10、《概要设计》v1.16）
> 文档版本：v1.10（v1.10 于 2026-09-22 同步 AI 回复内链接渲染缺陷修复：前端单测门禁 26 → **32 条**（新增 `FT-CHAT-01~06`——找房助手输出的 `[标题](#/app/houses/{id})` 站内链接此前因前缀判定误写 `#:` 恒以纯文本显示、不可点击，渲染逻辑抽至 `chatLinks.tsx` 并补回归），§一/§2.1 同步；顺带校正 §一 门禁表与 §2.3 流程图的后端单测计数漏改（212 → 213）；集成冒烟断言数不变（**148 条**）；关联《单元测试用例设计》v1.9）
> 文档版本：v1.9（v1.9 于 2026-09-22 同步评价闭环断言：集成冒烟断言数 146 → **148 条**（IT-6 新增 reviewed 前后回填 2 条：未评价订单回填 `reviewed=false`、评价后回填 `reviewed=true`），§一/§2.1/流程图同步；后端单测基线 212 → **213 条**（`UT-CONTRACT-22`）；关联《单元测试用例设计》v1.8、《测试用例设计》v1.9、《概要设计》v1.15）
> 文档版本：v1.8（v1.8 于 2026-09-21 按代码审查整改（第三轮）同步计数：后端单测基线 210 → **212 条**（新增 `UT-TOOL-06`、`UT-AGENT-07`：工具阶段意外异常统一包装为 `ToolRoundAbortedException` 中止并清残缺会话记忆），§一/§2.1/流程图同步；集成冒烟断言数不变（**146 条**，本轮未改 `ci-smoke.sh`）；关联《单元测试用例设计》v1.7、《概要设计》v1.14）
> 文档版本：v1.7（v1.7 于 2026-09-21 按代码审查整改（第二轮）同步计数：后端单测基线 199 → **210 条**（新增 `HousingToolProviderTest` 5 条、`UT-AGENT-06`、`UT-OPENAI-16~18`、`ChatServiceTest` 2 条），§一/§2.1/§2.3 与流程图同步；集成冒烟断言数不变（**146 条**，本轮未改 `ci-smoke.sh`）；关联《单元测试用例设计》v1.6、《概要设计》v1.13）
> 文档版本：v1.6（v1.6 于 2026-09-20 按代码审查整改（安全/业务/AI 层/工程）更新：集成冒烟断言数 131 → **146 条**（IT-1 新增注册角色白名单 2 条、IT-5 新增房源维度判重 1 条、IT-10 实名认证 5 → 17 条）；后端单测基线 190 → **199 条**；三个作业分别补作业级 `timeout-minutes`（backend-test 15 / frontend-test 15 / integration-smoke 25）；新增 `server/Dockerfile` 与 `server/.dockerignore`、`docker-compose.yml` 补 `UPLOAD_DIR` 与 `AGGREGATOR_*` 透传；关联《测试用例设计》v1.6）
> 文档版本：v1.5（v1.5 于 2026-09-19 同步找房智能体升级：集成冒烟断言数 130 → **131 条**——新增「找房回答直接附房源详情页链接」（`searchHouses` 卡片带 `detailUrl`、提示词约定链接协议、前端气泡渲染站内链接），关联《概要设计》v1.11、《测试用例设计》v1.5）
> 文档版本：v1.4（v1.4 于 2026-09-18 按前后端协同代码评审结论更新：断言数 127 → **130 条**、后端单测基线 182 → **190 条**；补记 `AI_MAX_TOKENS` 可调项（单轮 token 上限默认 4096，需给推理模型留余量）；补记「模型调用失败不再伪装成回答为空」的排障口径——流式分支此前不校验 HTTP 状态码，且把「正文与工具调用全空」伪造为一句可读提示，导致 CI 只表现为三条互不相关的 FR-13 失败断言；现改为如实报错 + 可自愈故障非流式重跑一次）
> 文档版本：v1.3（v1.3 于 2026-09-18 补记前端静态检查门禁与断言计数更新：`frontend-test` 作业新增 ESLint 与 Prettier 两步；集成冒烟断言数随代码评审由 125 条增至 127 条，§一/§二/§四 描述与流程图同步）
> 文档版本：v1.2（v1.2 于 2026-09-17 修正 BUG-02 根因：流式工具调用丢失是客户端解析问题（langchain4j 0.35 组装器在正文非空时丢弃 tool_calls），非网关截断；排障口径与断言口径相应更新）
> 编制日期：2026-09-17
> 编制：项目组测试担当（陈玄晔）
> 关联文档：《单元测试用例设计》、《测试用例设计》（集成层）、`.github/workflows/ci.yml`、`scripts/ci-smoke.sh`
> 适用阶段：五、集成测试（持续集成）
> 变更记录（v1.6，2026-09-20）：按代码审查整改（安全/业务/AI 层/工程）同步：
> ① 计数——集成冒烟断言 131 → **146 条**、后端单测基线 190 → **199 条**，§一/§2.3/§2.4 同步；前端单测门禁口径由 20 条校正为 **26 条**。
> ② 门禁加固——三个作业分别设作业级 `timeout-minutes`（backend-test 15 / frontend-test 15 / integration-smoke 25），
> 避免异常挂起时吃满 GitHub 默认的 6 小时上限。
> ③ 容器化前提——新增 `server/Dockerfile`（多阶段 Maven → JRE）与 `server/.dockerignore`，`docker-compose.yml` 的
> `server` 服务补 `UPLOAD_DIR` 与 `AGGREGATOR_*` 透传（此前 `build: ./server` 指向的上下文里没有 Dockerfile，
> 且只传了 `AI_API_KEY`，该服务实际起不来、起来了 AI 通道也不可用）；`JWT_SECRET`/`AES_KEY` 故意不在 compose 里
> 写成 `${VAR:-}`（未设置时会注入空串，比默认值更早炸），对外部署用环境变量或 `env_file`。
> ④ 启动自检——新增 `config/StartupConfigCheck`（`ApplicationRunner`）：启动时检测 JWT_SECRET / AES_KEY / 数据库口令 /
> 固定验证码仍是仓库默认值即输出 WARN 清单，**只告警不拒绝启动**（本机演示与 CI 都不注入这些变量，fail-fast 会把演示
> 与门禁一起打死）；CI 日志中出现该告警属预期。
> 变更记录（v1.3，2026-09-18）：前端工程化评审后引入 ESLint（扁平配置 `frontend/eslint.config.js`）与 Prettier
> （`frontend/prettier.config.js`），`frontend-test` 作业在 `npm ci` 与单测之间插入 `npm run lint` 与
> `npm run format:check` 两步——前者管"写得对不对"（未使用变量、hook 依赖遗漏），后者管"长得齐不齐"，
> 分开跑以便失败时一眼区分逻辑问题与格式问题；本地一次跑齐用 `npm run check`。作业结构、缓存与依赖安装方式未变。
> 变更记录（v1.1，2026-09-17）：CI 日志出现 Node.js 20 运行时弃用告警（`actions/checkout@v4`、`actions/setup-java@v4`、
> `actions/upload-artifact@v4` 以 Node 20 编译，已被强制改跑 Node 24）。四个官方 Action 统一升到**已切换 Node 24 运行时的大版本**：
> `checkout` v4→v5、`setup-java` v4→v5、`setup-node` v4→v5、`upload-artifact` v4→v6（其 v5 仍默认 Node 20，必须跨到 v6）。
> 仅运行时与 Action 版本变化，作业结构、步骤顺序与输入参数均未改动；项目自身的 Node 版本仍为 20（§2.2）。

---

## 一、目标与门禁原则

1. **每次推送/PR 都跑得动**：单次流水线目标 10 分钟量级，不依赖任何付费服务或私有凭据。
2. **三层门禁，逐层加严**：静态/单元（秒级）→ 构建（分钟级）→ 集成冒烟（全栈，~2 分钟）。
3. **AI 全部走真实模型**：系统已移除全部模拟/规则兜底，AI 用例必须接入真实模型——
   CI 通过 **GitHub Secrets** 注入模型凭据（Key 只进环境变量、不进日志），
   同时用反向门禁校验"没有模型时必须报错"，两条约束一起保证"不会偷偷降级"。
4. **本地与 CI 同源**：CI 调用的就是开发者本地用的命令与脚本，不写第二套逻辑。

| 门禁 | 命令 | 拦截什么 |
| ---- | ---- | -------- |
| 后端单元测试 | `mvn -B -ntp test`（237 条） | 业务状态机、权限、金额/日期计算、加解密回归、AI 配置持久化与热加载、RAG 向量检索与嵌入网关 |
| 前端静态检查与格式 | `npm run lint` + `npm run format:check`（ESLint + Prettier） | 死导入与未使用变量、hook 依赖数组遗漏、缩进引号等风格漂移 |
| 前端单测 | `npm test`（vitest，32 条） | 运行时地址解析、状态字典、SSE 流式解析、AI 回复内链接渲染 |
| 前端类型与构建 | `npm run build:web`（`tsc --noEmit` + vite） | TS 严格模式下的类型错误、构建失败 |
| 集成冒烟 | `bash scripts/ci-smoke.sh`（158 条断言） | 跨模块业务闭环、真实 MySQL/Redis 语义、鉴权链路、**真实模型**的工具调用与引用来源、AI 配置动态管理 |
| 反向门禁 | `bash scripts/ci-no-model-check.sh`（6 条，账号无合同时合同解读项跳过） | "未配置模型时 AI 能力必须硬报错"——防止模拟/规则兜底被重新引入 |

---

## 二、流水线结构

文件：`.github/workflows/ci.yml`。触发条件：`push`（main 分支）、`pull_request`、`workflow_dispatch`（手动重跑）。
`permissions: contents: read`（只读，不回写仓库）；`concurrency` 按分支分组并 `cancel-in-progress`（新提交取消旧流水线）；
全局 `TZ=Asia/Shanghai`（保证冒烟脚本的 `date` 计算与后端 `LocalDate.now()` 落在同一天）。

三个作业**并行**执行，互不阻塞，因此一次运行即可看到全部三层的结论。
三个作业均设作业级 `timeout-minutes`（`backend-test` 15 / `frontend-test` 15 / `integration-smoke` 25）：
异常挂起（等模型、等服务容器就绪等）时快速失败，不再消耗 GitHub 默认的 6 小时上限。

### 2.1 作业一：`backend-test` 后端单测

| 步骤 | 动作 |
| ---- | ---- |
| 检出代码 | `actions/checkout@v5` |
| 环境 | `actions/setup-java@v5`（temurin 17，`cache: maven`） |
| 执行 | `mvn -B -ntp test`（工作目录 `server/`，237 条单测） |
| 归档 | `actions/upload-artifact@v6` 上传 `server/target/surefire-reports/`（`if: always()`，失败可下载报告定位） |

不启任何服务容器：本层用例全部为 Mockito 打桩，不连库、不连缓存、不连网络。

### 2.2 作业二：`frontend-test` 前端单测与构建

| 步骤 | 动作 |
| ---- | ---- |
| 检出代码 | `actions/checkout@v5` |
| 环境 | `actions/setup-node@v5`（Node 20，`cache: npm`，`cache-dependency-path: frontend/package-lock.json`；v5 起新增"检测到 `packageManager` 字段即自动缓存"，本项目 `package.json` 未声明该字段且此处已显式指定 `cache: npm`，故缓存行为与 v4 一致） |
| 依赖 | `npm ci --no-audit --no-fund`（严格按 lockfile，保证可复现） |
| 静态检查 | `npm run lint`（ESLint 扁平配置 `frontend/eslint.config.js`） |
| 格式检查 | `npm run format:check`（Prettier 配置 `frontend/prettier.config.js`） |
| 单测 | `npm test`（vitest run） |
| 构建 | `npm run build:web`（类型检查 + 生产构建，Web 版与桌面端共用同一份 `dist`） |
| 归档 | 上传 `frontend/dist/`（`if-no-files-found: error`，构建产物缺失即失败） |

作业级环境变量 `ELECTRON_SKIP_BINARY_DOWNLOAD=1`：桌面端打包不在 CI 范围，
跳过 Electron 二进制下载可显著缩短依赖安装时间（本地打 AppImage/deb 时不受影响）。

### 2.3 作业三：`integration-smoke` 集成冒烟

| 步骤 | 动作 |
| ---- | ---- |
| 服务容器 | `mysql:8.0`（库/账号与 `docker-compose.yml` 一致，含 healthcheck）与 `redis:7` |
| 初始化库 | 安装 `mysql-client` → 等 MySQL 就绪 → 执行 `docker/mysql-init/01_schema.sql` → 打印表数量（应 19） |
| 构建 | `mvn -B -ntp -DskipTests package`（产出可执行 jar） |
| 启动 | 后台启动 jar（注入 `MYSQL_*`/`REDIS_*`/`JWT_SECRET`/`AES_KEY`），轮询直到**接口可用且种子数据已提交**（先探 `/api/v1/ai/engine`，再探查库的 `/api/v1/houses?size=1` 且 `total ≥ 1`，最多 3 分钟），超时打印后端日志尾部 |
| 冒烟 | `bash scripts/ci-smoke.sh`（158 条断言，失败即非 0 退出） |
| 无模型校验 | 另起一个不注入模型凭据的实例（`SERVER_PORT=8081`），执行 `scripts/ci-no-model-check.sh`，校验 AI 能力全部返回 4001 |
| 归档 | 失败时上传 `/tmp/backend.log` 与 SSE 原始输出 |

种子数据无需准备：空库启动时由应用 `DataInitializer` 自动灌入演示账号、房源与知识库。

### 2.3.1 为什么就绪判据必须查库（首次 CI 失败的真实根因）

首次在 GitHub Actions 跑冒烟时，出现"租客/未实名房东两次登录取不到 token → 后续用例连锁返回 1007/5000"的失败。
根因不是业务缺陷，而是**启动竞态**：

- `DataInitializer` 是 `CommandLineRunner`，且 `run()` 标注了 `@Transactional`；
- 因此 Web 端口（连带数据库连接池）都**先于**种子数据提交就绪。本机启动日志实测：

  ```
  14:44:15.557  Tomcat started on port 8080      ← 端口已监听
  14:44:15.562  Started RentAgentApplication     ← Web 服务就绪
  14:44:15.566  HikariPool-1 - Starting...       ← 连接池都还没建
  14:44:15.709  空库检测到，写入演示种子数据...
  14:44:16.067  nio-8080-exec-1 请求已进来       ← 请求撞在种子写入过程中
  14:44:16.124  演示种子数据写入完成             ← 种子才提交
  ```

- 窗口在本机约 **0.57 秒**，CI 冷容器 + 容器网络下放大到数秒，于是冒烟脚本开头两次登录正好落在窗口内，
  拿到 `1002`（用户还不存在）→ 空 token → 所有依赖登录态的断言连锁失败。
- 本地开发库通常已有种子（`selectCount > 0` 直接返回），所以只在空库首启时暴露。

修复分两层，互为兜底：
1. **流水线**：`启动后端并等待就绪（含演示种子数据）` 步骤以查库的 `/houses` 为判据；
2. **脚本自身**：`ci-smoke.sh` 开头 `wait_for_seed()` 同样等待种子提交后再执行断言（本地手跑也无需预热），
   并新增 `require_id()` 守卫——关键 id 缺失时打印结论并以退出码 1 结束，而不是让 `jq --argjson` 抛解析错误掩盖真实原因。

> 注意：等待判据**不能**用"反复尝试登录"实现——登录失败会计入 Redis 失败计数（5 次锁定 10 分钟），
> 反复重试反而会把演示账号锁住。

### 2.4 流水线拓扑

```
push / PR / 手动
        │
        ├── backend-test      JDK17 ── mvn test（237 条）────────────────────────────────────────┐
        ├── frontend-test     Node20 ── npm ci → lint → format:check → vitest → build:web ───────┤ 并行
        └── integration-smoke JDK17 + mysql:8.0 + redis:7                                        │
                              └─ 建表（19张） → 打包 → 启动 → ci-smoke.sh（158 条）              ┘
                                        ↓
                          全部成功 = 门禁通过；任一失败 = 阻断合并
```

---

## 三、环境与配置

| 变量 | 取值 | 说明 |
| ---- | ---- | ---- |
| `MYSQL_HOST` / `MYSQL_USER` / `MYSQL_PASSWORD` | `127.0.0.1` / `rentagent` / `rentagent123` | 与 compose 一致；服务容器端口映射到 runner 本地 |
| `REDIS_HOST` | `127.0.0.1` | 验证码与登录失败计数 |
| `AI_MAX_TOKENS`（可选） | 未设置时用 `application.yml` 默认 4096 | 单轮回复 token 上限；推理类模型的思维链也计入该预算，实测一轮对话约 1.6k~2.0k tokens，取值过紧会被 `finish_reason=length` 截断成「既无正文也无工具调用」 |
| `JWT_SECRET` | CI 专用测试串 | 真实环境由部署方注入，不入库 |
| `AES_KEY` | CI 专用测试 Base64 密钥 | 同上；**不可**复用生产密钥 |
| `UPLOAD_DIR` | `${{ runner.temp }}/rentagent-uploads` | 写在**步骤级** env：job 级 env 不提供 `runner` 上下文 |
| `BASE_URL` | `http://localhost:8080` | 冒烟脚本基址 |
| `TZ` | `Asia/Shanghai` | 全流程统一时区 |
| `AI_BACKEND` | `secrets.AI_BACKEND`，缺省 `dlut-deepseek` | 选择命名后端（协议+端点+模型组合） |
| `AGGREGATOR_BASE_URL` | `secrets.AGGREGATOR_BASE_URL`，缺省 `http://aigw.dlut.edu.cn/v1` | 模型端点 |
| `AGGREGATOR_MODEL` | `secrets.AGGREGATOR_MODEL`，缺省 `DeepSeek-V4-Flash-0731-W8A8` | 模型名 |
| `AGGREGATOR_API_KEY` | `secrets.AGGREGATOR_API_KEY`（**必填**） | 模型凭据。由 GitHub Actions Secrets 注入，仓库内绝不留存；GitHub 会对其做日志脱敏 |
| `AI_EMBEDDING_ENABLED` | `secrets.AI_EMBEDDING_ENABLED`，缺省 `true` | 是否启用词嵌入与 RAG 向量检索通道 |
| `AI_EMBEDDING_MODEL` | `secrets.AI_EMBEDDING_MODEL`，缺省 `bge-m3` | 词嵌入模型名称（如 `bge-m3`） |
| `AI_EMBEDDING_BASE_URL` | `secrets.AI_EMBEDDING_BASE_URL`（可选） | 词嵌入模型专属端点；未显式指定时，网关自动回退复用 `AGGREGATOR_BASE_URL` 或数据库激活网关 |
| `AI_EMBEDDING_API_KEY` | `secrets.AI_EMBEDDING_API_KEY`（可选） | 词嵌入模型专属密钥；未显式指定时，网关自动回退复用 `AGGREGATOR_API_KEY` 或数据库激活网关 |

安全约定：
- 密钥一律走 GitHub Secrets / 环境变量，仓库内不保存任何真实 Key（`server/run-dev.sh` 含个人 Key，已被 `.gitignore` 忽略，CI 绝不引用）；
- 集成作业使用一次性数据库容器，无需清理逻辑，也不会污染任何持久数据；
- 无模型校验步骤通过**同名环境变量置空**（`AGGREGATOR_API_KEY: ''`、`AI_EMBEDDING_BASE_URL: ''`、`AI_EMBEDDING_API_KEY: ''`、`AI_EMBEDDING_ENABLED: 'false'`）构造"未接入模型"的实例，不会误用主实例的凭据。

---

## 四、本地与 CI 的一致性

| 环节 | 本地开发者 | CI |
| ---- | ---------- | -- |
| 数据库 | `docker compose up -d mysql redis`（`mysql:8.0` / `redis-stack-server`） | 服务容器 `mysql:8.0` / `redis:7`（均为 Redis 基础命令，模块无差异） |
| 建表 | `docker exec -i <mysql> mysql -uroot -p... rentagent < docker/mysql-init/01_schema.sql` | 同样执行该 SQL 文件 |
| 后端启动 | `mvn spring-boot:run`（`server/run-dev.sh` 注入个人 AI Key） | `java -jar target/rentagent-server-*.jar`（不注入 AI Key） |
| 集成验证 | `bash scripts/ci-smoke.sh` | 同一条命令 |
| 单测 | `mvn test` / `npm test` | 同上 |

差异说明：**本地与 CI 都必须接入真实模型**（否则脚本在 `wait_for_model` 前置检查处失败并退出码 2，同时提示配置方式）。
本地直接用 `server/run-dev.sh`（含个人演示 Key）启动即可；若临时不想接模型，可改用
`SERVER_PORT=8081 java -jar …`（不带 AI 环境变量）+ `scripts/ci-no-model-check.sh` 验证"零兜底"约束。

---

## 五、失败处理与复现

1. **单测失败**：作业内直接看到 surefire 摘要，并可从 Artifacts 下载完整报告；本地用 `mvn test -Dtest=<类名>` 复现。
2. **前端失败**：日志含 vitest 用例名与断言差异；本地用 `npx vitest run src/<文件>` 复现。
3. **冒烟失败**：脚本逐条打印 `✓/✗` 与"期望/实际"，末尾给出通过/失败计数；结合上传的后端日志与 SSE 原始文件定位。
   退出码语义：`0` 通过、`1` 断言失败、`2` 前置未就绪（后端/种子数据/模型通道）。
   本地复现命令：
   ```bash
   docker compose up -d mysql redis     # 或复用已有实例
   # 首次需建表：docker exec -i <mysql> mysql -uroot -p... rentagent < docker/mysql-init/01_schema.sql
   cd server && mvn -DskipTests package
   MYSQL_HOST=127.0.0.1 MYSQL_USER=rentagent MYSQL_PASSWORD=rentagent123 REDIS_HOST=127.0.0.1 \
     java -jar target/rentagent-server-1.0.0.jar
   bash scripts/ci-smoke.sh             # 另开终端
   ```
4. **后端启动超时**：作业会打印 `/tmp/backend.log` 尾部 80 行（通常是数据库连通性或建表缺失）。
5. **建表步骤失败**：`mysqladmin ping` 在容器 init 阶段（临时实例）就会成功，此时建库与授权可能尚未就绪——
   作业里的建表语句本身带重试与表数校验（≥18），失败会明确报错而不会被静默吞掉。
6. **AI 断言失败**：先看 `ci-no-model-check` 与 `wait_for_model` 的输出确认模型通道；再看后端日志中
   `本轮未执行任何工具且回答疑似过程语` 的记录——出现它说明本轮工具链未走通（兜底已重跑一次），
   若两条"模型实际调用了 searchHouses / searchKnowledge"断言同时失败，优先怀疑协议适配器的流式解析
   （历史 BUG-02：客户端在正文非空时丢弃 tool_calls，已在 `OpenAiChatCompletionsModel` 修复），
   而不是网关通道；排查手段是先 `curl -N` 取该端点的原始 SSE 分片，直接看 `delta.tool_calls` 是否存在。

---

## 六、扩展与后续

| 序 | 事项 | 前置条件 / 说明 |
| -- | ---- | --------------- |
| 1 | 镜像发布到 GHCR | 已补齐 `server/Dockerfile`（多阶段 Maven → JRE）与 `server/.dockerignore`，`docker-compose.yml` 的 `server` 服务构建上下文已可用（并补 `UPLOAD_DIR` 与 `AGGREGATOR_*` 透传）；后续只需在 workflow 里加 `packages: write` 的镜像发布作业 |
| 2 | 覆盖率报告（JaCoCo / v8） | 加 `jacoco:report` 与 `vitest --coverage`，并把阈值设为门禁（建议行覆盖 ≥60% 起步） |
| 3 | Electron 桌面端打包 | 需 `electron-builder` + Linux 打包依赖，耗时较长；建议只在打 tag 时执行并上传 AppImage/deb |
| 4 | 性能基准（NFR-01/NFR-02） | 引入 k6 场景脚本，对核心查询接口做 50 并发基准并记录首字延迟 |
| 5 | 真实模型通道回归 | 用 GitHub Secrets 注入演示用 Key，增加一个**非阻塞**（`continue-on-error`）的对话质量作业 |
| 6 | 依赖漏洞扫描 | 加 `dependency-review-action`（PR）与 `npm audit`/OWASP 依赖检查 |
| 7 | ~~冒烟脚本的模型无关性~~（已完成） | 演示通道早已是真实模型（规则引擎实现已删除），`IT-7-08` 与 `IT-9-*` 均为真模型口径断言；**另需注意**：换模型后若新模型为推理模型，要留足 `ai.max-tokens`（思维链计入该预算，过紧会被 `finish_reason=length` 截断成空回答），并先跑一遍 `scripts/ci-smoke.sh` 的 IT-9 组确认工具调用与引用来源仍成立 |
| 8 | CI 的 Node 运行时升级（20 → 24 LTS） | 项目自身 `node-version` 仍为 `20`，该版本已于 2026-04 进入 EOL（本次仅升级了 Action 自身的运行时，未动它）；`frontend/package.json` 未声明 `engines`，本地开发机实测为 Node 26。升级前建议本地跑一遍 `npm ci → npm test → npm run build:web` 复核 vite/vitest/jsdom 兼容性，并同步 §2.2 与 §2.4 拓扑图中的 Node 版本 |

---

## 七、执行与验证记录

| 项目 | 内容 |
| ---- | ---- |
| 工作流结构校验 | `act -l -W .github/workflows/ci.yml` 通过（三个作业均可解析）；期间修正一处真实错误：`${{ runner.temp }}` 不能用于 job 级 `env`（`runner` 上下文在 job 级 env 不可用） |
| 后端单测 | 本机 `mvn test` → **148 条全过** |
| 前端单测与构建 | 本机 `npm test` → **20 条全过**；`npm run build:web` → 通过 |
| 代码评审后复跑（v1.3） | 前端静态检查：`npm run lint`（ESLint 扁平配置）与 `npm run format:check`（Prettier）本机均零报错；单元与集成：后端 `mvn test` → **182 条全过**、前端 `npm test` → **26 条全过**、`ci-smoke.sh` → **127 条断言全过、退出码 0**（连续两轮均全绿） |
| 前后端协同评审后复跑（v1.4） | 后端 `mvn test` → **190 条全过**；前端 `npx tsc --noEmit` / `npm run lint` 零报错、`npm test` → **26 条全过**；`ci-smoke.sh` → **130 条断言全过、退出码 0**；`ci-no-model-check.sh`（8081 无凭据实例）→ **5 条通过 + 1 条跳过、退出码 0** |
| 代码审查整改复跑（v1.6，2026-09-20） | 后端 `mvn test` → **199 条全过**；前端 `npm run check`（ESLint + Prettier + vitest **26 条**）与 `npm run build:web`（`tsc --noEmit` + vite）全过；`ci-smoke.sh` → **146 条断言全过、退出码 0**；`ci-no-model-check.sh`（8081 无凭据实例）→ **5 条通过**；未注入密钥的本机实例启动时按预期输出 4 条默认凭据告警（`StartupConfigCheck` 只告警不拒绝启动）。三个作业已补作业级 `timeout-minutes`（backend-test 15 / frontend-test 15 / integration-smoke 25） |
| 代码审查整改（第二轮）复跑（v1.7，2026-09-21） | 后端 `mvn test` → **210 条全过**（新增 11 条：`HousingToolProviderTest` 5 条、`UT-AGENT-06`、`UT-OPENAI-16~18`、`ChatServiceTest` 2 条）；`UT-AGENT-06` 已按"去掉清会话记忆的修复即失败"验证过判据有效性。`ci-smoke.sh`（本机 MySQL 8 + Redis 7 + 运行中后端，真实模型通道）→ **146 条断言全过、退出码 0**，其中 IT-9 段（流式 delta+done、`searchHouses` / `searchKnowledge` 工具留痕、知识库引用、流内无失败载荷）证明被改的 AI 链路端到端可用；断言数未增删。本轮只改后端与文档，前端未改动故未复跑（前端基线仍 26 条） |
| 代码审查整改（第三轮）复跑（v1.8，2026-09-21） | 后端 `mvn test` → **212 条全过**（新增 2 条：`UT-TOOL-06` 走真实 `DefaultToolExecutor` 的非法参数路径、`UT-AGENT-07` 验证工具阶段意外失败同样清残缺记忆）。`ci-smoke.sh`（本机 MySQL 8 + Redis 7 + 运行中后端，真实模型通道）→ **146 条断言全过、退出码 0**；插曲：连续复跑时真模型断言曾间歇失败，后端日志核实为聚合网关 429 限流，且失败路径符合"如实报 4001、不伪造回答"的设计（详见《测试用例设计》v1.8 复跑记录），等待限流窗口后单次复跑全绿。本轮只改后端与文档，前端未改动故未复跑（前端基线仍 26 条） |
| 地图找房与评价闭环批次复跑（v1.9，2026-09-22） | 后端 `mvn test` → **213 条全过**（新增 `UT-CONTRACT-22`：订单列表回填 `reviewed` 标记）；前端改动（地图组件 / 区县字典 / 订单按钮态）已复跑 `npm run check` 与 `npm run build:web` 全过（vitest **26 条**）；`ci-smoke.sh` → **148 条断言全过、退出码 0**（IT-6 新增 reviewed 前后回填 2 条）。种子库重置后浏览器实测：地图区界渲染、区县筛选联动、点位点击跳详情、房源评价展示与「已评价」置灰全部通过 |
| AI 回复链接渲染修复复跑（v1.10，2026-09-22） | 本轮为前端缺陷修复（站内链接前缀判定 `#:` → `#/`）+ 前端单测新增：前端 `npm run check`（ESLint + Prettier + vitest **32 条**）与 `npm run build:web` 全过；后端未改动（维持 213 条基线）；集成冒烟断言数不变（148 条），未复跑 |
| 客服场景三条断言同时失败排障（v1.4，CI 实录） | 现象：`FR-13 客服返回非空回答` / `模型实际调用了 searchKnowledge 工具` / `NFR-05 客服回答附带知识库来源引用` 三条同时失败，而同轮 FR-12 找房场景全过，后端日志**一条 WARN 都没有**。定位：聚合通道为推理模型，流式分片里 `delta.reasoning` 先占满预算、`finish_reason=length` 时正文与工具调用可能全空；适配器只累积 `delta.content` 与 `tool_calls`，全空时返回一句伪造的「抱歉，这次没有生成有效回答」且一个 delta 都不发——前端只能看到空白气泡、CI 只看到「回答为空」；FR-12 未暴露是因为找房场景的兜底条件（scene==1 且回答过短）恰好会触发非流式重跑，客服场景按设计不重跑。修复：① 空返回改为抛错（携带 `finish_reason` 与用量）并按可自愈传输层故障（空返回 / 限流 / 5xx / 超时）非流式重跑一次、4xx 不重跑；② 流式分支补 HTTP 状态码校验与 `CompletionException` 解包；③ 正文只在终止消息里给出时补走 delta 通道；④ 单轮 token 上限 2048 → 4096；⑤ 冒烟脚本为找房与客服各加一条「流内无失败载荷」断言，把模型侧故障与那三条业务断言区分开 |
| 集成冒烟 | 本机真实 MySQL 8 + Redis + 运行中后端 → **121 条断言全过，退出码 0**（连续 6 轮稳定） |
| 失败路径 | `BASE_URL` 指向空端口 → 退出码 7 并提示后端未就绪，门禁有效 |
| lockfile 一致性 | `npm ci --dry-run` 通过（新增测试依赖已同步进 `package-lock.json`） |
| CI 首跑（GitHub Actions） | 失败：前两次登录取不到 token → 连锁 1007/5000，脚本以退出码 2 中断。定位为启动竞态（见 §2.3.1，非业务缺陷），已按上述两层修复 |
| 修复后回归 | 本机以"清空库 → 起后端 → 立刻跑脚本"复现同一竞态：修复前 `login xiaochen` 得 `1002`、`/houses.total=0`；修复后脚本自动等待并 **121 条断言全过、退出码 0** |
| 异常路径验证 | 种子未就绪 → 退出码 2 + 排查提示；关键 id 缺失 → 退出码 1 + 失败结论（不再是 jq 解析错误） |
| 真实模型口径首跑 | 失败 4 条：分析结果 `model` 恒为 `rule-engine`（实为 14:15 的旧 jar——上一步建表静默失败打断了 `&&` 链，打包未执行）与客服 `citations=0`；当时归因于聚合网关**流式响应截断丢失 tool_calls**，以"非流式重跑兜底"修复后 **125 条全过**。**后续复核更正**：抓取原始 SSE 分片确认网关流式分片完整，真实根因是 langchain4j 0.35 的流式组装器在正文非空时丢弃 tool_calls；改自研适配器后本轮复跑仍 **125 条全过**，且后端日志中不再出现兜底重跑记录 |
| 无模型反向门禁 | 另起无凭据实例实测：AI 对话/定价/识别填充/合同解读/引擎探针 **6/6 全部 4001**，零兜底 |
| 建表步骤加固 | 本机复现了"ping 成功但建表失败"的窗口，作业已改为重试 + 表数校验（≥18）+ 不吞错误输出 |
| 未在本机执行的步骤 | `actions/setup-*`、服务容器创建、Artifacts 上传等 GitHub 托管步骤（需托管 runner；已通过 `act` 结构校验与等价命令本地验证） |
| Action 运行时升级（v1.1） | 逐一核对各 Action 各版本的 `action.yml` → `runs.using`：`checkout` v5/v6/v7、`setup-java` v5/v6、`setup-node` v5/v6/v7 均为 `node24`；`upload-artifact` 需 **v6** 起才是 `node24`（v5 仍为 `node20`，故未停在 v5）。升级后复核：`act -l -W .github/workflows/ci.yml` 三作业均可解析；`actionlint` 对 `ci.yml` 零 error（仅 2 条既有 `SC2012` info，与本次无关），并已验证 actionlint 会校验 Action 输入名（对刻意注入的错误输入名可报错），故"零 error"不是空结论 |
