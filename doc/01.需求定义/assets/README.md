# 《需求定义说明书》配图与生成脚本

本目录是 `doc/01.需求定义/需求定义说明书.docx` 的可复现素材，用于修改配图或重建文档。

## 文件说明

| 文件 | 说明 |
| ---- | ---- |
| `figures/fig_*.dot` | Graphviz 图源文件（纯文本，可直接改文字/连线） |
| `figures/fig_*.png` | 由 dot 渲染出的插图（已嵌入 docx），正文按 13~16cm 宽插入 |
| `dotgen_reqdef.py` | 出图脚本：生成全部 5 张 `fig_*.png` |
| `content_reqdef.py` | 文档正文内容（第 1~9 章的文字与表格数据） |
| `build_reqdef.py` | 文档生成脚本：以模板为基底生成 docx，并用 PDF 两遍校准目录页码与总页数 |
| `tpl.docx` | 版式底版（学校《概要设计模板》转换所得，提供封面/页眉/目录域/标题与表格样式） |

> 版式构件（`Builder`、目录域重建、页码校准）复用 `doc/03.概要设计/assets/build_doc.py`，
> 因此本目录的脚本需与 `doc/03.概要设计/assets/` 同时存在；这样两阶段交付物的版式只有一套实现。

## 配图清单（对应文档中的图号）

| 图号 | 文件 | 内容 |
| ---- | ---- | ---- |
| 图2-1 业务痛点与系统应对映射图 | `fig_2_1_pain.png` | 六个业务痛点 → 对应系统能力（含 FR 编号） |
| 图4-1 核心业务流程图 | `fig_4_1_flow.png` | 房源供给链与需求交易链的主流程，含判断分支与 FR 编号 |
| 图4-2 角色—用例对照图 | `fig_4_2_usecase.png` | 四个角色 × 用例矩阵，含 FR 编号 |
| 图5-1 系统上下文图 | `fig_5_1_context.png` | 系统边界：三类用户 + 四类外部服务 |
| 图5-2 系统总体架构图 | `fig_5_2_arch.png` | 表现层 / 业务层 / 智能体层 / 数据层 |

## 重新生成

```bash
# 仅重出全部插图（需要 graphviz 的 dot 命令）
python3 dotgen_reqdef.py

# 重建整份文档（需要 graphviz、libreoffice、python-docx、pymupdf、Pillow）
python3 build_reqdef.py
```

脚本会先出一版占位页码（`_pass1.docx`），转 PDF 实测每个标题的物理页码后再生成终版，
因此**目录页码无需在 Word 里按 F9 更新**；运行结束会打印页码漂移检查结果（应显示“无”）。
终版同时输出两份交付文件到上一级目录：`doc/01.需求定义/需求定义说明书.docx` 与同名 `.pdf`
（PDF 复用页码校准过程中转出的文件）。

## 修改内容的两种方式

1. **改文字/表格**：编辑 `content_reqdef.py` 中的常量（如 `TABLE_5_2`、`SEC4_RULES_LIST`），再跑 `build_reqdef.py`。
   章节骨架与图注在 `build_reqdef.py` 的 `section1()~section9()` 中。
2. **换配图**：改 `figures/*.dot` 后重跑 `dotgen_reqdef.py`，或用 Visio/draw.io/ProcessOn 重画后
   按同名覆盖 `figures/fig_*.png`，再跑 `build_reqdef.py`。

图源宽度请控制在约 18cm 以内：正文可用宽度 16.99cm，图按 13~16cm 插入，
图源过宽会让插入后的中文字号小于 9pt 而不可读。
