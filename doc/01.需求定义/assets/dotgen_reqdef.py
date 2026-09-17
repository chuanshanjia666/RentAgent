#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成《需求定义说明书》全部配图（Graphviz）。

图清单：
  图2-1 业务痛点与系统应对映射图   fig_2_1_pain
  图4-1 核心业务流程图             fig_4_1_flow
  图4-2 角色-用例图                fig_4_2_usecase
  图5-1 系统上下文图               fig_5_1_context
  图5-2 系统总体架构图             fig_5_2_arch

排版约束：正文可用宽度约 17cm，图片按 15~16.2cm 插入，故图源原始宽度需控制在
约 18cm 以内，保证插入后中文字号不低于 9pt 可读。各图的节点标签因此都做了短标签化。

运行：python3 dotgen_reqdef.py      （需要 graphviz 的 dot 命令）
"""
import pathlib
import subprocess

BASE = pathlib.Path(__file__).parent
OUT = BASE / "figures"
OUT.mkdir(exist_ok=True)

CN = "Microsoft YaHei"

GLOBAL = """
  graph [fontname="%s", bgcolor="white", margin=0.15];
  node  [fontname="%s", fontsize=11, shape=box, style="filled,rounded",
         fillcolor="#eef4fb", color="#4a7ebb", penwidth=1.1, margin="0.14,0.09"];
  edge  [fontname="%s", fontsize=9, color="#5c5c5c", penwidth=1.0, arrowsize=0.7];
""" % (CN, CN, CN)


def render(name, body, dpi=170):
    src = OUT / f"{name}.dot"
    src.write_text("digraph {\n" + GLOBAL + body + "}\n", encoding="utf-8")
    subprocess.run(["dot", f"-Gdpi={dpi}", "-Tpng",
                    "-o", str(OUT / f"{name}.png"), str(src)], check=True)
    print("ok", name)


# ── 图2-1 业务痛点与系统应对映射图 ──────────────────────────────────────────
fig_2_1_pain = """
  rankdir=LR;
  splines=polyline;
  nodesep=0.18;
  ranksep=1.1;

  subgraph cluster_pain {
    label="业务痛点（问题定义）";
    fontsize=12;
    style="dashed,rounded";
    color="#c0c0c0";
    fontcolor="#666666";
    node [fillcolor="#fdeeee", color="#c98b8b"];
    p1 [label="信息不对称：描述与实际不符"];
    p2 [label="虚假房源泛滥：人工审核滞后"];
    p3 [label="匹配效率低：模糊需求难表达"];
    p4 [label="沟通成本高：高频问题反复咨询"];
    p5 [label="合同门槛高：风险条款难识别"];
    p6 [label="决策支持缺失：房东定价无依据"];
    { rank=same; p1; p2; p3; p4; p5; p6; }
  }

  subgraph cluster_sol {
    label="RentAgent 应对（本项目范围）";
    fontsize=12;
    style="dashed,rounded";
    color="#c0c0c0";
    fontcolor="#666666";
    node [fillcolor="#eaf3ea", color="#7ba87b"];
    s1 [label="全量审核 + 房东实名认证（FR-03/07）"];
    s2 [label="AI 虚假房源检测，输出风险分（FR-16）"];
    s3 [label="AI 找房助手，自然语言找房（FR-12）"];
    s4 [label="RAG 智能客服，答案附来源（FR-13）"];
    s5 [label="合同智能解读，风险条款标红（FR-14）"];
    s6 [label="智能定价建议，给出区间与依据（FR-15）"];
    { rank=same; s1; s2; s3; s4; s5; s6; }
  }

  p1 -> s1;
  p2 -> s2;
  p3 -> s3;
  p4 -> s4;
  p5 -> s5;
  p6 -> s6;
"""


# ── 图4-1 核心业务流程图 ────────────────────────────────────────────────────
fig_4_1_flow = """
  rankdir=TB;
  splines=polyline;
  nodesep=0.22;
  ranksep=0.26;
  node [fontsize=9.5, margin="0.1,0.05"];
  edge [fontsize=8.5];

  a  [label="房东提交房源信息（FR-05）"];
  d1 [label="已实名认证？", shape=diamond, fillcolor="#fdf3e3", color="#c9a86b"];
  b  [label="房东实名认证（FR-03）"];
  c  [label="进入待审核队列"];
  d2 [label="管理员审核
（AI 风险分辅助 FR-16）", shape=diamond, fillcolor="#fdf3e3", color="#c9a86b"];
  e  [label="驳回并通知房东，
修改后重新提交（FR-06/07/21）", fillcolor="#fdeeee", color="#c98b8b"];
  g  [label="审核通过，房源上架（FR-07）"];
  h  [label="租客检索 / AI 找房 → 查看详情并预约（FR-09~12/25/17）"];
  d3 [label="房东确认预约？", shape=diamond, fillcolor="#fdf3e3", color="#c9a86b"];
  l  [label="拒绝并通知租客，可另约时段", fillcolor="#fdeeee", color="#c98b8b"];
  k  [label="看房完成，确认租赁意向"];
  m  [label="生成电子合同，双方在线确认签署生效（FR-18 / FR-14）"];
  o  [label="租金计划与履约管理，合同完成后评价（FR-19/20）"];
  q  [label="业务闭环达成", shape=ellipse, fillcolor="#dbe7f6", color="#4a7ebb"];

  a -> d1;
  d1 -> b [label="否"];
  d1 -> c [label="是"];
  b -> c [label="认证通过"];
  c -> d2;
  d2 -> e [label="驳回"];
  e -> c [label="重新提交"];
  d2 -> g [label="通过"];
  g -> h;
  h -> d3;
  d3 -> l [label="拒绝"];
  l -> d3 [label="另约时段"];
  d3 -> k [label="确认"];
  k -> m;
  m -> o;
  o -> q;

  { rank=same; b; c; }
  { rank=same; e; g; }
  { rank=same; l; k; }
"""


# 图4-2 以「HTML 表格图」确定性生成：Graphviz 自动布局在四列场景下会把各列错开成
# 阶梯或压成一列，表格图可保证列对齐、字号可控，并顺带带上 FR 追溯标号。
UC_COLS = [
    ("游客 / 租客", "#dbe7f6", [
        ("注册与登录", "FR-01/02"), ("实名与个人信息", "FR-03/04"), ("检索与关键词搜索", "FR-09"),
        ("地图找房", "FR-10"), ("个性化推荐", "FR-11"), ("收藏房源", "FR-25"),
        ("预约看房", "FR-17"), ("在线签约", "FR-18"), ("租金账单查看", "FR-19"),
        ("提交评价", "FR-20"), ("消息通知", "FR-21"),
    ]),
    ("房东", "#dbe7f6", [
        ("实名认证", "FR-03"), ("发布房源", "FR-05"), ("房源智能填充", "FR-08"),
        ("编辑与上下架", "FR-06"), ("智能定价建议", "FR-15"), ("处理看房预约", "FR-17"),
        ("订单与退续租", "FR-19"), ("查看评价", "FR-20"),
    ]),
    ("平台管理员", "#dbe7f6", [
        ("房源审核", "FR-07"), ("虚假房源检测", "FR-16"), ("用户管理", "FR-22"),
        ("评价举报处理", "FR-23"), ("知识库维护", "FR-13"), ("数据统计看板", "FR-24"),
    ]),
    ("AI 智能体（内置服务角色）", "#f3e8d8", [
        ("AI 找房助手", "FR-12"), ("智能客服问答", "FR-13"), ("合同智能解读", "FR-14"),
        ("智能定价分析", "FR-15"), ("虚假房源风险分析", "FR-16"),
    ]),
]


def build_usecase_dot():
    rows = max(len(c[2]) for c in UC_COLS)
    L = ["  rankdir=TB;",
         '  node [shape=plaintext, fontname="%s", fontsize=10];' % CN,
         "  uc [label=<",
         '    <TABLE BORDER="1" CELLBORDER="1" CELLSPACING="0" CELLPADDING="5"'
         ' COLOR="#4a7ebb" BGCOLOR="white">']
    # 角色表头
    L.append("      <TR>")
    for name, fill, _ in UC_COLS:
        L.append('        <TD ALIGN="CENTER" BGCOLOR="%s"><B>%s</B></TD>' % (fill, name))
    L.append("      </TR>")
    # 用例行
    for r in range(rows):
        L.append("      <TR>")
        for _, fill, items in UC_COLS:
            if r < len(items):
                txt, fr = items[r]
                L.append('        <TD ALIGN="LEFT" BGCOLOR="#eef4fb">%s　<FONT COLOR="#7a7a7a">%s</FONT></TD>'
                         % (txt, fr))
            else:
                L.append('        <TD ALIGN="LEFT" BGCOLOR="#fafafa"> </TD>')
        L.append("      </TR>")
    L.append("      <TR>")
    L.append('        <TD COLSPAN="4" ALIGN="LEFT" BGCOLOR="#fbf7ee">'
             '<FONT COLOR="#8a6a3a">注：AI 智能体列的用例以工具调用（Function Calling）方式为租客、'
             '房东、管理员三端提供服务，不独立面向用户入口。</FONT></TD>')
    L.append("      </TR>")
    L.append("    </TABLE>>];")
    return chr(10).join(L) + chr(10)


fig_4_2_usecase = build_usecase_dot()


# ── 图5-1 系统上下文图 ──────────────────────────────────────────────────────
fig_5_1_context = """
  rankdir=LR;
  splines=polyline;
  nodesep=0.25;
  ranksep=0.55;
  node [fontsize=10];

  subgraph cluster_role {
    label="系统用户";
    fontsize=12;
    style="dashed,rounded";
    color="#c0c0c0";
    fontcolor="#666666";
    node [fillcolor="#dbe7f6"];
    u1 [label="租客（含游客）", fillcolor="#dbe7f6"];
    u2 [label="房东", fillcolor="#dbe7f6"];
    u3 [label="平台管理员", fillcolor="#dbe7f6"];
    { rank=same; u1; u2; u3; }
  }

  sys [label="RentAgent 系统\\n基于 AI 智能体的房屋租赁系统", fillcolor="#dbe7f6",
       color="#2f5f9e", penwidth=2.0, fontsize=12, margin="0.24,0.3"];

  subgraph cluster_ext {
    label="外部系统 / 服务";
    fontsize=12;
    style="dashed,rounded";
    color="#c0c0c0";
    fontcolor="#666666";
    node [fillcolor="#f3e8d8", color="#b08a52"];
    x1 [label="大模型服务\\n任意兼容模型 API"];
    x2 [label="通知通道\\n站内消息 / 邮件短信"];
    x3 [label="图片存储\\n服务器本地目录"];
    x4 [label="地图底图服务"];
    { rank=same; x1; x2; x3; x4; }
  }

  u1 -> sys [label="业务使用", dir=both];
  u2 -> sys [label="业务使用", dir=both];
  u3 -> sys [label="管理与审核", dir=both];
  sys -> x1 [label="模型调用", dir=both];
  sys -> x2 [label="通知下发"];
  sys -> x3 [label="图片读写"];
  sys -> x4 [label="底图请求"];
"""


# ── 图5-2 系统总体架构图 ────────────────────────────────────────────────────
fig_5_2_arch = """
  rankdir=TB;
  ranksep=0.5;
  node [shape=plaintext, fontname="%s", fontsize=11];

  arch [label=<
    <TABLE BORDER="1" CELLBORDER="1" CELLSPACING="0" CELLPADDING="6"
           COLOR="#4a7ebb" BGCOLOR="white">
      <TR>
        <TD COLSPAN="2" ALIGN="LEFT" BGCOLOR="#dbe7f6">
          <B>表现层</B>　React 18 + TypeScript + Vite + Ant Design 5 + ECharts<br/>
          租客端（找房 / 预约 / 签约 / AI 对话）　·　房东端（发布 / 管理 / 定价）<br/>
          管理端（审核 / 看板 / 知识库维护）<br/>
          <FONT COLOR="#31527a">分发形态：浏览器 Web 版　·　Electron 桌面端（同一份构建产物，桌面端不新增功能）</FONT>
        </TD>
      </TR>
      <TR>
        <TD COLSPAN="2" ALIGN="LEFT" BGCOLOR="#eef4fb">
          <B>业务层</B>　Spring Boot 3 RESTful API（MyBatis-Plus + JWT / RBAC）<br/>
          用户与权限　·　房源管理　·　检索与推荐　·　交易与合同<br/>
          评价与消息　·　后台管理
        </TD>
      </TR>
      <TR>
        <TD COLSPAN="2" ALIGN="LEFT" BGCOLOR="#f6f0e4">
          <B>智能体层（Agent Layer）</B>　LLM 网关（三协议可切换）· 意图识别与提示词编排 · 多轮对话记忆<br/>
          工具集：房源检索 / 预约 / 合同生成 / 定价分析　·　RAG：房源知识库 · 租赁政策 FAQ<br/>
          <FONT COLOR="#8a6a3a">外部依赖：LLM API（网关按协议适配，端点与模型可配置、可一键切换，系统不绑定厂商）</FONT>
        </TD>
      </TR>
      <TR>
        <TD COLSPAN="2" ALIGN="LEFT" BGCOLOR="#eaf3ea">
          <B>数据层</B>　MySQL 8（18 张业务表，utf8mb4 / InnoDB）　·　Redis 7（缓存、会话）<br/>
          向量检索（Embedding，Redis Stack / PGVector 演进预留）　·　图片文件目录
        </TD>
      </TR>
    </TABLE>>];
""" % CN


if __name__ == "__main__":
    render("fig_2_1_pain", fig_2_1_pain)
    render("fig_4_1_flow", fig_4_1_flow)
    render("fig_4_2_usecase", fig_4_2_usecase)
    render("fig_5_1_context", fig_5_1_context)
    render("fig_5_2_arch", fig_5_2_arch)
