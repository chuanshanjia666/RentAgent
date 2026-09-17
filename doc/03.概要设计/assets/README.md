# 《概要设计》配图与生成脚本

本目录是 `doc/03.概要设计/概要设计.docx` 的可复现素材，供后续修改配图或重建文档使用。

## 文件说明

| 文件 | 说明 |
| ---- | ---- |
| `fig_*.dot` | Graphviz 图源文件（纯文本，可直接改文字/连线） |
| `fig_*.png` | 由 dot 渲染出的插图（已嵌入 docx，平铺在本目录） |
| `dotgen.py` | 出图脚本：从 `fig_*.dot` 生成全部 `fig_*.png` |
| `content.py` | 文档正文内容（第 1~5 章的文字、表格数据、接口规约） |
| `build_doc.py` | 文档生成脚本：以模板为基底生成 docx，并用 PDF 两遍校准目录页码 |
| `gen_db_dict.py` | 数据库表结构字典生成脚本：按 `数据库参考示例.xls` 体例生成 `../数据库表.xlsx` |

## 配图清单（对应文档中的图号）

| 图号 | 文件 |
| ---- | ---- |
| 图2-1 系统结构图 | `fig_2_1_system.png` |
| 图3-1/3-3/3-5/3-7/3-9/3-11/3-13 各模块结构图 | `fig_3_1_mod_*.png`（7 个模块） |
| 图3-2/3-4/3-6/3-8/3-10/3-12/3-14 各模块类图 | `fig_3_1_cls_*.png`（7 个模块） |
| 图4-1 数据库表间关系图 | `fig_4_1_er.png` |

## 重新生成

```bash
# 仅重出所有插图（需要 graphviz 的 dot 命令）
python3 dotgen.py

# 重建整份文档（需要 graphviz、libreoffice、python-docx、pymupdf、Pillow）
python3 build_doc.py
```

重建文档时 `build_doc.py` 依赖版式底版 `tpl.docx`（学校《概要设计模板》转出的 docx）；
该文件缺失时会自动用 `soffice --headless --convert-to docx ../概要设计模板.doc` 生成。

> 本目录的版式构件（`Builder`、目录域重建、页码校准）同时被
> `doc/01.需求定义/assets/build_reqdef.py` 复用，两份 Word 交付物因此共用一套版式实现；
> 改动 `build_doc.py` 中的构件会影响两阶段交付物的重新生成。

## 修改配图的两种方式

1. **只改文字**：直接编辑对应的 `fig_*.dot` 中的 `label="..."`，再执行 `dot -Tpng -Gdpi=170 -o fig_x.png fig_x.dot`。
2. **换工具重画**：本目录的 `fig_*.png` 可整体替换为任意绘图工具（Visio、draw.io、ProcessOn、StarUML 等）导出的图片，
   只要保持文件名不变，再重新运行 `build_doc.py` 即可。

## 数据库表结构字典（doc/03.概要设计/数据库表.xlsx）

按参考文件 `数据库参考示例.xls` 的体例生成：**一表一 sheet**，每张表的结构为

1. 第 1 行：表名
2. 第 2 行：`表描述` | 中文表描述 | `做成日期` | 日期
3. 第 3 行：`表名称` | 表名 | `备注` | 索引与外键说明
4. 第 4 行：表头 `类型｜大小｜名称｜非空｜默认值｜补充｜描述｜扩展｜左｜类型｜右｜List显示｜特殊取值描述｜追加日期｜追加原因`
5. 第 5 行起：逐字段一行；末尾空两行后写 `END`

首个 sheet 为 `表清单`（18 张表一览：业务域、字段数、主键、外键、唯一约束）。
另出 `../数据库表.xls` 兼容副本（与 xlsx 内容逐格一致，便于按参考文件的格式提交）。

重新生成：

```bash
python3 gen_db_dict.py      # 需 openpyxl；生成 RentAgent-数据库表.xlsx
```

数据来源为 `docker/mysql-init/01_schema.sql`（字段/类型/非空/默认值/外键/索引）
与《数据库设计简介》4.3 数据字典（中文说明）；改字段请以 `01_schema.sql` 为准并同步更新本表。
