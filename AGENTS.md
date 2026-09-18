# AGENTS.md — RentAgent 工作区指南

课程设计项目：基于 AI 智能体的房屋租赁平台（"对话即服务"）。**全部文档与注释用中文**，提交信息也用中文
（`feat:` / `fix:` / `style:` 前缀 + 中文摘要）。

## 目录与职责

| 路径 | 说明 |
| ---- | ---- |
| `server/` | Spring Boot 3.3.4（Java 17）+ MyBatis-Plus 3.5.7。分层：`controller/` → `service/` → `mapper/` → `entity/`；`dto/`（入参与响应 record）、`common/`（`R`/`ErrorCode`/`BizException`/`JsonColumns`）、`security/`（JWT 过滤器 + `@RequireRole` 拦截器）、`config/`、`agent/`（LLM 网关与智能体） |
| `frontend/` | React 18 + TS strict + Vite 5 + antd 5 + ECharts。**一套代码两种分发**：Web 版与 Electron 桌面端共用 `frontend/dist` |
| `scripts/` | `ci-smoke.sh`（集成冒烟门禁，事实上的接口契约测试）、`ci-no-model-check.sh`（反向门禁）、`smoke.sh`（人工排障打印版） |
| `docker/mysql-init/01_schema.sql` | 18 张表的建表脚本（外键与中文 COMMENT 齐全，是《数据库表》生成源） |
| `doc/` | 课程六阶段交付物；`.md` 是底稿、`.docx/.pdf/.xlsx` 是提交件。见 `doc/README.md` |
| `.github/workflows/ci.yml` | 三作业并行：`backend-test` / `frontend-test` / `integration-smoke`（后者含无模型门禁） |

## 常用命令

```bash
# 基础设施（必须点名服务：裸 docker compose up -d 会连带把 server 容器一起起来）
docker compose up -d mysql redis

# 后端（8080）
cd server && mvn -q compile            # 改完先编译，避免 spring-boot:run 跑旧 class
./run-dev.sh                            # 本地开发脚本，含个人 API Key（已 gitignore，勿提交）
mvn test                                # 单测 190 条，不连库/缓存/网络
mvn -Dtest=HouseServiceTest test        # 单测单类（配合 -DfailIfNoSpecifiedTests=false 更省事）

# 前端（5173；浏览器请用 localhost 而非 127.0.0.1，CORS 白名单按来源区分）
cd frontend && npm run dev
npm run check          # = lint + format:check + test ← 提交前必跑，等价于 CI 的 frontend-test 前 3 步
npm run format         # Prettier 自动修；CI 会卡格式（见"门禁"一节）
npm run build:web      # tsc --noEmit && vite build（desktop 与 Web 共用该产物）

# 集成冒烟（需 MySQL + Redis + 运行中的后端；130 条断言，退出码 0 通过 / 1 断言失败 / 2 前置未就绪）
bash scripts/ci-smoke.sh
BASE_URL=http://localhost:8081 bash scripts/ci-no-model-check.sh   # 需另起一个无 AI 凭据的实例

# 文档重出（勿手改 docx/pdf）
python3 "doc/03.概要设计/assets/build_doc.py"        # 概要设计.docx + .pdf（两遍校准 TOC 页码）
python3 "doc/03.概要设计/assets/gen_db_dict.py"      # 数据库表.xlsx（源为 01_schema.sql）
python3 "doc/01.需求定义/assets/build_reqdef.py"     # 需求定义说明书.docx + .pdf
```

## 门禁与风格（改完必须过）

- **前端四步**：ESLint → Prettier `format:check` → vitest → `tsc --noEmit && vite build`。
  只跑 `tsc` 与 `eslint` 会漏掉 Prettier 而在 CI 挂掉（已踩过）。本机一条命令全跑：`npm run check`。
- Prettier 参数是**按现有代码多数派**定的（`semi:false`、`singleQuote`、`trailingComma:none`、
  `arrowParens:avoid`、`printWidth:100`），见 `frontend/prettier.config.js`；缩进/换行见 `frontend/.editorconfig`。
- 后端无可执行 formatter，风格靠约定：**控制器只做编排**（不写业务、不直接拼 `R.err(...)`、不碰 mapper），
  业务规则/属主校验/通知/留痕放 service；错误一律 `throw new BizException(ErrorCode.X)` 交给全局处理器。
- 改动验收清单：`mvn test` + `npm run check` + `bash scripts/ci-smoke.sh` 三件套。

## 前后端契约约定（改动时容易踩）

- 统一响应 `{code, message, data}`，`code=0` 成功；错误码分段 1xxx 用户 / 2xxx 房源 / 3xxx 交易 / 4xxx AI /
  5xxx 系统，集中在 `ErrorCode`。**同分段内语义相近的场景复用同一码、由 message 区分**（如 1002 覆盖
  登录失败/未登录/原密码不正确，1006 覆盖用户或举报不存在）——不要为此新增码。
- 分页统一 `PageVO{list,total,page,size}`；**列表页一律服务端分页**（前端 `usePagedList`），
  不要写死 `size=50` 再在前端假分页；`page/size` 默认 10、上限 50；地图找房这类点位接口不分页但设 300 上限。
- 列表响应中"跨表拼装"的行一律用 **record 专用 VO**（`TradeDto.*VO`、`AdminDto.*VO`），不用
  `Map<String,Object>`：OpenAPI 要出 schema、`frontend/src/types.ts` 要对齐；改 VO 字段名等于改前端类型。
- 同一逻辑字段只保留一种形状。`house.facilities` 是 JSON 列但实体侧用
  `@TableField(typeHandler = JacksonTypeHandler.class)` + `@TableName(autoResultMap = true)` 统一成
  `List<String>`；**新增 JSON 列请照此办理**，并在前端直接用数组，不要写"字符串或数组"的兼容分支。
- JSON 列的读写统一走 `common/JsonColumns`（容错解析不抛异常：失败退化为空集合或原字符串）。
- 审核/处置类写操作必须留痕（`AuditLogService.log`，对应 FR-07/22/23），通知与留痕都在 service 内完成。
- 接口路径与字段是**双份约定**：改接口形状要同步 `scripts/ci-smoke.sh` 的断言（它按 JSON 路径取值）
  与 `doc/03.概要设计/`（`概要设计说明.md` + `assets/content.py` 的 5.2 接口规约表）。

## AI 层规则（最容易被误解的一块）

- **AI 能力必须调真实模型**：未配置模型或调用失败统一返回 4001 并附可读原因，**不做任何规则/模拟兜底**
  （规则引擎 `MockAgent` 已删除）。因此不要提议"离线降级""本地假回答"。
- 三种协议（openai-chat-completions / anthropic-messages / openai-responses）的模型客户端**均为自研**：
  厂商客户端在"同一轮正文与工具调用并存"时会丢 tool_calls。改适配器时保持"正文与 tool_calls 分别累积后合并"。
- **响应既无正文也无工具调用时必须报错**（`LlmEmptyResponseException`），不得伪造回答；可自愈故障
  （空返回 / 429 / 5xx / 超时）由 `LlmAgent` 非流式重跑一次，4xx 不重跑。
- 聚合通道是**推理模型**：思维链计入 `max_tokens`（实测一轮 1.6k~2.0k tokens），预算过紧会被
  `finish_reason=length` 截断成"既无正文也无工具调用"。`ai.max-tokens` 默认 4096，可用 `AI_MAX_TOKENS` 覆盖。
- 流式契约：`delta` 事件 `{"delta":"…"}` 逐字；`done` 事件带 `messageId/content/citations/transferred/latencyMs`；
  失败发 `{error:"…"}`。**正文只在终止消息里给出时必须补走 delta 通道**。`frontend/src/api.ts` 的 `ssePost`
  保证 `onDone`/`onError` 恰好触发一个；SSE 走原生 fetch，**不走 axios 拦截器，401 处理要手动保持一致**。
- 客服场景（scene 2）与找房场景（scene 1）的强化重跑提示词不同；工具调用留痕写在 `ai_chat_message`（role=3）。

## 前端约定

- 接口基址只能来自 `src/runtime.ts`（`API_BASE` / `apiUrl()` / `assetUrl()`）：桌面端以 `file://` 加载，
  写相对路径或绝对 URL 会直接坏掉桌面端。路由是 `HashRouter`，跳转/兜底清登录态时用 `location.hash`。
- 请求统一走 `src/api.ts` 的 `http`（拦截器已解包 `data`，业务码非 0 会弹错并 reject）；不要另建 axios 实例。
- 无路径别名，一律相对导入。类型集中在 `src/types.ts`，与后端 record 同名同形。
- 环境钉死：React 18、TS 5.x、`@types/react` 18、antd 5、vitest 2.x + jsdom ^25（**jsdom 无 localStorage，
  测试里要自己桩**）、electron 40.10.2（npm 会拦 postinstall，Electron 二进制需手工解压）。

## 环境陷阱（本机与 CI 都踩过）

- **空库首启竞态**：`DataInitializer` 是 `CommandLineRunner` + `@Transactional`，Web 端口与连接池**先于种子提交**就绪。
  就绪判据必须**查库**（轮询不鉴权的 `GET /houses?size=1` 直到 `total ≥ 1`），**绝不能用"反复尝试登录"探测**
  （登录失败会计入 Redis 计数，5 次锁 10 分钟）。
- `docker compose down -v` 会连种子库一起删；本机若有**两个 Docker daemon**，租库的容器/数据卷只在
  `desktop-linux` 上下文里（`sudo docker` 走 `default`，看不到）。
- `pkill -f 'spring-boot:run'` 会**匹配到自己所在的命令行**从而杀掉当前 shell；用 `pkill -f 'spring-boot[:]run'`
  这类括号规避写法。
- 改后端后先 `mvn compile` 再 `spring-boot:run`；CI 跑的是 `mvn package` 出的 jar。
- 聚合网关按 UA 放行（默认 okhttp/java UA 会被限流或截断流式响应），`application.yml` 里已覆盖 `user-agent`。
- CI 的集成作业注入 `secrets.AGGREGATOR_API_KEY`；无模型门禁另起 `SERVER_PORT=8081` 且把 AI 变量置空。

## 文档纪律

- 代码行为/接口/数据结构一变，就要同步：`doc/03.概要设计/概要设计说明.md`（升版本 + 变更记录）→
  用脚本重出 `概要设计.docx` / `.pdf`；`doc/03.概要设计/assets/content.py`（**docx 的正文源**，接口规约表与
  变更记录都在这里）；`doc/04.编码/单元测试用例设计.md`、`doc/05.集成测试/测试用例设计.md` 与
  `CI流水线设计.md`（这两份无 docx，改 md + 升版本号即可）。
- 只改 md 不改 docx 的例外：04/05 三份 md；数据库结构未变则无需重出《数据库表》与 ER 图。
- `.docx`/`.pdf`/`.xlsx` 是二进制产物，**不要手改**，改 `content.py` / 脚本后重跑生成。
- 改敏感区域前先读：`doc/03.概要设计/概要设计说明.md`（接口与数据结构口径）、
  `doc/05.集成测试/CI流水线设计.md`（门禁与排障口径）、`scripts/ci-smoke.sh`（真实契约）。
- `doc/README.md` 提到 04/05 的内容与最新计数可能滞后；`README.md` 有两处已过时描述
  （"未配 Key 时为 rule-engine" 应为 4001；"概要设计说明 v1.5" 已到 v1.10），以代码与 doc/03 为准。
