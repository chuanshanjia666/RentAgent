# RentAgent CI 流水线设计

> 项目名称：RentAgent —— 基于 AI 智能体的房屋租赁系统
> 文档版本：v1.0
> 编制日期：2026-09-17
> 编制：项目组测试担当（陈玄晔）
> 关联文档：《单元测试用例设计》、《测试用例设计》（集成层）、`.github/workflows/ci.yml`、`scripts/ci-smoke.sh`
> 适用阶段：五、集成测试（持续集成）

---

## 一、目标与门禁原则

1. **每次推送/PR 都跑得动**：单次流水线目标 10 分钟量级，不依赖任何付费服务或私有凭据。
2. **三层门禁，逐层加严**：静态/单元（秒级）→ 构建（分钟级）→ 集成冒烟（全栈，~2 分钟）。
3. **零密钥可跑**：不注入任何大模型 Key，正好验证"无 Key 时降级规则引擎"这一演示必备路径；
   需要真实模型的评测另行人工执行（见《测试用例设计》第七节遗留项 8）。
4. **本地与 CI 同源**：CI 调用的就是开发者本地用的命令与脚本，不写第二套逻辑。

| 门禁 | 命令 | 拦截什么 |
| ---- | ---- | -------- |
| 后端单元测试 | `mvn -B -ntp test`（148 条） | 业务状态机、权限、金额/日期计算、加解密回归 |
| 前端单测 | `npm test`（vitest，20 条） | 运行时地址解析、状态字典、SSE 流式解析 |
| 前端类型与构建 | `npm run build:web`（`tsc --noEmit` + vite） | TS 严格模式下的类型错误、构建失败 |
| 集成冒烟 | `bash scripts/ci-smoke.sh`（121 条断言） | 跨模块业务闭环、真实 MySQL/Redis 语义、鉴权链路、降级路径 |

---

## 二、流水线结构

文件：`.github/workflows/ci.yml`。触发条件：`push`（main 分支）、`pull_request`、`workflow_dispatch`（手动重跑）。
`permissions: contents: read`（只读，不回写仓库）；`concurrency` 按分支分组并 `cancel-in-progress`（新提交取消旧流水线）；
全局 `TZ=Asia/Shanghai`（保证冒烟脚本的 `date` 计算与后端 `LocalDate.now()` 落在同一天）。

三个作业**并行**执行，互不阻塞，因此一次运行即可看到全部三层的结论。

### 2.1 作业一：`backend-test` 后端单测

| 步骤 | 动作 |
| ---- | ---- |
| 检出代码 | `actions/checkout@v4` |
| 环境 | `actions/setup-java@v4`（temurin 17，`cache: maven`） |
| 执行 | `mvn -B -ntp test`（工作目录 `server/`） |
| 归档 | `actions/upload-artifact@v4` 上传 `server/target/surefire-reports/`（`if: always()`，失败可下载报告定位） |

不启任何服务容器：本层用例全部为 Mockito 打桩，不连库、不连缓存、不连网络。

### 2.2 作业二：`frontend-test` 前端单测与构建

| 步骤 | 动作 |
| ---- | ---- |
| 检出代码 | `actions/checkout@v4` |
| 环境 | `actions/setup-node@v4`（Node 20，`cache: npm`，`cache-dependency-path: frontend/package-lock.json`） |
| 依赖 | `npm ci --no-audit --no-fund`（严格按 lockfile，保证可复现） |
| 单测 | `npm test`（vitest run） |
| 构建 | `npm run build:web`（类型检查 + 生产构建，Web 版与桌面端共用同一份 `dist`） |
| 归档 | 上传 `frontend/dist/`（`if-no-files-found: error`，构建产物缺失即失败） |

作业级环境变量 `ELECTRON_SKIP_BINARY_DOWNLOAD=1`：桌面端打包不在 CI 范围，
跳过 Electron 二进制下载可显著缩短依赖安装时间（本地打 AppImage/deb 时不受影响）。

### 2.3 作业三：`integration-smoke` 集成冒烟

| 步骤 | 动作 |
| ---- | ---- |
| 服务容器 | `mysql:8.0`（库/账号与 `docker-compose.yml` 一致，含 healthcheck）与 `redis:7` |
| 初始化库 | 安装 `mysql-client` → 等 MySQL 就绪 → 执行 `docker/mysql-init/01_schema.sql` → 打印表数量（应 18） |
| 构建 | `mvn -B -ntp -DskipTests package`（产出可执行 jar） |
| 启动 | 后台启动 jar（注入 `MYSQL_*`/`REDIS_*`/`JWT_SECRET`/`AES_KEY`），轮询 `/api/v1/ai/engine` 直到就绪（最多 2 分钟），超时打印后端日志尾部 |
| 冒烟 | `bash scripts/ci-smoke.sh`（121 条断言，失败即非 0 退出） |
| 归档 | 失败时上传 `/tmp/backend.log` 与 SSE 原始输出 |

种子数据无需准备：空库启动时由应用 `DataInitializer` 自动灌入演示账号、房源与知识库。

### 2.4 流水线拓扑

```
push / PR / 手动
        │
        ├── backend-test      JDK17 ── mvn test（148 条）──────────────┐
        ├── frontend-test     Node20 ── npm ci → vitest → build:web ──┤ 并行
        └── integration-smoke JDK17 + mysql:8.0 + redis:7             │
                              └─ 建表 → 打包 → 启动 → ci-smoke.sh（121 条）┘
                                        ↓
                          全部成功 = 门禁通过；任一失败 = 阻断合并
```

---

## 三、环境与配置

| 变量 | 取值 | 说明 |
| ---- | ---- | ---- |
| `MYSQL_HOST` / `MYSQL_USER` / `MYSQL_PASSWORD` | `127.0.0.1` / `rentagent` / `rentagent123` | 与 compose 一致；服务容器端口映射到 runner 本地 |
| `REDIS_HOST` | `127.0.0.1` | 验证码与登录失败计数 |
| `JWT_SECRET` | CI 专用测试串 | 真实环境由部署方注入，不入库 |
| `AES_KEY` | CI 专用测试 Base64 密钥 | 同上；**不可**复用生产密钥 |
| `UPLOAD_DIR` | `${{ runner.temp }}/rentagent-uploads` | 写在**步骤级** env：job 级 env 不提供 `runner` 上下文 |
| `BASE_URL` | `http://localhost:8080` | 冒烟脚本基址 |
| `TZ` | `Asia/Shanghai` | 全流程统一时区 |
| `AI_*` / `AGGREGATOR_*` / `ANTHROPIC_*` | **不设置** | 触发规则引擎降级；注意仓库内 `server/run-dev.sh` 含个人 Key 且已被 `.gitignore` 忽略，CI 绝不引用 |

安全约定：
- 密钥一律走 GitHub Secrets / 环境变量，仓库内不保存任何真实 Key（当前 CI 不需要任何外部凭据）；
- 集成作业使用一次性数据库容器，无需清理逻辑，也不会污染任何持久数据。

---

## 四、本地与 CI 的一致性

| 环节 | 本地开发者 | CI |
| ---- | ---------- | -- |
| 数据库 | `docker compose up -d mysql redis`（`mysql:8.0` / `redis-stack-server`） | 服务容器 `mysql:8.0` / `redis:7`（均为 Redis 基础命令，模块无差异） |
| 建表 | `docker exec -i <mysql> mysql -uroot -p... rentagent < docker/mysql-init/01_schema.sql` | 同样执行该 SQL 文件 |
| 后端启动 | `mvn spring-boot:run`（`server/run-dev.sh` 注入个人 AI Key） | `java -jar target/rentagent-server-*.jar`（不注入 AI Key） |
| 集成验证 | `bash scripts/ci-smoke.sh` | 同一条命令 |
| 单测 | `mvn test` / `npm test` | 同上 |

差异说明：本地若配置了真实模型 Key，AI 相关断言（`IT-7-08`、`IT-9-*`）会因引擎标识与回答文本变化而失败。
此类场景请临时 `unset AI_API_KEY AGGREGATOR_API_KEY AI_BACKEND` 后执行，或将 AI 断言单独分组执行。

---

## 五、失败处理与复现

1. **单测失败**：作业内直接看到 surefire 摘要，并可从 Artifacts 下载完整报告；本地用 `mvn test -Dtest=<类名>` 复现。
2. **前端失败**：日志含 vitest 用例名与断言差异；本地用 `npx vitest run src/<文件>` 复现。
3. **冒烟失败**：脚本逐条打印 `✓/✗` 与"期望/实际"，末尾给出通过/失败计数；结合上传的后端日志与 SSE 原始文件定位。
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

---

## 六、扩展与后续

| 序 | 事项 | 前置条件 / 说明 |
| -- | ---- | --------------- |
| 1 | 镜像发布到 GHCR | 仓库当前**没有 Dockerfile**（`docker-compose.yml` 的 `server` 服务引用了不存在的构建上下文）；补齐 Dockerfile 后可加 `packages: write` 的发布作业 |
| 2 | 覆盖率报告（JaCoCo / v8） | 加 `jacoco:report` 与 `vitest --coverage`，并把阈值设为门禁（建议行覆盖 ≥60% 起步） |
| 3 | Electron 桌面端打包 | 需 `electron-builder` + Linux 打包依赖，耗时较长；建议只在打 tag 时执行并上传 AppImage/deb |
| 4 | 性能基准（NFR-01/NFR-02） | 引入 k6 场景脚本，对核心查询接口做 50 并发基准并记录首字延迟 |
| 5 | 真实模型通道回归 | 用 GitHub Secrets 注入演示用 Key，增加一个**非阻塞**（`continue-on-error`）的对话质量作业 |
| 6 | 依赖漏洞扫描 | 加 `dependency-review-action`（PR）与 `npm audit`/OWASP 依赖检查 |
| 7 | 冒烟脚本的模型无关性 | 若演示通道从规则引擎切到真实模型，需为 `IT-7-08`、`IT-9-*` 增加按 `engine` 分支断言的适配 |

---

## 七、执行与验证记录

| 项目 | 内容 |
| ---- | ---- |
| 工作流结构校验 | `act -l -W .github/workflows/ci.yml` 通过（三个作业均可解析）；期间修正一处真实错误：`${{ runner.temp }}` 不能用于 job 级 `env`（`runner` 上下文在 job 级 env 不可用） |
| 后端单测 | 本机 `mvn test` → **148 条全过** |
| 前端单测与构建 | 本机 `npm test` → **20 条全过**；`npm run build:web` → 通过 |
| 集成冒烟 | 本机真实 MySQL 8 + Redis + 运行中后端 → **121 条断言全过，退出码 0**（连续 6 轮稳定） |
| 失败路径 | `BASE_URL` 指向空端口 → 退出码 7 并提示后端未就绪，门禁有效 |
| lockfile 一致性 | `npm ci --dry-run` 通过（新增测试依赖已同步进 `package-lock.json`） |
| 未在本机执行的步骤 | `actions/setup-*`、服务容器创建、Artifacts 上传等 GitHub 托管步骤（需托管 runner；已通过 `act` 结构校验与等价命令本地验证） |
