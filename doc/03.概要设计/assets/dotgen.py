#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""RentAgent 概要设计配图生成器（graphviz dot）。

生成：
  fig_2_1_system          图2-1  系统结构图
  fig_3_x_mod_<key>       各模块模块结构图
  fig_3_x_cls_<key>       各模块类图
  fig_4_1_er              图4-1  数据库表间关系图（E-R）
"""
import subprocess
import pathlib

OUT = pathlib.Path(__file__).parent / "figures"
OUT.mkdir(exist_ok=True)

CN = "Microsoft YaHei"
MONO = "Microsoft YaHei"

GLOBAL = f'''
  graph [fontname="{CN}", bgcolor="white", margin=0.15];
  node  [fontname="{CN}", fontsize=11, shape=box, style="filled,rounded",
         fillcolor="#eef4fb", color="#4a7ebb", penwidth=1.1, margin="0.14,0.09"];
  edge  [fontname="{CN}", fontsize=9, color="#5c5c5c", penwidth=1.0, arrowsize=0.7];
'''


def render(name, dot_source, dpi=170):
    src = OUT / f"{name}.dot"
    src.write_text(dot_source, encoding="utf-8")
    png = OUT / f"{name}.png"
    cmd = ["dot", f"-Gdpi={dpi}", "-Tpng", "-o", str(png), str(src)]
    subprocess.run(cmd, check=True)
    print("rendered", png.name)


# ─────────────────────────────────────────────────────────────────────────────
# 图2-1 系统结构图
# ─────────────────────────────────────────────────────────────────────────────
def fig_system():
    d = f'''digraph system {{
  rankdir=TB; splines=polyline; nodesep=0.32; ranksep=0.40;
{GLOBAL}
  node [width=2.05, height=0.5];

  subgraph cluster_pres {{
    label="表现层（三端单仓单应用：React 18 + TypeScript + Vite + Ant Design 5 + ECharts · hash 路由按角色分区）\\n分发形态：浏览器 Web 版 / Electron 桌面端（共用同一份构建产物，桌面端不新增功能）";
    fontsize=11; fontcolor="#31527a"; style="rounded,dashed"; color="#a8c4e0"; margin=10;
    t1 [label="租客端\\n找房 / AI 对话 / 预约 / 签约", fillcolor="#dceaf8", width=2.6];
    t2 [label="房东端\\n发布 / 定价 / 预约处理 / 订单", fillcolor="#dceaf8", width=2.6];
    t3 [label="管理端\\n审核 / 用户 / 举报 / 数据看板", fillcolor="#dceaf8", width=2.6];
    {{rank=same; t1; t2; t3;}}
  }}

  subgraph cluster_gate {{
    label="接入层"; fontsize=11; fontcolor="#31527a"; style="rounded,dashed";
    color="#a8c4e0"; margin=8;
    ng [label="Nginx：静态资源托管 · HTTPS 终止 · /api 反向代理 → server:8080 · /uploads 图片卷",
        fillcolor="#e7f2e7", color="#5c9c5c", width=5.6];
  }}

  subgraph cluster_api {{
    label="接口层（RESTful API，统一前缀 /api/v1）"; fontsize=11; fontcolor="#31527a";
    style="rounded,dashed"; color="#a8c4e0"; margin=8;
    api [label="统一响应 R&lt;T&gt;{{code,message,data}} · JWT 鉴权过滤器 · @RequireRole 角色拦截器\\n全局异常处理 · 参数校验 · SSE 流式端点",
         fillcolor="#fdf3dc", color="#d1a542", width=5.6];
  }}

  subgraph cluster_biz {{
    label="业务模块层（七大模块，详见第 3 章）"; fontsize=11; fontcolor="#31527a";
    style="rounded,dashed"; color="#a8c4e0"; margin=8;
    m1 [label="用户与权限\\nFR-01~04", fillcolor="#dceaf8", width=1.5];
    m2 [label="房源管理\\nFR-05~08", fillcolor="#dceaf8", width=1.5];
    m3 [label="检索与推荐\\nFR-09~11/25", fillcolor="#dceaf8", width=1.5];
    m4 [label="交易与合同\\nFR-17~19", fillcolor="#dceaf8", width=1.5];
    m5 [label="评价与消息\\nFR-20/21", fillcolor="#dceaf8", width=1.5];
    m6 [label="后台管理\\nFR-22~24", fillcolor="#dceaf8", width=1.5];
    m7 [label="AI 智能体服务\\nFR-12~16", fillcolor="#f3e2f7", color="#9b6fb0", width=1.7];
    {{rank=same; m1; m2; m3; m4; m5; m6; m7;}}
  }}

  subgraph cluster_tech {{
    label="技术分层"; fontsize=11; fontcolor="#31527a";
    style="rounded,dashed"; color="#a8c4e0"; margin=8;
    l1 [label="Controller 层\\n接收请求 / 参数校验 / 统一响应", fillcolor="#fdf3dc", color="#d1a542", width=3.3];
    l2 [label="Service 业务层\\n状态机 · 事务 · 属主校验", fillcolor="#fdf3dc", color="#d1a542", width=3.0];
    l3 [label="智能体层 Agent\\nLangChain4j 编排 · 工具注册 · RAG · LLM 网关", fillcolor="#f3e2f7", color="#9b6fb0", width=3.4];
    l4 [label="Mapper 持久化层 · MyBatis-Plus 数据访问", fillcolor="#fdf3dc", color="#d1a542", width=4.2];
    {{rank=same; l1; l2; l3;}}
  }}

  subgraph cluster_data {{
    label="数据层与外部服务"; fontsize=11; fontcolor="#31527a"; labelloc="b";
    style="rounded,dashed"; color="#a8c4e0"; margin=8;
    d1 [label="MySQL 8\\n业务库 18 张表", fillcolor="#e7f2e7", color="#5c9c5c", width=1.9];
    d2 [label="Redis 7\\n验证码 / 限流 / 热度", fillcolor="#e7f2e7", color="#5c9c5c", width=1.9];
    d3 [label="Redis Stack\\n向量索引（RAG 升级位）", fillcolor="#e7f2e7", color="#5c9c5c", width=1.9];
    d4 [label="文件存储\\n房源图片本地卷", fillcolor="#e7f2e7", color="#5c9c5c", width=1.9];
    ex [label="大模型服务（HTTPS 出网）\\n任意兼容模型（DeepSeek / Claude / GPT / GLM …）\\n三协议适配 · 模型无关 · 未配置/调用失败即报错",
        fillcolor="#f7e7e7", color="#c07070", width=3.1];
    {{rank=same; d1; d2; d3; d4; ex;}}
  }}

  t1 -> ng;  t2 -> ng;  t3 -> ng;
  ng  -> api;
  api -> m1; api -> m2; api -> m3; api -> m4; api -> m5; api -> m6; api -> m7;
  m1 -> l1 [style=invis]; m2 -> l1 [style=invis]; m3 -> l1 [style=invis];
  m4 -> l1 [style=invis]; m5 -> l1 [style=invis]; m6 -> l1 [style=invis];
  m7 -> l3 [style=invis];

  l1 -> l2 [label="调用"];
  l2 -> l3 [label="工具调用"];
  l2 -> l4 [label="读写"];
  l4 -> d1; l4 -> d2; l4 -> d3; l4 -> d4;
  l3 -> ex [label="HTTPS", style=dashed];
}}
'''
    render("fig_2_1_system", d)


# ─────────────────────────────────────────────────────────────────────────────
# 模块结构图
# ─────────────────────────────────────────────────────────────────────────────
MODULES = [
    # key, 名称, 需求, controller, service, mapper, entity/其它
    ("auth", "用户与权限模块", "FR-01 ~ FR-04",
     ["AuthController"],
     ["AuthService"],
     ["SysUserMapper", "RealnameAuthMapper"],
     ["SysUser", "RealnameAuth"]),
    ("house", "房源管理模块", "FR-05 ~ FR-08",
     ["HouseController", "AdminController\\n(审核端点)"],
     ["HouseService"],
     ["HouseMapper", "HouseImageMapper"],
     ["House", "HouseImage"]),
    ("search", "检索与推荐模块", "FR-09 ~ FR-11、FR-25",
     ["HouseController\\n(检索端点)", "FavoriteController"],
     ["SearchService", "HouseService\\n(推荐/详情)"],
     ["HouseMapper", "FavoriteMapper"],
     ["House", "Favorite"]),
    ("trade", "交易与合同模块", "FR-17 ~ FR-19",
     ["AppointmentController", "ContractController"],
     ["AppointmentService", "ContractService"],
     ["ViewingAppointmentMapper", "ContractMapper", "LeaseOrderMapper", "RentBillMapper"],
     ["ViewingAppointment", "Contract", "LeaseOrder", "RentBill"]),
    ("interact", "评价与消息模块", "FR-20、FR-21",
     ["ReviewController", "NotificationController", "ReportController\\n(举报入口)"],
     ["ReviewService", "NotificationService", "ReportService"],
     ["ReviewMapper", "NotificationMapper", "ReportMapper"],
     ["Review", "Notification", "Report"]),
    ("admin", "后台管理模块", "FR-22 ~ FR-24",
     ["AdminController"],
     ["AdminService", "AdminChatService\\n(对话审计)", "ReportService\\n(处理)",
      "AuditLogService", "AnalysisService\\n(检测)"],
     ["SysUserMapper", "HouseMapper", "ReportMapper", "AuditLogMapper",
      "AiChatSessionMapper /\\nAiChatMessageMapper\\n(审计读取)"],
     ["AuditLog", "Report\\n聚合查询(看板)"]),
    ("ai", "AI 智能体服务模块", "FR-12 ~ FR-16",
     ["AiController"],
     ["ChatService", "KbService", "AnalysisService"],
     ["AiChatSessionMapper", "AiChatMessageMapper", "AiAnalysisMapper", "KbDocumentMapper", "KbChunkMapper"],
     ["AiChatSession", "AiChatMessage", "AiAnalysis", "KbDocument", "KbChunk"]),
]


def fig_module_structure(key, name, frs, ctrls, svcs, mappers, entities):
    def esc(x):
        return x.replace("\\n", "&#10;").replace("&", "&amp;").replace("&#10;", "\\n")

    ctrl_nodes = "\n".join(
        f'  c{i} [label="{c}", fillcolor="#fdf3dc", color="#d1a542"];' for i, c in enumerate(ctrls))
    svc_nodes = "\n".join(
        f'  s{i} [label="{s}", fillcolor="#eaf6ea", color="#5c9c5c"];' for i, s in enumerate(svcs))
    map_nodes = "\n".join(
        f'  p{i} [label="{m}", fillcolor="#efe9f7", color="#8a6fb0"];' for i, m in enumerate(mappers))
    ent_nodes = "\n".join(
        f'  e{i} [label="{e}", fillcolor="#f7f0e6", color="#b08a5c", shape=box, style=filled];'
        for i, e in enumerate(entities))
    same_c = "  {rank=same; " + "; ".join(f"c{i}" for i in range(len(ctrls))) + ";}\n"
    same_s = "  {rank=same; " + "; ".join(f"s{i}" for i in range(len(svcs))) + ";}\n"
    same_p = "  {rank=same; " + "; ".join(f"p{i}" for i in range(len(mappers))) + ";}\n"
    same_e = "  {rank=same; " + "; ".join(f"e{i}" for i in range(len(entities))) + ";}\n"

    top_edge = "\n".join(f'  root -> c{i} [style=bold];' for i in range(len(ctrls)))
    c2s = "\n".join(f'  c{i} -> s{j};' for i in range(len(ctrls)) for j in range(len(svcs)))
    s2p = "\n".join(f'  s{i} -> p{j};' for i in range(len(svcs)) for j in range(len(mappers)))
    p2e = "\n".join(f'  p{i} -> e{j} [style=dotted, arrowhead=none, color="#8c8c8c"];'
                    for i in range(len(mappers)) for j in range(len(entities)))

    d = f'''digraph mod_{key} {{
  rankdir=TB; nodesep=0.28; ranksep=0.50; compound=true;
{GLOBAL}
  node [width=2.3, height=0.52];

  subgraph cluster_0 {{
    label="{name}　（{frs}）"; fontsize=13; fontcolor="#1f3a5f";
    style="rounded,filled"; fillcolor="#eef4fb"; color="#4a7ebb"; penwidth=1.4; margin=12;
    root [label="模块入口（Controller 层）", style="filled,rounded", fillcolor="#cfe0f5",
          color="#31527a", penwidth=1.6, width=3.0];
{ctrl_nodes}
{same_c}
    root -> c0 [style=invis];
{top_edge}
  }}

  subgraph cluster_1 {{
    label="Service 业务层"; fontsize=11; fontcolor="#2f6b2f";
    style="rounded,filled"; fillcolor="#f4fbf4"; color="#5c9c5c"; margin=10;
{svc_nodes}
{same_s}
  }}

  subgraph cluster_2 {{
    label="Mapper 持久化层"; fontsize=11; fontcolor="#5c4478";
    style="rounded,filled"; fillcolor="#f8f5fc"; color="#8a6fb0"; margin=10;
{map_nodes}
{same_p}
  }}

  subgraph cluster_3 {{
    label="实体 / 数据载体"; fontsize=11; fontcolor="#7a5a30";
    style="rounded,filled"; fillcolor="#fdfaf6"; color="#b08a5c"; margin=10;
{ent_nodes}
{same_e}
  }}

  c0 -> s0 [style=invis];
{c2s}
{s2p}
{p2e}
}}
'''
    render(f"fig_3_1_mod_{key}", d)


# ─────────────────────────────────────────────────────────────────────────────
# 类图（UML 三段式：类名 / 属性 / 方法）
# ─────────────────────────────────────────────────────────────────────────────
def uml(cls, stereotype, attrs, methods, color="#fdf3dc", border="#d1a542"):
    rows = "".join(
        f'<TR><TD ALIGN="LEFT" BALIGN="LEFT">{a}</TD></TR>' for a in attrs)
    mrows = "".join(
        f'<TR><TD ALIGN="LEFT" BALIGN="LEFT">{m}</TD></TR>' for m in methods)
    stereo = (f'<TR><TD BGCOLOR="{color}"><FONT POINT-SIZE="9" COLOR="#666666">'
              f'&#171;{stereotype}&#187;</FONT></TD></TR>') if stereotype else ""
    return (
        f'<<TABLE BORDER="0" CELLBORDER="1" CELLSPACING="0" CELLPADDING="3">'
        f'<TR><TD BGCOLOR="{color}"><B>{cls}</B></TD></TR>'
        f'{stereo}'
        f'<TR><TD ALIGN="LEFT" BALIGN="LEFT"><TABLE BORDER="0" CELLBORDER="0" CELLSPACING="0">'
        f'{rows}</TABLE></TD></TR>'
        f'<TR><TD ALIGN="LEFT" BALIGN="LEFT"><TABLE BORDER="0" CELLBORDER="0" CELLSPACING="0">'
        f'{mrows}</TABLE></TD></TR>'
        f'</TABLE>>')


CLASS_FIGS = {
 "auth": dict(
   title="用户与权限模块类图",
   nodes=[
     ("AuthController", "Controller", ["- authService: AuthService"], [
        "+ sendCaptcha(CaptchaReq): R&lt;Void&gt;",
        "+ register(RegisterReq): R&lt;TokenResp&gt;",
        "+ login(LoginReq): R&lt;TokenResp&gt;",
        "+ currentUser(): R&lt;Map&gt;",
        "+ updateProfile(UpdateProfileReq): R&lt;Void&gt;",
        "+ changePassword(ChangePasswordReq): R&lt;Void&gt;",
        "+ submitRealname(RealnameReq): R&lt;Void&gt;"], "#fdf3dc", "#d1a542"),
     ("AuthService", "Service", ["- userMapper: SysUserMapper",
        "- realnameMapper: RealnameAuthMapper", "- redis: StringRedisTemplate"], [
        "+ sendCaptcha(String): void",
        "+ register(RegisterReq): TokenResp",
        "+ login(LoginReq): TokenResp",
        "+ submitRealname(long, RealnameReq): void",
        "+ realnameOf(long): RealnameVO",
        "+ realnamePassed(long): boolean"], "#eaf6ea", "#5c9c5c"),
     ("JwtUtil", "security", ["- secret: String", "- expireHours: long"], [
        "+ issue(long, int): String",
        "+ verify(String): Claims"], "#f7e7e7", "#c07070"),
     ("JwtAuthFilter", "security", ["- jwtUtil: JwtUtil", "- userMapper: SysUserMapper"], [
        "+ doFilterInternal(req, resp, chain): void",
        "- 校验令牌有效性 / 用户禁用即时生效"], "#f7e7e7", "#c07070"),
     ("RoleInterceptor", "security", ["- handler: HandlerMethod"], [
        "+ preHandle(req, resp, handler): boolean",
        "+ 解析 @RequireRole 所需角色集合"], "#f7e7e7", "#c07070"),
     ("UserContext", "ThreadLocal 上下文", ["~ uid: Long", "~ role: Integer"], [
        "+ set(long, int): void",
        "+ uid(): long", "+ role(): int",
        "+ clear(): void"], "#eef4fb", "#4a7ebb"),
     ("CryptoUtil", "工具", ["- aesKey: byte[]"], [
        "+ aesEncrypt(String): String",
        "+ aesDecrypt(String): String",
        "+ sha256(String): String",
        "+ bcrypt(String): String"], "#efe9f7", "#8a6fb0"),
     ("SysUser", "Entity", ["- id: Long", "- username: String", "- password: String",
        "- phone: String", "- role: Integer", "- status: Integer", "- deleted: Integer"], [
        "（MyBatis-Plus 映射 sys_user）"], "#f7f0e6", "#b08a5c"),
     ("RealnameAuth", "Entity", ["- id: Long", "- userId: Long", "- realName: String",
        "- idCardNoEnc: String", "- idCardHash: String", "- status: Integer"], [
        "（MyBatis-Plus 映射 realname_auth）"], "#f7f0e6", "#b08a5c"),
   ],
   edges=[("AuthController","AuthService","调用"),("AuthService","SysUserMapper",""),
          ("AuthService","RealnameAuthMapper",""),("AuthService","CryptoUtil","加密/哈希"),
          ("JwtAuthFilter","JwtUtil",""),("JwtAuthFilter","UserContext","写入上下文"),
          ("RoleInterceptor","UserContext","读取角色"),("AuthController","JwtUtil","签发令牌"),
          ("JwtAuthFilter","SysUserMapper","状态校验"),("SysUserMapper","SysUser","映射"),
          ("RealnameAuthMapper","RealnameAuth","映射")]),

 "house": dict(
   title="房源管理模块类图",
   nodes=[
     ("HouseController", "Controller", ["- houseService: HouseService"], [
        "+ create(SaveReq): R&lt;House&gt;",
        "+ update(long, SaveReq): R&lt;Void&gt;",
        "+ changeStatus(long, StatusReq): R&lt;Void&gt;",
        "+ myHouses(page, size): R&lt;PageVO&lt;House&gt;&gt;",
        "+ detail(long): R&lt;Item&gt;",
        "+ search(SearchReq): R&lt;PageVO&lt;Item&gt;&gt;",
        "+ map(SearchReq): R&lt;List&lt;House&gt;&gt;",
        "+ recommend(int): R&lt;List&lt;House&gt;&gt;"], "#fdf3dc", "#d1a542"),
     ("AdminController", "Controller(管理员)", ["- adminService: AdminService"], [
        "+ pending(page, size): R&lt;PageVO&lt;House&gt;&gt;",
        "+ audit(long, Map): R&lt;Void&gt;",
        "+ fakeDetect(long): R&lt;DetectVO&gt;"], "#fdf3dc", "#d1a542"),
     ("HouseService", "Service", ["- houseMapper: HouseMapper",
        "- imageMapper: HouseImageMapper", "- authService: AuthService",
        "- notificationService: NotificationService"], [
        "+ create(SaveReq, long): House",
        "+ update(long, SaveReq, long): void",
        "+ changeStatus(long, String, long): void",
        "+ audit(long, boolean, String, long): void",
        "+ detail(long): Item",
        "+ toItem(House, User): Item",
        "+ recommend(Long, int): List&lt;House&gt;",
        "+ ST_PENDING/ST_PASSED/ST_REJECTED/ST_ONLINE/ST_OFFLINE/ST_RENTED",
        "- 状态机校验 checkTransition()"], "#eaf6ea", "#5c9c5c"),
     ("HouseMapper", "Mapper", ["&#171;extends BaseMapper&lt;House&gt;&#187;"], [
        "+ selectPageByLandlord(...)",
        "+ incrViewCount(long): int"], "#efe9f7", "#8a6fb0"),
     ("HouseImageMapper", "Mapper", ["&#171;extends BaseMapper&lt;HouseImage&gt;&#187;"], [
        "+ deleteByHouseId(long): int"], "#efe9f7", "#8a6fb0"),
     ("House", "Entity", ["- id: Long", "- landlordId: Long", "- title: String",
        "- community/city/district: String", "- layout: String", "- area/rent: BigDecimal",
        "- lng/lat: BigDecimal", "- viewCount: Integer", "- avgScore: BigDecimal",
        "- status: Integer", "- rejectReason: String", "- deleted: Integer"], [
        "（MyBatis-Plus 映射 house，逻辑删除）"], "#f7f0e6", "#b08a5c"),
     ("HouseImage", "Entity", ["- id: Long", "- houseId: Long", "- url: String",
        "- sort: Integer", "- isCover: Boolean"], [
        "（映射 house_image，随房源级联删除）"], "#f7f0e6", "#b08a5c"),
     ("AnalysisService", "Service(AI 分析)", ["- aiAnalysisMapper: AiAnalysisMapper",
        "- llmGateway: LlmGateway"], [
        "+ assistFill(AiFillReq, long): AiFillVO",
        "+ fakeDetect(long, long): DetectVO"], "#f3e2f7", "#9b6fb0"),
   ],
   edges=[("HouseController","HouseService",""),("AdminController","HouseService","审核"),
          ("AdminController","AnalysisService","FR-16 检测"),
          ("HouseService","AuthService","实名校验"),
          ("HouseService","NotificationService","审核结果通知"),
          ("HouseService","HouseMapper",""),("HouseService","HouseImageMapper",""),
          ("HouseMapper","House","映射"),("HouseImageMapper","HouseImage","映射")]),

 "search": dict(
   title="检索与推荐模块类图",
   nodes=[
     ("HouseController", "Controller", ["- houseService / searchService"], [
        "+ search(SearchReq): R&lt;PageVO&lt;Item&gt;&gt;",
        "+ map(SearchReq): R&lt;List&lt;House&gt;&gt;",
        "+ recommend(int): R&lt;List&lt;House&gt;&gt;"], "#fdf3dc", "#d1a542"),
     ("FavoriteController", "Controller", ["- searchService: SearchService"], [
        "+ add(long houseId): R&lt;Void&gt;",
        "+ remove(long houseId): R&lt;Void&gt;",
        "+ list(page, size): R&lt;PageVO&lt;House&gt;&gt;"], "#fdf3dc", "#d1a542"),
     ("SearchService", "Service", ["- houseMapper: HouseMapper",
        "- favoriteMapper: FavoriteMapper", "- redis: StringRedisTemplate"], [
        "+ search(SearchReq): Page&lt;House&gt;",
        "+ mapHouses(SearchReq): List&lt;House&gt;",
        "+ addFavorite(long, long): void",
        "+ removeFavorite(long, long): void",
        "+ favorites(long, long, long): Page&lt;House&gt;",
        "- 动态条件拼装 buildWrapper()",
        "- 唯一索引兜底防重复收藏"], "#eaf6ea", "#5c9c5c"),
     ("HouseService", "Service(推荐)", ["- favoriteMapper: FavoriteMapper"], [
        "+ recommend(Long, int): List&lt;House&gt;",
        "- 区域/户型偏好加权 + 热度排序"], "#eaf6ea", "#5c9c5c"),
     ("HouseMapper", "Mapper", ["&#171;extends BaseMapper&lt;House&gt;&#187;"], [
        "+ selectSearch(SearchReq)",
        "+ selectByGeoRange(lngMin, lngMax, latMin, latMax)",
        "+ topByViewCount(int)"], "#efe9f7", "#8a6fb0"),
     ("FavoriteMapper", "Mapper", ["&#171;extends BaseMapper&lt;Favorite&gt;&#187;"], [
        "+ selectHousePageByUser(long, page, size)",
        "- UNIQUE(user_id, house_id) 防重"], "#efe9f7", "#8a6fb0"),
     ("House", "Entity", ["- 检索域字段：status / district / rent / layout",
        "- orientation / community / lng / lat", "- viewCount / avgScore"], [
        "（仅 status=已上架 进入检索域）"], "#f7f0e6", "#b08a5c"),
     ("Favorite", "Entity", ["- id: Long", "- userId: Long", "- houseId: Long",
        "- createdAt: LocalDateTime"], [
        "（映射 favorite，m:n 联系表）"], "#f7f0e6", "#b08a5c"),
   ],
   edges=[("HouseController","SearchService","FR-09/10"),("HouseController","HouseService","FR-11"),
          ("FavoriteController","SearchService","FR-25"),
          ("SearchService","HouseMapper",""),("SearchService","FavoriteMapper",""),
          ("HouseService","FavoriteMapper","偏好样本"),
          ("HouseMapper","House","映射"),("FavoriteMapper","Favorite","映射")]),

 "trade": dict(
   title="交易与合同模块类图",
   nodes=[
     ("AppointmentController", "Controller", ["- appointmentService"], [
        "+ create(AppointmentCreateReq): R&lt;ViewingAppointment&gt;",
        "+ act(long, AppointmentActionReq): R&lt;Void&gt;",
        "+ mine(page, size): R&lt;PageVO&lt;Map&gt;&gt;",
        "+ received(page, size): R&lt;PageVO&lt;Map&gt;&gt;"], "#fdf3dc", "#d1a542"),
     ("ContractController", "Controller", ["- contractService"], [
        "+ create(ContractCreateReq): R&lt;Contract&gt;",
        "+ act(long, ContractActionReq): R&lt;Map&gt;",
        "+ detail(long): R&lt;Contract&gt;",
        "+ mine(page, size): R&lt;PageVO&lt;Contract&gt;&gt;",
        "+ interpret(long): R&lt;InterpVO&gt;",
        "+ orders(page, size): R&lt;PageVO&lt;Map&gt;&gt;",
        "+ bills(long): R&lt;List&lt;RentBill&gt;&gt;",
        "+ payBill(long): R&lt;Void&gt;"], "#fdf3dc", "#d1a542"),
     ("AppointmentService", "Service（状态机）", [
        "- ST_PENDING=0 / ST_CONFIRMED=1 / ST_REJECTED=2 / ST_COMPLETED=3 / ST_CANCELLED=4",
        "- appointmentMapper / houseMapper / notificationService"], [
        "+ create(long, LocalDateTime, String, long): ViewingAppointment",
        "+ act(long, String, String, long, int): void",
        "+ pageFor(long, boolean, long, long): Page&lt;Map&gt;",
        "+ toVO(ViewingAppointment): Map",
        "- 唯一键 uk_active_slot 防时段冲突（占位键仅待确认/已确认持有，终态置 NULL 释放时段）"], "#eaf6ea", "#5c9c5c"),
     ("ContractService", "Service（状态机）", [
        "- ST_TENANT_CONFIRM=0 / ST_LANDLORD_CONFIRM=1 / ST_EFFECTIVE=2",
        "- ST_TERMINATED=3 / ST_VOIDED=4",
        "- contractMapper / orderMapper / billMapper"], [
        "+ create(long, LocalDate, LocalDate, long): Contract",
        "+ act(long, String, long, int): Map",
        "+ detail(long, long, int): Contract",
        "+ mine(long, long, long): Page&lt;Contract&gt;",
        "+ orders(long, int, long, long): Page&lt;Map&gt;",
        "+ bills(long, long): List&lt;RentBill&gt;",
        "+ payBill(long, long): void",
        "- renderTemplate(): clauses JSON 模板填充",
        "- generateBills(LeaseOrder): 按月生成账单（幂等）",
        "- 合同生效 → 房源状态置为已出租",
        "- detail 仅合同双方与管理员可读（防遍历 id 越权）"], "#eaf6ea", "#5c9c5c"),
     ("ViewingAppointmentMapper", "Mapper", [""], ["&#171;extends BaseMapper&lt;ViewingAppointment&gt;&#187;"],
        "#efe9f7", "#8a6fb0"),
     ("ContractMapper", "Mapper", [""], ["&#171;extends BaseMapper&lt;Contract&gt;&#187;"], "#efe9f7", "#8a6fb0"),
     ("LeaseOrderMapper", "Mapper", [""], ["&#171;extends BaseMapper&lt;LeaseOrder&gt;&#187;"], "#efe9f7", "#8a6fb0"),
     ("RentBillMapper", "Mapper", [""], ["&#171;extends BaseMapper&lt;RentBill&gt;&#187;"], "#efe9f7", "#8a6fb0"),
     ("Contract / LeaseOrder / RentBill", "Entity", [
        "Contract：house/tenant/landlord, clauses, risk_flags, 起止日期, status, 双方签署时间",
        "LeaseOrder：contract_id(唯一), house/tenant/landlord, 租期, 月租, 押金, status",
        "RentBill：lease_order_id, period_no, due_date, amount, status, paid_at"], [
        "（映射 contract / lease_order / rent_bill）"], "#f7f0e6", "#b08a5c"),
   ],
   edges=[("AppointmentController","AppointmentService",""),("ContractController","ContractService",""),
          ("ContractController","AnalysisService","FR-14 解读"),
          ("AppointmentService","ViewingAppointmentMapper",""),
          ("AppointmentService","NotificationService","预约通知"),
          ("ContractService","ContractMapper",""),("ContractService","LeaseOrderMapper","派生订单"),
          ("ContractService","RentBillMapper","账单计划"),
          ("ContractService","NotificationService","签约通知"),
          ("ContractService","HouseMapper","置为已出租"),
          ("ViewingAppointmentMapper","Contract / LeaseOrder / RentBill","同一实体包"),
          ("ContractMapper","Contract / LeaseOrder / RentBill","同一实体包")]),

 "interact": dict(
   title="评价与消息模块类图",
   nodes=[
     ("ReviewController", "Controller", ["- reviewService"], [
        "+ create(ReviewReq): R&lt;Review&gt;",
        "+ list(long houseId, page, size): R&lt;PageVO&lt;Map&gt;&gt;"], "#fdf3dc", "#d1a542"),
     ("NotificationController", "Controller", ["- notificationService"], [
        "+ list(onlyUnread, page, size): R&lt;PageVO&lt;Notification&gt;&gt;",
        "+ unreadCount(): R&lt;Long&gt;",
        "+ read(long id): R&lt;Void&gt;"], "#fdf3dc", "#d1a542"),
     ("ReportController", "Controller", ["- reportService"], [
        "+ create(ReportReq): R&lt;Report&gt;"], "#fdf3dc", "#d1a542"),
     ("ReviewService", "Service", ["- reviewMapper: ReviewMapper",
        "- orderMapper: LeaseOrderMapper", "- houseMapper: HouseMapper"], [
        "+ create(long, int, int, String, long): Review",
        "+ listByHouse(long, long, long): Page&lt;Map&gt;",
        "- 校验订单已完成 + lease_order_id 唯一（一单一评）",
        "- 刷新 house.avg_score 冗余均分"], "#eaf6ea", "#5c9c5c"),
     ("NotificationService", "Service", ["- notificationMapper: NotificationMapper"], [
        "+ send(Long, int, String, String, String, Long): void",
        "+ page(long, boolean, long, long): Page&lt;Notification&gt;",
        "+ unreadCount(long): long",
        "+ markRead(long, long): void",
        "- 关键事件统一触达（5 秒内可见）"], "#eaf6ea", "#5c9c5c"),
     ("ReportService", "Service", ["- reportMapper: ReportMapper"], [
        "+ create(long, int, long, String): Report",
        "+ handle(long, String, long): void",
        "+ page(int, long, long): Page&lt;Report&gt;",
        "- 评价类举报直接隐藏；房源类通知房东整改"], "#eaf6ea", "#5c9c5c"),
     ("ReviewMapper", "Mapper", ["&#171;extends BaseMapper&lt;Review&gt;&#187;"], ["+ selectByHousePage(...)"],
        "#efe9f7", "#8a6fb0"),
     ("NotificationMapper", "Mapper", ["&#171;extends BaseMapper&lt;Notification&gt;&#187;"],
        ["+ countUnread(long): long"], "#efe9f7", "#8a6fb0"),
     ("Review / Notification / Report", "Entity", [
        "Review：lease_order_id(唯一), house_id, 房源分/房东分(1~5), content, status",
        "Notification：user_id, type, title, content, ref_type, ref_id, is_read",
        "Report：reporter_id, target_type, target_id, reason, status, handle_by"], [
        "（映射 review / notification / report）"], "#f7f0e6", "#b08a5c"),
   ],
   edges=[("ReviewController","ReviewService",""),("NotificationController","NotificationService",""),
          ("ReportController","ReportService",""),
          ("ReviewService","ReviewMapper",""),("ReviewService","LeaseOrderMapper","完成校验收敛"),
          ("ReviewService","HouseMapper","刷新均分"),
          ("NotificationService","NotificationMapper",""),
          ("ReportService","ReportMapper",""),
          ("ReviewMapper","Review / Notification / Report","同一实体包"),
          ("NotificationMapper","Review / Notification / Report","同一实体包"),
          ("ReportService","NotificationService","处理结果通知")]),

 "ai": dict(
   title="AI 智能体服务模块类图",
   nodes=[
     ("AiController", "Controller", ["- chatService / kbService / analysisService"], [
        "+ createSession(SessionCreateReq): R&lt;AiChatSession&gt;",
        "+ sessions(page, size): R&lt;PageVO&lt;AiChatSession&gt;&gt;",
        "+ history(long id): R&lt;List&lt;MessageVO&gt;&gt;",
        "+ send(long id, MessageSendReq): SseEmitter",
        "+ engine(): R&lt;Map&lt;String,String&gt;&gt;",
        "+ pricing(long id): R&lt;PricingVO&gt;",
        "+ assistFill(Map req): R&lt;Map&gt;"], "#fdf3dc", "#d1a542"),
     ("ChatService", "Service", ["- sessionMapper / messageMapper", "- agentEngine: AgentEngine"], [
        "+ engineName(): String",
        "+ createSession(long, int, String): AiChatSession",
        "+ sessions(long, long, long): Page&lt;AiChatSession&gt;",
        "+ history(long, long): List&lt;MessageVO&gt;",
        "+ send(long, String, long): SseEmitter",
        "- 组装角色设定 + 记忆窗口 + 工具表",
        "- 异步回调中落库消息与工具调用"], "#eaf6ea", "#5c9c5c"),
     ("KbService", "Service", ["- docMapper / chunkMapper"], [
        "+ importDoc(String, int, String, String): KbDocument",
        "+ search(String, int): List&lt;Map&lt;String,String&gt;&gt;",
        "+ hit(String): boolean",
        "- 中文关键词提取（停用词 + 二字滑窗）"], "#eaf6ea", "#5c9c5c"),
     ("AnalysisService", "Service", ["- analysisMapper / houseMapper", "- llmGateway: LlmGateway"], [
        "+ pricing(House, long): PricingVO",
        "+ fakeDetect(long, long): DetectVO",
        "+ interpret(long, long): InterpVO",
        "+ assistFill(AiFillReq, long): AiFillVO",
        "- 结果与输入快照写入 ai_analysis"], "#eaf6ea", "#5c9c5c"),
     ("AgentEngine", "接口（可插拔）", ["（唯一实现：LlmAgent）"], [
        "+ available(): boolean",
        "+ describe(): String",
        "+ stream(long sessionId, long userId, int scene,",
        "         String userMessage, Callback callback): void"], "#f3e2f7", "#9b6fb0"),
     ("LlmAgent", "AgentEngine 实现", ["- gateway: LlmGateway", "- toolProvider: HousingToolProvider",
        "- memory(Redis 窗口 + 摘要)"], [
        "+ stream(...): void",
        "- LangChain4j AiServices 编排（ReAct）",
        "- 按 scene 路由角色设定与工具集",
        "- 本轮未执行工具且回答不像结论时才非流式重跑"], "#f3e2f7", "#9b6fb0"),
     ("AiJsonClient", "结构化模型调用", ["- llmGateway: LlmGateway",
        "- objectMapper: ObjectMapper"], [
        "+ call(String prompt, String payload, Class&lt;T&gt;): T",
        "+ call(String prompt, String payload, JavaType): T",
        "- 优先 response_format=json_schema（strict）强约束",
        "- Schema 由 JsonSchemas 按目标类型生成（required 全量）",
        "- 端点拒绝该参数则退回提示词约束 + 解析容错"], "#f3e2f7", "#9b6fb0"),
     ("AiPrompts", "提示词库", ["（定价 / 检测 / 解读 / 识别填充）"], [
        "+ PRICING / FAKE_DETECT /",
        "+ CONTRACT_INTERPRET / ASSIST_FILL",
        "- 统一要求“只输出 JSON”并声明字段与取值约束"], "#f3e2f7", "#9b6fb0"),
     ("LlmGateway", "模型网关", ["- props: AiProps",
        "- backends: openai-chat-completions / anthropic-messages / openai-responses"], [
        "+ available(): boolean",
        "+ chat(): ChatLanguageModel",
        "+ streaming(): StreamingChatLanguageModel",
        "+ jsonSchema(): Optional&lt;JsonSchemaChatModel&gt;",
        "+ describe(): String  // &lt;协议&gt;:&lt;模型&gt;，未配置为 none",
        "- 超时 60s · 未配置模型时上层直接报 4001"], "#f7f0e6", "#b08a5c"),
     ("OpenAiChatCompletionsModel", "自研适配器", ["- baseUrl / apiKey / model / headers"], [
        "+ generate(...tools...): Response&lt;AiMessage&gt;",
        "+ generate(...handler): void  // SSE 流式",
        "+ generateJson(...): String  // json_schema 结构化",
        "- 同一轮正文与 tool_calls 分别累积后合并",
        "- 只取 content，跳过 reasoning 思维链分片"], "#f7e7e7", "#c07070"),
     ("AnthropicMessagesChatModel", "自研适配器", ["- baseUrl / apiKey / model"], [
        "+ generate(...): Response&lt;AiMessage&gt;",
        "+ generate(...tools...): Response&lt;AiMessage&gt;",
        "+ generate(...handler): void  // SSE 流式",
        "- 解析 content[]：text 拼接 / tool_use 转工具请求",
        "- 跳过 thinking 块并关闭 thinking 降首字延迟"], "#f7e7e7", "#c07070"),
     ("OpenAiResponsesChatModel", "自研适配器", ["- baseUrl / apiKey / model"], [
        "+ generate(...): Response&lt;AiMessage&gt;",
        "+ generate(...tools...): Response&lt;AiMessage&gt;",
        "+ generate(...handler): void  // SSE 流式",
        "- 解析 output_text.delta 与 function_call"], "#f7e7e7", "#c07070"),
     ("HousingToolProvider", "工具提供器（留痕）", ["- housingTools / toolTraceService"], [
        "+ provideTools(ToolProviderRequest): ToolProviderResult",
        "+ takeKnowledgeCitations(long sessionId): String",
        "- 把每个 @Tool 执行包装为“计时 → 执行 → 留痕 → 返回”",
        "- 缓存 searchKnowledge 返回体作为来源引用（NFR-05）"], "#eaf6ea", "#5c9c5c"),
     ("ToolTraceService", "Service（留痕）", ["- messageMapper: AiChatMessageMapper"], [
        "+ record(long sessionId, String toolName, String argsJson,",
        "         String resultJson, int latencyMs): void",
        "- 写 role=3 工具消息（tool_name / args / result / latency）",
        "- JSON 列合法性校验与超长预览兜底"], "#eaf6ea", "#5c9c5c"),
     ("HousingTools", "工具注册表（@Tool）", ["- searchService / houseService / kbService"], [
        "+ searchHouses(minRent, maxRent, layoutKeyword,",
        "               district, keyword, subway,",
        "               facilities, sort): String",
        "+ getHouseDetail(houseId): String",
        "+ searchKnowledge(question): String"], "#eef4fb", "#4a7ebb"),
     ("AiChatSession / AiChatMessage / AiAnalysis", "Entity", [
        "AiChatSession：user_id, scene, title, context_summary, is_transferred",
        "AiChatMessage：session_id, role, content, tool_name/tool_args/tool_result, citations, latency_ms",
        "AiAnalysis：user_id, type, target_type/target_id, input_snapshot, result, model"], [
        "（映射 ai_chat_session / ai_chat_message / ai_analysis）"], "#f7f0e6", "#b08a5c"),
     ("KbDocument / KbChunk", "Entity", [
        "KbDocument：title, category, source_url, content, status, chunk_count",
        "KbChunk：document_id, seq(唯一), content, token_count, vector_ref"], [
        "（映射 kb_document / kb_chunk，切片随文档级联删除）"], "#f7f0e6", "#b08a5c"),
   ],
   edges=[("AiController","ChatService",""),("AiController","KbService",""),
          ("AiController","AnalysisService",""),
          ("ChatService","AgentEngine",""),
          ("AgentEngine","LlmAgent","实现"),
          ("LlmAgent","LlmGateway",""),("LlmAgent","HousingToolProvider","Function Calling"),
          ("HousingToolProvider","HousingTools","委托执行"),
          ("HousingToolProvider","ToolTraceService","工具留痕"),
          ("HousingToolProvider","ChatService","来源引用（citations）"),
          ("AnalysisService","AiJsonClient","结构化调用"),
          ("AiJsonClient","AiPrompts","提示词"),
          ("AiJsonClient","LlmGateway","同步模型"),
          ("LlmGateway","OpenAiChatCompletionsModel",""),
          ("LlmGateway","AnthropicMessagesChatModel",""),
          ("LlmGateway","OpenAiResponsesChatModel",""),
          ("HousingTools","KbService","searchKnowledge"),
          ("ChatService","AiChatSession / AiChatMessage / AiAnalysis","落库"),
          ("AnalysisService","AiChatSession / AiChatMessage / AiAnalysis","落库"),
          ("KbService","KbDocument / KbChunk","切片入库"),
          ("ToolTraceService","AiChatSession / AiChatMessage / AiAnalysis","落库")]),

 "admin": dict(
   title="后台管理模块类图",
   nodes=[
     ("AdminController", "Controller（@RequireRole(3)）", ["- adminService / adminChatService / reportService / analysisService"], [
        "+ users(String, page, size): R&lt;PageVO&lt;SysUser&gt;&gt;",
        "+ setUserStatus(long, boolean): R&lt;Void&gt;",
        "+ pending(page, size): R&lt;PageVO&lt;House&gt;&gt;",
        "+ audit(long, Map): R&lt;Void&gt;",
        "+ fakeDetect(long): R&lt;DetectVO&gt;",
        "+ reports(int, page, size): R&lt;PageVO&lt;Map&gt;&gt;",
        "+ handleReport(long, Map): R&lt;Void&gt;",
        "+ audits(page, size): R&lt;PageVO&lt;AuditLog&gt;&gt;",
        "+ chats(String, Integer, Boolean, Long, page, size)",
        "+ chatDetail(long id): R&lt;DetailVO&gt;",
        "+ dashboard(String): R&lt;Map&gt;"], "#fdf3dc", "#d1a542"),
     ("AdminService", "Service", ["- userMapper / houseMapper / auditLogMapper"], [
        "+ users(String, long, long): Page&lt;SysUser&gt;",
        "+ setUserStatus(long, boolean, long): void",
        "+ pendingHouses(long, long): Page&lt;House&gt;",
        "+ dashboard(String): Map",
        "+ auditLogs(long, long): Page&lt;AuditLog&gt;",
        "- 聚合统计：按日/周/月分组（FR-24）"], "#eaf6ea", "#5c9c5c"),
     ("AdminChatService", "Service（对话审计）", ["- sessionMapper / messageMapper / userMapper"], [
        "+ sessions(String, Integer, Boolean, Long, long, long)",
        "+ detail(long sessionId): DetailVO",
        "+ dashboardMetrics(String periodFormat): Map",
        "- GROUP BY session_id 批量聚合，避免 N+1（NFR-05）"], "#eaf6ea", "#5c9c5c"),
     ("ReportService", "Service", ["- reportMapper / reviewMapper / houseMapper"], [
        "+ page(int, long, long): Page&lt;Report&gt;",
        "+ handle(long, String, long): void"], "#eaf6ea", "#5c9c5c"),
     ("AuditLogService", "Service（横切）", ["- auditLogMapper: AuditLogMapper"], [
        "+ log(long, String, String, Long, Map, String): void",
        "- 审核/禁用/处理举报等管理动作全量留痕"], "#eaf6ea", "#5c9c5c"),
     ("SysUserMapper", "Mapper", [""], ["&#171;extends BaseMapper&lt;SysUser&gt;&#187;"], "#efe9f7", "#8a6fb0"),
     ("HouseMapper", "Mapper", [""], ["+ countGroupByDay/Week/Month()"], "#efe9f7", "#8a6fb0"),
     ("ReportMapper", "Mapper", [""], ["&#171;extends BaseMapper&lt;Report&gt;&#187;"], "#efe9f7", "#8a6fb0"),
     ("AuditLogMapper", "Mapper", [""], ["&#171;extends BaseMapper&lt;AuditLog&gt;&#187;"], "#efe9f7", "#8a6fb0"),
     ("SysUser / House / Report / AuditLog", "Entity", [
        "SysUser：status 禁用即时生效（每请求状态校验）",
        "House：status 审核状态机 + reject_reason",
        "Report：多态关联 target_type/target_id",
        "AuditLog：operator_id, action, target_type, target_id, detail(JSON), ip"], [
        "（映射 sys_user / house / report / audit_log）"], "#f7f0e6", "#b08a5c"),
   ],
   edges=[("AdminController","AdminService",""),("AdminController","AdminChatService","NFR-05 审计"),
          ("AdminController","ReportService",""),
          ("AdminController","AuditLogService","留痕"),
          ("AdminChatService","SysUserMapper","人物信息"),
          ("AdminService","AdminChatService","FR-24 AI 对话量"),
          ("AdminService","SysUserMapper",""),("AdminService","HouseMapper",""),
          ("AdminService","AuditLogMapper",""),
          ("ReportService","ReportMapper",""),("ReportService","ReviewMapper","隐藏评价"),
          ("ReportService","HouseMapper","房源整改"),
          ("SysUserMapper","SysUser / House / Report / AuditLog","同一实体包"),
          ("HouseMapper","SysUser / House / Report / AuditLog","同一实体包")]),
}


def fig_class(key):
    cfg = CLASS_FIGS[key]
    nodes = []
    for n in cfg["nodes"]:
        name, stereo, attrs, methods = n[0], n[1], n[2], n[3]
        fill = n[4] if len(n) > 4 else "#fdf3dc"
        border = n[5] if len(n) > 5 else "#d1a542"
        nid = "n" + str(len(nodes))
        nodes.append(
            f'  {nid} [shape=plaintext, label={uml(name, stereo, attrs, methods, fill, border)}];')
    name2id = {n[0]: f"n{i}" for i, n in enumerate(cfg["nodes"])}
    # 模块外协作者（其它模块的类）：自动补一个灰色存根节点，保持类图协作关系完整
    for a, b, _ in cfg["edges"]:
        for x in (a, b):
            if x not in name2id:
                nid = "x" + str(len(name2id))
                name2id[x] = nid
                nodes.append(
                    f'  {nid} [shape=plaintext, fontsize=9, fontcolor="#666666", '
                    f'label={uml(x, "模块外协作者", ["（其它模块）"], ["—"], "#f2f2f2", "#b0b0b0")}];')
    edges = []
    for a, b, lbl in cfg["edges"]:
        lblattr = f' [label="{lbl}"]' if lbl else ""
        edges.append(f'  {name2id[a]} -> {name2id[b]}{lblattr};')
    d = f'''digraph cls_{key} {{
  rankdir=TB; nodesep=0.42; ranksep=0.55; splines=spline;
  graph [fontname="{CN}", bgcolor="white", margin=0.15, labelloc="t",
         label="{cfg['title']}", fontsize=13, fontcolor="#1f3a5f"];
  node  [fontname="{CN}", fontsize=9.5, shape=plaintext, margin=0];
  edge  [fontname="{CN}", fontsize=9, color="#4a6f9e", penwidth=1.0,
         arrowsize=0.7, style=solid];
{chr(10).join(nodes)}
{chr(10).join(edges)}
}}
'''
    render(f"fig_3_1_cls_{key}", d, dpi=165)


# ─────────────────────────────────────────────────────────────────────────────
# AI 模块结构图（智能体层组件结构）
# ─────────────────────────────────────────────────────────────────────────────
def fig_agent_structure():
    """AI 模块结构图：智能体层组件结构（纵向编排，字号加大以保证页面可读）。"""
    d = f'''digraph agent {{
  rankdir=TB; nodesep=0.40; ranksep=0.42;
  graph [fontname="{CN}", bgcolor="white", margin=0.15];
  node  [fontname="{CN}", fontsize=14, shape=box, style="filled,rounded", margin="0.16,0.10"];
  edge  [fontname="{CN}", fontsize=13, color="#5c5c5c", penwidth=1.2];

  ui [label="前端对话界面 / 内嵌入口\n（定价 · 检测 · 解读 · 辅助填充）", fillcolor="#dceaf8", color="#4a7ebb", width=3.6];
  ctrl [label="AiController\nPOST /ai/sessions　　　　　　　· GET /ai/engine\nPOST /ai/sessions/{id}/messages（SSE 流式）\nGET /ai/sessions/{id}/history\nPOST /ai/houses/{id}/pricing-suggestion\nPOST /ai/assist-fill",
        fillcolor="#fdf3dc", color="#d1a542", width=4.6];

  subgraph cluster_svc {{
    label="应用服务层"; fontsize=13; fontcolor="#2f6b2f"; style="rounded,filled";
    fillcolor="#f4fbf4"; color="#5c9c5c"; margin=10;
    chat [label="ChatService\n会话 CRUD\nSSE 流式发送\n消息异步落库", fillcolor="#eaf6ea", color="#5c9c5c"];
    kb   [label="KbService\n文档切片入库\n关键词检索 topK\n命中判定", fillcolor="#eaf6ea", color="#5c9c5c"];
    ana  [label="AnalysisService\n定价建议\n虚假检测\n合同解读 · 识别", fillcolor="#eaf6ea", color="#5c9c5c"];
    trace [label="ToolTraceService\n工具调用留痕（role=3）\n工具名 · 入参 · 返回 · 耗时", fillcolor="#eaf6ea", color="#5c9c5c"];
    {{rank=same; chat; kb; ana; trace;}}
  }}

  subgraph cluster_engine {{
    label="智能体编排层（AgentEngine 接口，可插拔）"; fontsize=13; fontcolor="#5c4478";
    style="rounded,filled"; fillcolor="#f8f5fc"; color="#8a6fb0"; margin=10;
    api [label="AgentEngine（接口）\navailable() · describe()\nstream(sessionId, userId,\n　　　 scene, msg, callback)",
         fillcolor="#efe9f7", color="#8a6fb0", width=4.0];
    llm [label="LlmAgent\nLangChain4j AiServices 编排\n按 scene 路由角色设定\n工具注册表（@Tool 扫描）\n记忆窗口 + 摘要压缩",
         fillcolor="#f3e2f7", color="#9b6fb0"];
    ajc [label="AiJsonClient（结构化模型调用）\n定价 / 检测 / 解读 / 识别填充\n提示词 AiPrompts → 模型 JSON → 校验\n不合规重试一次，仍失败报 4001",
         fillcolor="#f3e2f7", color="#9b6fb0"];
    {{rank=same; llm; ajc;}}
  }}

  subgraph cluster_gw {{
    label="模型接入层（LLM 网关，三协议可切换）"; fontsize=13; fontcolor="#7a5a30";
    style="rounded,filled"; fillcolor="#fdfaf6"; color="#b08a5c"; margin=10;
    gw [label="LlmGateway\n按 AI_BACKEND 选择后端 → available() / chat()\n　　　　　　　　　　/ streaming() / describe()\n超时 60 秒 · 未配置模型即报 4001",
        fillcolor="#f7f0e6", color="#b08a5c", width=4.6];
    p1 [label="openai-chat-completions\n通用协议（多厂商/网关兼容）", fillcolor="#f7e7e7", color="#c07070"];
    p2 [label="anthropic-messages\n自研适配器 · 兼容端点", fillcolor="#f7e7e7", color="#c07070"];
    p3 [label="openai-responses\n自研适配器 · 新接口", fillcolor="#f7e7e7", color="#c07070"];
    {{rank=same; p1; p2; p3;}}
  }}

  subgraph cluster_tools {{
    label="工具与知识层"; fontsize=13; fontcolor="#31527a"; style="rounded,filled";
    fillcolor="#eef4fb"; color="#4a7ebb"; margin=10;
    prov [label="HousingToolProvider（工具提供器）\n向 AiServices 注册 @Tool 规格\n并把执行包装为\n计时 → 执行 → 留痕 → 返回",
           fillcolor="#eef4fb", color="#4a7ebb", width=4.6];
    tools [label="HousingTools（业务工具，@Tool 注册）\nsearchHouses(minRent, maxRent, layoutKeyword,\n　　　　　　　district, keyword, subway,\n　　　　　　　facilities, sort) → total + 卡片\ngetHouseDetail(houseId) · searchKnowledge",
           fillcolor="#eef4fb", color="#4a7ebb", width=4.8];
    rag [label="RAG 检索\nsearchKnowledge(question) → 切片 + 来源引用\n首版关键词命中 + 计分；向量升级位已预留",
         fillcolor="#eef4fb", color="#4a7ebb", width=4.8];
  }}

  data [label="数据与外部资源\nMySQL：house · contract · ai_chat_session / message\n　　　　　 ai_analysis · kb_document / chunk\nRedis：会话缓存　|　外部：大模型服务 HTTPS 出网",
        fillcolor="#e7f2e7", color="#5c9c5c", width=5.0];

  ui -> ctrl;
  ctrl -> chat; ctrl -> kb; ctrl -> ana;
  chat -> api; kb -> api; ana -> api;
  api -> llm; api -> ajc [label="分析类能力"];
  llm -> gw [label="流式调用"]; llm -> prov [label="工具提供器"];
  prov -> tools [label="委托执行"]; prov -> trace [label="工具留痕"];
  ajc -> gw [label="同步模型"];
  tools -> rag [style=dotted, label="客服意图"];
  gw -> p1; gw -> p2; gw -> p3;
  tools -> data; ana -> data; chat -> data; kb -> data; trace -> data;
}}
'''
    render("fig_3_1_mod_ai_agent", d)


# ─────────────────────────────────────────────────────────────────────────────
# 图4-1 数据库表间关系图（E-R）
# ─────────────────────────────────────────────────────────────────────────────
def tbl(title, cols, color="#eef4fb", border="#4a7ebb"):
    rows = "".join(
        f'<TR><TD ALIGN="LEFT" BALIGN="LEFT">{c}</TD></TR>' for c in cols)
    return (f'<<TABLE BORDER="0" CELLBORDER="1" CELLSPACING="0" CELLPADDING="3">'
            f'<TR><TD BGCOLOR="{color}"><B>{title}</B></TD></TR>'
            f'<TR><TD ALIGN="LEFT" BALIGN="LEFT"><TABLE BORDER="0" CELLBORDER="0" CELLSPACING="0">'
            f'{rows}</TABLE></TD></TR></TABLE>>')


def fig_er():
    """图4-1 数据库表间关系图：只表达实体与关系（字段细节由正文表格承载）。"""
    U, H, T, A = "#dceaf8", "#eaf6ea", "#fdf3dc", "#f3e2f7"
    BU, BH, BT, BA = "#4a7ebb", "#5c9c5c", "#d1a542", "#9b6fb0"

    ENT = [
        ("sys_user", "sys_user\n用户", U, BU),
        ("realname_auth", "realname_auth\n实名认证", U, BU),
        ("notification", "notification\n站内通知", U, BU),
        ("house", "house\n房源", H, BH),
        ("house_image", "house_image\n房源图片", H, BH),
        ("favorite", "favorite\n收藏", H, BH),
        ("viewing_appointment", "viewing_appointment\n看房预约", T, BT),
        ("contract", "contract\n电子合同", T, BT),
        ("lease_order", "lease_order\n租赁订单", T, BT),
        ("rent_bill", "rent_bill\n租金账单", T, BT),
        ("review", "review\n评价", T, BT),
        ("report", "report\n举报", T, BT),
        ("audit_log", "audit_log\n审计日志", T, BT),
        ("ai_chat_session", "ai_chat_session\nAI 会话", A, BA),
        ("ai_chat_message", "ai_chat_message\n对话消息", A, BA),
        ("ai_analysis", "ai_analysis\nAI 分析结果", A, BA),
        ("kb_document", "kb_document\n知识库文档", A, BA),
        ("kb_chunk", "kb_chunk\n知识库切片", A, BA),
    ]
    nodes = "".join(
        f'  {i} [label="{lbl}", fillcolor="{f}", color="{b}", penwidth=1.4, width=2.5];\n'
        for i, lbl, f, b in ENT)

    ROWS = [["sys_user", "realname_auth", "notification"],
            ["house", "house_image", "favorite"],
            ["viewing_appointment", "contract", "lease_order"],
            ["rent_bill", "review", "report"],
            ["audit_log", "ai_chat_session", "ai_chat_message"],
            ["ai_analysis", "kb_document", "kb_chunk"]]
    ranks = "".join("  {rank=same; " + "; ".join(r) + ";}\n" for r in ROWS)
    chain = "".join(f'  {ROWS[i][0]} -> {ROWS[i+1][0]} [style=invis, weight=10];\n'
                    for i in range(len(ROWS) - 1))

    def e(a, b, lbl, d="1:n", color="#3f6b3f", style="solid"):
        return (f'  {a} -> {b} [dir=both, arrowtail=none, arrowhead=crow, style={style}, '
                f'constraint=false, label="{lbl} {d}", fontcolor="{color}", color="{color}"];')

    edges = [
        e("sys_user", "realname_auth", "提交"),
        e("sys_user", "notification", "接收"),
        e("sys_user", "house", "发布"),
        e("house", "house_image", "包含"),
        e("sys_user", "favorite", "收藏"),
        e("house", "favorite", "被收藏"),
        e("house", "viewing_appointment", "被预约"),
        e("sys_user", "viewing_appointment", "发起"),
        e("house", "contract", "签约标的"),
        e("sys_user", "contract", "双方签署"),
        e("contract", "lease_order", "派生", d="1:1", color="#a06a1f"),
        e("lease_order", "rent_bill", "生成账单"),
        e("lease_order", "review", "被评价", d="1:0..1"),
        e("sys_user", "report", "提交"),
        e("sys_user", "audit_log", "留痕", style="dashed", color="#8c8c8c"),
        e("sys_user", "ai_chat_session", "发起"),
        e("ai_chat_session", "ai_chat_message", "包含"),
        e("sys_user", "ai_analysis", "触发", style="dashed", color="#8c8c8c"),
        e("kb_document", "kb_chunk", "切片"),
    ]

    legend = ('  legend [shape=plaintext, label=<<TABLE BORDER="1" CELLBORDER="0" CELLSPACING="0" '
              'CELLPADDING="4"><TR><TD ALIGN="LEFT">图例：实体矩形为业务表；箭头 crow 端为多端；'
              '虚线为逻辑外键（业务层校验）</TD></TR>'
              '<TR><TD ALIGN="LEFT">底色：<FONT COLOR="#2a5480">■</FONT> 用户域　'
              '<FONT COLOR="#2f6b2f">■</FONT> 房源域　<FONT COLOR="#8a6218">■</FONT> 交易域　'
              '<FONT COLOR="#5c4478">■</FONT> AI 域　|　1:1 与 1:0..1 由唯一索引表达</TD></TR></TABLE>>];')

    d = f'''digraph er {{
  rankdir=TB; newrank=true; nodesep=0.30; ranksep=0.62; splines=spline;
  graph [fontname="{CN}", bgcolor="white", margin=0.15];
  node  [shape=box, style="filled,rounded", fontname="{CN}", fontsize=15, margin="0.16,0.10"];
  edge  [fontname="{CN}", fontsize=12, penwidth=1.3];
  legend [fillcolor="#fafafa", color="#888888"];
{ranks}{chain}{nodes}
{chr(10).join(edges)}
}}
'''
    render("fig_4_1_er", d, dpi=150)


def fig_arch_layered():
    """图2-2 系统分层架构图：自上而下五层（客户端 / 接入 / 业务 / AI / 数据）+ 外部模型网关。"""
    d = '''digraph arch_layered {
  rankdir=TB; nodesep=0.30; ranksep=0.50; splines=spline;
  graph [fontname="''' + CN + '''", bgcolor="white", compound=true, margin=0.15];
  node  [fontname="''' + CN + '''", fontsize=11, shape=box, style="filled,rounded",
         fillcolor="#eef4fb", color="#4a7ebb", penwidth=1.1, margin="0.16,0.10"];
  edge  [fontname="''' + CN + '''", fontsize=9.5, color="#5c5c5c", penwidth=1.0, arrowsize=0.7];

  subgraph cluster_client {
    label="① 客户端层 —— 一套代码（frontend/dist），两种分发"; labeljust=l; fontsize=12;
    style="rounded,filled"; fillcolor="#f6f9fd"; color="#4a7ebb"; margin=9;
    web      [label="浏览器 Web 版\\nReact 18 + TS + antd 5 + ECharts\\nHashRouter · api.ts · ssePost"];
    electron [label="Electron 桌面端\\n同一构建产物以 file:// 加载\\n预注入后端 API 地址"];
  }

  subgraph cluster_access {
    label="② 接入层（Spring Boot 3.3.4 · :8080）"; labeljust=l; fontsize=12;
    style="rounded,filled"; fillcolor="#f6f9fd"; color="#4a7ebb"; margin=9;
    access [label="REST /api/v1/*（统一响应 R{code, message, data}）\\nJWT 过滤器 + @RequireRole 角色拦截器\\nSSE 流式 · 静态图片 /uploads/**"];
  }

  subgraph cluster_biz {
    label="③ 业务层（controller → service → mapper）"; labeljust=l; fontsize=12;
    style="rounded,filled"; fillcolor="#f6f9fd"; color="#4a7ebb"; margin=9;
    m1 [label="用户 / 实名认证"];
    m2 [label="房源（种子 + 图片）"];
    m3 [label="预约 / 合同 / 订单"];
    m4 [label="评价 / 举报 / 收藏"];
    m5 [label="通知 / 后台管理"];
    m6 [label="知识库 KB"];
    m1 -> m4 [style=invis]; m2 -> m5 [style=invis]; m3 -> m6 [style=invis];
  }

  subgraph cluster_ai {
    label="④ AI 层（全部真调模型，零兜底）"; labeljust=l; fontsize=12;
    style="rounded,filled"; fillcolor="#f6f9fd"; color="#4a7ebb"; margin=9;
    chat  [label="ChatService：SSE 编排\\n消息 / 留痕落库"];
    agent [label="AgentEngine = LlmAgent\\n系统提示词（房源链接协议）\\n+ 12 轮会话记忆"];
    tools [label="HousingToolProvider（留痕 role=3）\\nHousingTools：searchHouses\\ngetHouseDetail · searchKnowledge\\n（detailUrl 由代码统一拼装）", fillcolor="#f7f0e6", color="#b08a5c"];
    gw    [label="LlmGateway：三协议适配器全部自研\\nopenai-chat-completions（当前）\\nanthropic-messages · openai-responses\\n正文与 tool_calls 分别累积后合并"];
    jsonc [label="AiJsonClient：定价 / 虚假检测\\n合同解读 / 智能填充\\njson_schema(strict) 强约束", fillcolor="#f7f0e6", color="#b08a5c"];
    chat -> agent -> tools -> gw [style=invis];
  }

  subgraph cluster_data {
    label="⑤ 数据层"; labeljust=l; fontsize=12;
    style="rounded,filled"; fillcolor="#f6f9fd"; color="#4a7ebb"; margin=9;
    mysql   [label="MySQL 8 · 18 张表\\nhouse / house_image / ai_chat_message …"];
    redis   [label="Redis\\n验证码 · 登录失败计数"];
    uploads [label="uploads 图片目录\\nseed-*.jpg"];
  }

  ext [label="外部：聚合模型网关（按 UA 放行）\\ndeepseek / deepseek-v4.1-flash",
       fillcolor="#f7e7e7", color="#c07070"];

  web      -> access [label="HTTP /api/v1"];
  electron -> access [label="HTTP（注入基址）"];
  access   -> m2     [label="业务请求"];
  access   -> chat   [label="找房 / 客服 SSE"];
  chat     -> agent  [label="编排"];
  agent    -> gw     [label="流式对话"];
  agent    -> tools  [label="Function Calling"];
  tools    -> mysql  [label="检索已上架房源"];
  m2       -> mysql;
  m5       -> mysql  [style=dotted];
  m6       -> mysql  [style=dotted];
  jsonc    -> gw     [label="结构化分析", style=dashed];
  gw       -> ext    [label="HTTPS 出网"];
}
'''
    render("fig_2_2_arch", d, dpi=150)


def fig_agent_flow():
    """AI 找房智能体工作时序图：一次找房请求的 9 步全过程（含工具留痕与链接协议）。"""
    d = '''digraph agent_flow {
  rankdir=TB; nodesep=0.30; ranksep=0.40; splines=spline;
  graph [fontname="''' + CN + '''", bgcolor="white", margin=0.15];
  node  [fontname="''' + CN + '''", fontsize=11, shape=box, style="filled,rounded",
         fillcolor="#eef4fb", color="#4a7ebb", penwidth=1.1, margin="0.16,0.10"];
  edge  [fontname="''' + CN + '''", fontsize=9.5, color="#5c5c5c", penwidth=1.0, arrowsize=0.7];

  f1 [label="1. 租客在 AI 助手发送需求\\n（scene=1 找房场景）"];
  f2 [label="2. ssePost 建立流式连接\\nChatService 组装：系统提示词\\n+ 12 轮记忆 + 工具清单", fillcolor="#eaf6ea", color="#5c9c5c"];
  f3 [label="3. LlmAgent → LlmGateway → deepseek\\n自研适配器逐字累积正文\\n并同时捕获 tool_calls", fillcolor="#fdeeee", color="#c07070"];
  f4 [label="4. 模型决策调用工具（不凭空作答）\\nsearchHouses(maxRent=2500,\\nlayoutKeyword=1室, subway=true)", fillcolor="#fdeeee", color="#c07070"];
  f5 [label="5. HousingToolProvider 包装执行\\n计时 → 执行 → 留痕 role=3\\n（tool_name / args / result / 耗时）", fillcolor="#f7f0e6", color="#b08a5c"];
  f6 [label="6. SearchService 查 MySQL（仅已上架）\\n返回 {total, list:[卡片…\\ncoverUrl, detailUrl:#/app/houses/4]}", fillcolor="#f7f0e6", color="#b08a5c"];
  f7 [label="7. 工具结果回填模型 → 生成推荐正文\\n逐套输出 Markdown 链接 [标题](detailUrl)\\n不得编造房源与链接", fillcolor="#fdeeee", color="#c07070"];
  f8 [label="8. SSE 流式返回：delta 逐字推送\\ndone{messageId, citations,\\ntransferred, latencyMs}", fillcolor="#eaf6ea", color="#5c9c5c"];
  f9 [label="9. 前端 renderWithLinks 渲染可点击 <a>\\n点击直达房源详情页\\n消息全量落库（审计可回放）"];
  fb [label="可靠性兜底：本轮没执行任何工具且回答像过程语，\\n或 429 / 5xx / 超时 / 空返回 → 非流式重跑一次（同一会话记忆）；\\n4xx 如实报错 4001，无正文无工具调用绝不伪造回答",
      fillcolor="#f7e7e7", color="#c07070", style="filled,rounded,dashed"];

  f1 -> f2 -> f3 -> f4 -> f5 -> f6 -> f7 -> f8 -> f9;
  f6 -> f3 [label="结果回填，继续生成", style=dashed, constraint=false, color="#b08a5c"];
}
'''
    render("fig_3_agent_flow", d, dpi=150)


if __name__ == "__main__":
    fig_system()
    for key, name, frs, c, s, m, en in MODULES:
        if key != "ai":
            fig_module_structure(key, name, frs, c, s, m, en)
    fig_agent_structure()
    for key in CLASS_FIGS:
        fig_class(key)
    fig_er()
    fig_arch_layered()
    fig_agent_flow()
    print("all figures done ->", OUT)
