#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""以《概要设计模板》为基底生成 RentAgent《概要设计》docx。

策略：
  1. 复用模板的样式表、页面设置、页眉页脚（保持模板版式）；
  2. 更新封面、文件修改控制表、页眉版本号；
  3. 重建目录域（TOC 1-3 级，占位页码 → 由 PDF 实测校准）；
  4. 按模板章节骨架重建正文：1 文档概述 / 2 系统结构图 / 3 模块详细概述 /
     4 数据库设计 / 5 接口设计，配图与表格按序编号。
"""
import copy
import pathlib
import re
import shutil
import subprocess
import sys

from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Emu, Pt, RGBColor

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import content as C

BASE = pathlib.Path(__file__).parent
TPL = BASE / "tpl.docx"
# 插图既可能平铺在本目录（仓库现状），也可能放在 figures/ 子目录
FIG = BASE / "figures" if (BASE / "figures").is_dir() else BASE
USABLE_CM = 16.99  # 页面可用宽度（A4 - 左右页边距）
TPL_SRC = BASE.parent / "概要设计模板.doc"   # 学校下发的模板（二进制 doc）


def ensure_tpl():
    """模板副本缺失时，从上一级目录的学校模板转出 docx 版式底版。"""
    if TPL.exists():
        return
    subprocess.run(["soffice", "--headless", "--convert-to", "docx",
                    "--outdir", str(BASE), str(TPL_SRC)], check=True,
                   capture_output=True, timeout=300)
    # soffice 以源文件名输出（概要设计模板.docx），改名为本脚本约定的 tpl.docx
    converted = BASE / (TPL_SRC.stem + ".docx")
    if converted.exists():
        converted.replace(TPL)
    if not TPL.exists():
        raise SystemExit(f"模板转换失败：{TPL_SRC}")


# ── 底层工具 ────────────────────────────────────────────────────────────────
def qn_w(tag):
    return qn("w:" + tag)


def set_para_runs(p, text, keep_format_of=0, font=None, size=None, bold=None):
    """清空段落内容（含修订标记），写入单个 run；沿用原首个 run 的字符格式。"""
    if hasattr(p, "_p"):
        p = p._p
    pPr = p.find(qn_w("pPr"))
    rPr_src = None
    for r in p.findall(qn_w("r")):
        rPr = r.find(qn_w("rPr"))
        if rPr is not None:
            rPr_src = copy.deepcopy(rPr)
        break
    # 删除除 pPr 外的所有子元素（含 w:ins / w:del / w:bookmarkStart 等）
    for child in list(p):
        if child is not pPr:
            p.remove(child)
    r = OxmlElement("w:r")
    if rPr_src is not None:
        r.append(rPr_src)
    rPr = r.find(qn_w("rPr"))
    if rPr is None:
        rPr = OxmlElement("w:rPr")
        r.insert(0, rPr)
    if font:
        rf = rPr.find(qn_w("rFonts"))
        if rf is None:
            rf = OxmlElement("w:rFonts")
            rPr.insert(0, rf)
        rf.set(qn_w("ascii"), font)
        rf.set(qn_w("eastAsia"), font)
        rf.set(qn_w("hAnsi"), font)
    if size:
        for tag in ("sz", "szCs"):
            e = rPr.find(qn_w(tag))
            if e is None:
                e = OxmlElement("w:" + tag)
                rPr.append(e)
            e.set(qn_w("val"), str(int(size * 2)))
    if bold is not None:
        e = rPr.find(qn_w("b"))
        if e is None:
            e = OxmlElement("w:b")
            rPr.append(e)
        e.set(qn_w("val"), "1" if bold else "0")
    t = OxmlElement("w:t")
    t.set(qn("xml:space"), "preserve")
    t.text = text
    r.append(t)
    p.append(r)
    return p


# ── 文档级构件 ──────────────────────────────────────────────────────────────
class Builder:
    def __init__(self, doc):
        self.doc = doc
        self.toc = []          # [(level, number, title)] 供目录使用
        self.fig_no = {}
        self.tbl_no = {"c3": 0}
        self.fig_c3 = 0

    # ---- 段落 ----
    def para(self, text="", style="正文缩进", align=None, size=None, bold=None,
             font=None, space_after=None, first=False):
        if first:
            p = self.doc.paragraphs[-1]
        else:
            p = self.doc.add_paragraph(style=style or "Normal")
        set_para_runs(p, text, font=font, size=size, bold=bold)
        if align is not None:
            p.alignment = align
        if space_after is not None:
            p.paragraph_format.space_after = Pt(space_after)
        return p

    def body(self, text):
        return self.para(text, style="正文缩进")

    def body_many(self, texts):
        for t in texts:
            self.body(t)

    def heading(self, level, number, title, break_before=False):
        p = self.doc.add_paragraph(style=f"Heading {level}")
        # 关闭继承自 Heading 样式的自动编号，编号由文本显式给出
        pPr = p._p.get_or_add_pPr()
        numPr = OxmlElement("w:numPr")
        ilvl = OxmlElement("w:ilvl")
        ilvl.set(qn_w("val"), "0")
        nid = OxmlElement("w:numId")
        nid.set(qn_w("val"), "0")
        numPr.append(ilvl)
        numPr.append(nid)
        pPr.append(numPr)
        full = f"{number} {title}" if number else title
        set_para_runs(p._p, full)
        if break_before:
            # 用段落属性分页，避免独立分页段落在前页占满时产生空白页
            p.paragraph_format.page_break_before = True
        self.toc.append((level, number, title))
        return p

    def page_break(self):
        p = self.doc.add_paragraph(style="Normal")
        p.add_run().add_break(WD_BREAK.PAGE)
        return p

    def caption(self, text):
        """图题：置于图下方。"""
        p = self.doc.add_paragraph(style="Normal")
        set_para_runs(p._p, text, size=10.5)
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.space_before = Pt(3)
        p.paragraph_format.space_after = Pt(9)
        return p

    def tcaption(self, text):
        """表题：按模板体例置于表格上方，并与表格保持同页。"""
        p = self.doc.add_paragraph(style="Normal")
        set_para_runs(p._p, text, size=10.5)
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.space_before = Pt(9)
        p.paragraph_format.space_after = Pt(3)
        p.paragraph_format.keep_with_next = True
        return p

    def code(self, lines):
        for ln in lines:
            p = self.doc.add_paragraph(style="Normal")
            set_para_runs(p._p, ln if ln else " ", font="Consolas", size=9)
            pf = p.paragraph_format
            pf.space_after = Pt(0)
            pf.space_before = Pt(0)
            pf.left_indent = Cm(1.0)
            pf.line_spacing = 1.0

    def figure(self, name, caption, width_cm=15.5, max_height_cm=19.5):
        path = FIG / f"{name}.png"
        from PIL import Image
        w_px, h_px = Image.open(path).size
        height_cm = width_cm * h_px / w_px
        if height_cm > max_height_cm:
            width_cm = max_height_cm * w_px / h_px
            height_cm = max_height_cm
        p = self.doc.add_paragraph(style="Normal")
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.space_before = Pt(6)
        p.paragraph_format.space_after = Pt(0)
        # 图与紧随其后的图题注保持同页，避免题注被挤到下一页页首
        p.paragraph_format.keep_with_next = True
        p.add_run().add_picture(str(path), width=Cm(width_cm), height=Cm(height_cm))
        self.caption(caption)

    # ---- 表格 ----
    def table(self, headers, rows, widths, bold_last_col=False, size=9,
              header_size=9, align_center_cols=()):
        t = self.doc.add_table(rows=1, cols=len(headers))
        t.alignment = WD_TABLE_ALIGNMENT.CENTER
        self._borders(t)
        hdr = t.rows[0]
        for i, h in enumerate(headers):
            self._cell(hdr.cells[i], h, bold=True, size=header_size,
                       shade="E6E6E6", center=True)
        for row in rows:
            cells = t.add_row().cells
            for i, val in enumerate(row):
                self._cell(cells[i], str(val), size=size,
                           center=(i in align_center_cols))
        self._widths(t, widths)
        self._no_split(t)
        return t

    def _no_split(self, t):
        """行内禁止断开，并让表头在跨页时重复。"""
        for ri, row in enumerate(t.rows):
            trPr = row._tr.get_or_add_trPr()
            cant = OxmlElement("w:cantSplit")
            trPr.append(cant)
            if ri == 0:
                th = OxmlElement("w:tblHeader")
                trPr.append(th)

    def _cell(self, cell, text, bold=False, size=9, shade=None, center=False):
        cell.text = ""
        p = cell.paragraphs[0]
        p.style = self.doc.styles["Normal"]
        # 单元格一律显式对齐，避免继承两端对齐后在窄列中拉开字距、割裂长标识符
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER if center else WD_ALIGN_PARAGRAPH.LEFT
        p.paragraph_format.space_after = Pt(1)
        p.paragraph_format.space_before = Pt(1)
        p.paragraph_format.line_spacing = 1.0
        set_para_runs(p._p, text, size=size, bold=bold)
        if shade:
            tcPr = cell._tc.get_or_add_tcPr()
            shd = OxmlElement("w:shd")
            shd.set(qn_w("val"), "clear")
            shd.set(qn_w("fill"), shade)
            tcPr.append(shd)

    def _borders(self, t):
        tblPr = t._tbl.tblPr
        borders = OxmlElement("w:tblBorders")
        for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
            e = OxmlElement("w:" + edge)
            e.set(qn_w("val"), "single")
            e.set(qn_w("sz"), "4")
            e.set(qn_w("space"), "0")
            e.set(qn_w("color"), "000000")
            borders.append(e)
        tblPr.append(borders)

    def _widths(self, t, widths):
        t.autofit = False
        for row in t.rows:
            for i, w in enumerate(widths):
                if i < len(row.cells):
                    row.cells[i].width = Cm(w)

    # ---- 接口函数规约表（对齐模板 5.2 的表格样式）----
    def spec_table(self, spec):
        W = [1.7, 4.6, 2.1, 2.2, 6.39]
        t = self.doc.add_table(rows=0, cols=5)
        t.alignment = WD_TABLE_ALIGNMENT.CENTER
        self._borders(t)

        def add_row():
            return t.add_row().cells

        # 函数名 / 文件名 / 功能概要
        for label, val in (("函数名", spec["name"]), ("文件名", spec["file"]),
                           ("功能概要", spec["summary"])):
            cells = add_row()
            self._cell(cells[0], label, bold=True, shade="F2F2F2")
            m = cells[1].merge(cells[2]).merge(cells[3]).merge(cells[4])
            self._cell(m, val)
        # 参数标题行
        cells = add_row()
        m = cells[0]
        for c in cells[1:]:
            m = m.merge(c)
        self._cell(m, "参数", bold=True, shade="F2F2F2")
        # 参数表头
        cells = add_row()
        self._cell(cells[0], "类型", bold=True, center=True)
        self._cell(cells[1], "变量名", bold=True, center=True)
        self._cell(cells[2], "I/O", bold=True, center=True)
        m = cells[3].merge(cells[4])
        self._cell(m, "说明", bold=True, center=True)
        # 参数行
        for typ, var, io, desc in spec["params"]:
            cells = add_row()
            self._cell(cells[0], typ, size=9)
            self._cell(cells[1], var, size=9)
            self._cell(cells[2], io, size=9, center=True)
            m = cells[3].merge(cells[4])
            self._cell(m, desc, size=9)
        # 返回值
        ret_rows = [("类型", spec["ret_type"])] + [("值", v) for v in
                                                  [f"{k}：{d}" if k else d for k, d in spec["ret_vals"]]]
        first_idx = len(t.rows)
        cells = add_row()
        self._cell(cells[0], "返回值", bold=True, shade="F2F2F2")
        self._cell(cells[1], "类型", bold=True, center=True)
        self._cell(cells[2], "OUT", center=True)
        m = cells[3].merge(cells[4])
        self._cell(m, "说明", bold=True, center=True, )
        for i, (k, v) in enumerate(ret_rows):
            cells = add_row()
            self._cell(cells[0], "")
            self._cell(cells[1], k, bold=(k == "类型"))
            self._cell(cells[2], "", center=True)
            m = cells[3].merge(cells[4])
            self._cell(m, v, size=9)
        # 纵向合并“返回值”标签
        last_idx = len(t.rows) - 1
        label_cell = t.rows[first_idx].cells[0]
        merged = label_cell
        for r in range(first_idx + 1, last_idx + 1):
            merged = merged.merge(t.rows[r].cells[0])
        self._cell(merged, "返回值", bold=True, shade="F2F2F2")
        # 详细说明
        for label, val in (("详细说明", spec["detail"]), ("使用注意事项", spec["notes"])):
            cells = add_row()
            m = cells[0]
            for c in cells[1:]:
                m = m.merge(c)
            self._cell(m, label, bold=True, shade="F2F2F2")
            cells = add_row()
            m = cells[0]
            for c in cells[1:]:
                m = m.merge(c)
            self._cell(m, val, size=9)
        self._widths(t, W)
        self._no_split(t)
        return t


def set_core_props(doc, title_suffix):
    """覆盖模板残留的文档属性（原标题/作者属于模板来源项目）。"""
    import content as _C
    cp = doc.core_properties
    cp.title = f"{_C.PROJECT.split(' ——')[0]} {title_suffix}"
    cp.subject = _C.PROJECT
    cp.author = _C.AUTHOR
    cp.last_modified_by = _C.AUTHOR
    cp.comments = f"文档编号 {_C.DOC_NO}｜版本 {_C.DOC_VER}｜{_C.DOC_DATE}"


# ── 主流程 ──────────────────────────────────────────────────────────────────
def build(out_path, toc_pages=None, total_pages=None):
    ensure_tpl()
    doc = Document(str(TPL))
    set_core_props(doc, "概要设计")
    body = doc.element.body

    # ── 1. 封面 ─────────────────────────────────────────────────────────────
    cover = ["密级：秘密",
             f"文档编号：{C.DOC_NO}",
             f"项目名称：{C.PROJECT}",
             f"项目编号：{C.PROJ_NO}",
             "", "", "", "",
             "概要设计",
             f"版本：{C.DOC_VER}",
             C.DOC_DATE,
             "", "", "", "",
             C.ORG,
             "(版权所有，翻版必究)"]
    for i, txt in enumerate(cover):
        set_para_runs(doc.paragraphs[i]._p, txt)

    # 压缩封面空段落，使版权行与页数表并入首页
    cover_paras = [doc.paragraphs[i]._p for i in range(18)]
    empties = []
    for el in cover_paras:
        txt = "".join(t.text or "" for t in el.iter(qn_w("t")))
        if txt.strip() == "":
            empties.append(el)
    for el in empties:
        from docx.text.paragraph import Paragraph
        pp = Paragraph(el, doc.paragraphs[0]._parent)
        pp.paragraph_format.space_before = Pt(0)
        pp.paragraph_format.space_after = Pt(0)
        pp.paragraph_format.line_spacing = Pt(7)
        for r in el.findall(qn_w("r")):
            rPr = r.find(qn_w("rPr"))
            if rPr is None:
                rPr = OxmlElement("w:rPr")
                r.insert(0, rPr)
            for tag in ("sz", "szCs"):
                e = rPr.find(qn_w(tag))
                if e is None:
                    e = OxmlElement("w:" + tag)
                    rPr.append(e)
                e.set(qn_w("val"), "12")
    # 删除多余空行（保留必要留白），使封面收进一页
    for el in [empties[1], empties[2], empties[6], empties[7]]:
        el.getparent().remove(el)

    # ── 2. 文件修改控制表（tables[1]）──
    t = doc.tables[1]
    while len(t.rows) > 1:
        t._tbl.remove(t.rows[-1]._tr)
    for r in C.CHANGE_ROWS:
        cells = t.add_row().cells
        for i, v in enumerate(r):
            cells[i].text = ""
            set_para_runs(cells[i].paragraphs[0]._p, v, size=9)
    # 该表来自模板、不经 b.table() 的 _no_split，跨页时表头不会重复——
    # 版本行文字很长必然跨页，这里显式让首行作为重复表头（保留行内可拆分，避免整行被推到下页留下大片空白）
    trPr = t.rows[0]._tr.get_or_add_trPr()
    if not trPr.findall(qn_w("tblHeader")):
        trPr.append(OxmlElement("w:tblHeader"))

    # ── 3. 页眉版本号 ───────────────────────────────────────────────────────
    for sec in doc.sections:
        try:
            hdr = sec.header
        except Exception:
            continue
        for p in hdr.paragraphs:
            for r in p.runs:
                if r.text.strip() == "0.1.0":
                    r.text = C.DOC_VER
                if "概要设计书" in r.text:
                    r.text = ("RentAgent 概要设计书" + " " * 56 + "版本：")

    # ── 4. 删除模板正文（保留封面/修改控制/目录，及末尾 sectPr）──
    children = list(body)
    for el in children[26:-1]:
        body.remove(el)

    b = Builder(doc)
    # ── 5. 正文内容 ─────────────────────────────────────────────────────────
    section1(b)
    section2(b)
    section3(b)
    section4(b)
    section5(b)

    # ── 6. 重建目录 ─────────────────────────────────────────────────────────
    rebuild_toc(doc, b.toc, toc_pages)

    # ── 7. 总页数（封面表格）──
    if total_pages:
        t0 = doc.tables[0]
        vals = {"总页数": str(total_pages), "正文": str(max(total_pages - 4, 0)),
                "附录": "无", "生效日期": C.DOC_DATE.replace("-", ".")}
        for row in t0.rows:
            for i, cell in enumerate(row.cells):
                key = cell.text.strip()
                if key in vals and i + 1 < len(row.cells):
                    set_para_runs(row.cells[i + 1].paragraphs[0]._p, vals[key], size=8)
        # 编制 / 批准 签署栏
        for row in t0.rows:
            for i, cell in enumerate(row.cells):
                key = cell.text.strip()
                if key == "编制" and i + 1 < len(row.cells):
                    set_para_runs(row.cells[i + 1].paragraphs[0]._p, C.AUTHOR)
                if key == "批准" and i + 1 < len(row.cells):
                    set_para_runs(row.cells[i + 1].paragraphs[0]._p, C.APPROVER)

    doc.save(str(out_path))
    return b.toc


# ── 第 1 章 ─────────────────────────────────────────────────────────────────
def section1(b):
    b.heading(1, "1", "文档概述", break_before=False)
    b.heading(2, "1.1", "文档目的和范围")
    b.body_many(C.SEC1_PURPOSE)
    b.heading(2, "1.2", "术语/缩略语")
    b.body("本文档使用的术语与缩略语如下表所示。")
    b.tcaption("表1-1 术语与缩略语")
    b.table(["序号", "术语/缩略语", "说明"], C.TERMS, [1.3, 3.4, 12.29],
            align_center_cols=(0,))
    b.heading(2, "1.3", "参考文档")
    b.body("本文档编写所依据或引用的文档如下表所示。")
    b.tcaption("表1-2 参考文档")
    b.table(["序号", "文档名", "作者", "时间", "版本"], C.REF_DOCS,
            [1.1, 6.6, 2.6, 2.69, 3.9], align_center_cols=(0, 3, 4))


# ── 第 2 章 ─────────────────────────────────────────────────────────────────
def section2(b):
    b.heading(1, "2", "系统结构图", break_before=True)
    b.body_many(C.SEC2_INTRO)
    b.figure("fig_2_1_system", "图2-1  系统结构图", width_cm=16.2)


# ── 第 3 章 ─────────────────────────────────────────────────────────────────
MOD_FIGS = {
    "auth": ("fig_3_1_mod_auth", "fig_3_1_cls_auth"),
    "house": ("fig_3_1_mod_house", "fig_3_1_cls_house"),
    "search": ("fig_3_1_mod_search", "fig_3_1_cls_search"),
    "ai": ("fig_3_1_mod_ai_agent", "fig_3_1_cls_ai"),
    "trade": ("fig_3_1_mod_trade", "fig_3_1_cls_trade"),
    "interact": ("fig_3_1_mod_interact", "fig_3_1_cls_interact"),
    "admin": ("fig_3_1_mod_admin", "fig_3_1_cls_admin"),
}


def section3(b):
    b.heading(1, "3", "模块详细概述", break_before=True)
    b.body_many(C.SEC3_INTRO)
    b.tcaption("表3-1 模块划分总览")
    b.table(["序号", "模块名称", "覆盖需求", "主要职责", "主要实现组件"], C.MODULE_OVERVIEW,
            [1.1, 3.2, 3.0, 5.2, 4.49], align_center_cols=(0,))

    fig_n = 0
    tbl_n = 1
    for idx, m in enumerate(C.MODULES_CONTENT, start=1):
        num = f"3.{idx}"
        b.heading(2, num, m["title"], break_before=True)
        b.body_many(m["intro"])

        b.heading(3, f"{num}.1", f"{m['title']}功能定义")
        tbl_n += 1
        b.tcaption(f"表3-{tbl_n} {m['title']}功能定义")
        b.table(["序号", "功能点", "功能点详细说明"], m["funcs"],
                [1.2, 3.4, 12.39], align_center_cols=(0,))

        # 模块名本身可能已含"模块"（如"AI 智能体服务模块"），此处去掉后缀避免"模块模块结构"
        mod_name = m['title'].removesuffix('模块')
        b.heading(3, f"{num}.2", f"{mod_name}模块结构")
        b.body(f"{m['title']}的模块结构如下图所示。")
        fig_n += 1
        mod_fig, cls_fig = MOD_FIGS[m["key"]]
        wid = 16.2 if m["key"] == "ai" else 12.6
        b.figure(mod_fig, f"图3-{fig_n}  {mod_name}模块结构图", width_cm=wid)

        b.heading(3, f"{num}.3", f"{m['title']}类图")
        fig_n += 1
        # 类图信息密度最高（十余个类框），按正文栏宽上限出图，避免等比缩小后框内文字过小
        b.figure(cls_fig, f"图3-{fig_n}  {m['title']}类图", width_cm=16.2)
        b.body(m["cls_note"])
        b.body("模块对外接口函数清单如下表所示，函数级规约见第 5.2 节。")
        tbl_n += 1
        b.tcaption(f"表3-{tbl_n} {m['title']}接口说明")
        b.table(["序号", "模块名称", "接口函数", "函数说明"], m["apis"],
                [1.0, 2.8, 6.6, 6.59], align_center_cols=(0,))


# ── 第 4 章 ─────────────────────────────────────────────────────────────────
def section4(b):
    b.heading(1, "4", "数据库设计", break_before=True)
    b.heading(2, "4.1", "数据库引擎概述")
    b.body_many(C.SEC41_ENGINE)

    b.heading(2, "4.2", "数据库概要设计")
    b.body_many(C.SEC42_ER)
    items = C.SEC42_ER_LIST
    for i, it in enumerate(items):
        b.para(f"（{i + 1}）{it}", style="正文缩进")
    b.body_many(C.SEC42_TAIL)
    b.figure("fig_4_1_er", "图4-1  数据库表间关系图", width_cm=16.2)
    b.tcaption("表4-1 业务表清单与关键字段")
    b.table(["序号", "表名", "中文名", "主键", "外键", "关键字段与约束"], C.TABLE_LIST,
            [0.9, 3.3, 1.5, 0.9, 2.7, 7.69], size=8, align_center_cols=(0, 3))
    b.tcaption("表4-2 全局物理约定")
    b.table(["项目", "约定", "依据"], C.PHYS_CONV, [3.0, 7.0, 6.99])
    b.tcaption("表4-3 关键索引设计")
    b.table(["表名", "索引", "类型", "设计依据"], C.INDEX_DESIGN,
            [3.4, 5.4, 1.8, 6.39], align_center_cols=(2,))


# ── 第 5 章 ─────────────────────────────────────────────────────────────────
def section5(b):
    b.heading(1, "5", "接口设计", break_before=True)
    b.heading(2, "5.1", "全局变量")
    b.body_many(C.SEC51_INTRO)
    b.tcaption("表5-1 业务状态字典")
    b.table(["类别", "标识", "取值与含义", "用途"], C.STATUS_DICT,
            [2.6, 4.2, 6.79, 3.4])
    b.body_many(C.SEC51_MID)
    b.tcaption("表5-2 业务错误码")
    b.table(["错误码", "枚举名", "提示信息"], C.ERROR_CODES, [2.0, 5.4, 9.59],
            align_center_cols=(0,))
    b.body_many(C.SEC51_TAIL)
    b.code(C.USER_CONTEXT_CODE)
    b.body("")

    b.heading(2, "5.2", "模块间接口函数设计")
    b.body("本节按模块列出接口函数的详细规约。函数名统一采用“动词 + 业务对象”命名，"
           "同名的重载以参数类型区分；所有函数均以统一响应体 R<T> 返回（SSE 流式接口除外）。")

    # 按模块分组输出规约表
    order = ["用户与权限模块", "房源管理模块", "检索与推荐模块", "AI 智能体服务模块",
             "交易与合同模块", "评价与消息模块", "后台管理模块"]
    for i, mod in enumerate(order, start=1):
        b.heading(3, f"5.2.{i}", mod)
        specs = [s for s in C.SPECS if s["module"] == mod]
        b.body(f"{mod}共包含 {len(specs)} 个对外接口函数，规约如下。")
        for j, s in enumerate(specs, start=1):
            if j > 1:
                b.body("")
            b.spec_table(s)
            b.body("")

    b.heading(2, "5.3", "关键数据结构定义")
    b.body_many(C.SEC53_INTRO)
    b.heading(3, "5.3.1", "统一响应结构")
    b.code(C.CODE_RESPONSE)
    b.heading(3, "5.3.2", "分页结构")
    b.code(C.CODE_PAGE)
    b.heading(3, "5.3.3", "核心 DTO 结构")
    b.body("入参 DTO 统一以 record 定义并配合 Bean Validation 注解完成参数校验，"
           "校验失败由全局异常处理器转换为 1000 错误码。")
    b.code(C.CODE_DTO)
    b.heading(3, "5.3.4", "核心 VO 结构")
    b.body("出参中需要跨表拼装或承载 AI 分析结果的对象以 VO 定义，如下所示。")
    b.code(C.CODE_VO)


# ── 目录重建 ────────────────────────────────────────────────────────────────
def rebuild_toc(doc, toc, toc_pages):
    body = doc.element.body
    sdt = None
    for el in body:
        if el.tag == qn_w("sdt"):
            sdt = el
            break
    assert sdt is not None, "模板目录内容控件未找到"
    sc = sdt.find(qn_w("sdtContent"))
    children = list(sc)
    samples = {}
    for el in children:
        if el.tag != qn_w("p"):
            continue
        pPr = el.find(qn_w("pPr"))
        st = pPr.find(qn_w("pStyle")) if pPr is not None else None
        if st is None:
            continue
        name = st.get(qn_w("val"))
        if name in ("TOC1", "TOC2", "TOC3") and name not in samples:
            samples[name] = copy.deepcopy(el)
        # 同时替换模板里 TOC1 的编号（模板为静态文本）
        for r in el.iter(qn_w("r")):
            for t in r.iter(qn_w("t")):
                if t.text == "1" and name == "TOC1":
                    t.text = "1"
    for el in children:
        sc.remove(el)

    # 字段起始段
    p0 = copy.deepcopy(samples["TOC1"])
    for r in list(p0.findall(qn_w("r"))):
        p0.remove(r)
    for hl in list(p0.findall(qn_w("hyperlink"))):
        p0.remove(hl)
    seq = [("begin", None), ("instr", ' TOC \\o "1-3" \\h \\z \\u '), ("separate", None)]
    for kind, txt in seq:
        r = OxmlElement("w:r")
        rPr = OxmlElement("w:rPr")
        r.append(rPr)
        if kind == "instr":
            it = OxmlElement("w:instrText")
            it.set(qn("xml:space"), "preserve")
            it.text = txt
            r.append(it)
        else:
            fc = OxmlElement("w:fldChar")
            fc.set(qn_w("fldCharType"), kind)
            r.append(fc)
        p0.append(r)
    sc.append(p0)

    # 目录条目
    for level, number, title in toc:
        sample = samples.get(f"TOC{level}", samples["TOC1"])
        p = copy.deepcopy(sample)
        for r in list(p.findall(qn_w("r"))):
            p.remove(r)
        for hl in list(p.findall(qn_w("hyperlink"))):
            p.remove(hl)
        rPr_src = None
        for hl in sample.findall(qn_w("hyperlink")):
            for r in hl.findall(qn_w("r")):
                rPr_src = r.find(qn_w("rPr"))
                break
            if rPr_src is not None:
                break
        if rPr_src is None:
            for r in sample.findall(qn_w("r")):
                rPr_src = r.find(qn_w("rPr"))
                if rPr_src is not None:
                    break

        def mkrun(text=None, tab=False):
            r = OxmlElement("w:r")
            if rPr_src is not None:
                r.append(copy.deepcopy(rPr_src))
            if tab:
                r.append(OxmlElement("w:tab"))
            if text is not None:
                t = OxmlElement("w:t")
                t.set(qn("xml:space"), "preserve")
                t.text = text
                r.append(t)
            return r

        page = (toc_pages or {}).get((level, number), "1")
        p.append(mkrun(text=number))
        p.append(mkrun(tab=True))
        p.append(mkrun(text=title))
        p.append(mkrun(tab=True))
        p.append(mkrun(text=str(page)))
        sc.append(p)

    # 字段结束段
    pz = copy.deepcopy(samples["TOC1"])
    for r in list(pz.findall(qn_w("r"))):
        pz.remove(r)
    for hl in list(pz.findall(qn_w("hyperlink"))):
        pz.remove(hl)
    r = OxmlElement("w:r")
    r.append(OxmlElement("w:rPr"))
    fc = OxmlElement("w:fldChar")
    fc.set(qn_w("fldCharType"), "end")
    r.append(fc)
    pz.append(r)
    sc.append(pz)


# ── 目录页码校准 ────────────────────────────────────────────────────────────
def measure_pages(docx_path, toc):
    """转 PDF 后定位每个标题的物理页码（与页眉 PAGE 域一致）。

    注意：目录页自身包含全部标题文字，必须先排除目录页，否则全部命中目录页。
    目录页特征：含点前导符（......）或“目录”标题。
    """
    subprocess.run(["soffice", "--headless", "--convert-to", "pdf",
                    "--outdir", str(BASE / "_pdf"), str(docx_path)],
                   check=True, capture_output=True, timeout=600)
    pdf = BASE / "_pdf" / (pathlib.Path(docx_path).stem + ".pdf")
    import pymupdf
    d = pymupdf.open(str(pdf))
    texts = [d[i].get_text() for i in range(len(d))]

    body_start = 0
    for i, txt in enumerate(texts):
        flat = txt.replace(" ", "").replace("\n", "")
        if "......" in txt or flat.startswith("目录"):
            body_start = i + 1

    pages = {}
    for level, number, title in toc:
        needle = f"{number} {title}".replace(" ", "")
        found = None
        for i in range(body_start, len(texts)):
            flat = texts[i].replace(" ", "").replace("\n", "")
            if needle in flat:
                found = i + 1
                break
        if found is None:
            for i in range(body_start, len(texts)):
                flat = texts[i].replace(" ", "").replace("\n", "")
                if title.replace(" ", "") in flat:
                    found = i + 1
                    break
        pages[(level, number)] = found or 1
    return pages, len(d)


# ── 入口：两遍生成，第二遍写入实测页码，并另存交付用 PDF ─────────────────────
OUT = BASE.parent / "概要设计.docx"


def main():
    tmp = BASE / "_pass1.docx"
    toc = build(tmp)
    pages, total = measure_pages(tmp, toc)
    print(f"pass1: {total} pages")
    build(OUT, toc_pages=pages, total_pages=total)

    # 校验第二遍页码是否漂移
    pages2, total2 = measure_pages(OUT, toc)
    drift = {k: (pages[k], pages2[k]) for k in pages if pages[k] != pages2.get(k)}
    print(f"pass2: {total2} pages | 页码漂移: {drift if drift else '无'}")

    # 交付用 PDF：直接复用校准过程中转出的 PDF，避免重复转换
    shutil.copyfile(BASE / "_pdf" / f"{OUT.stem}.pdf", OUT.with_suffix(".pdf"))
    print("输出:", OUT, "与", OUT.with_suffix(".pdf"))


if __name__ == "__main__":
    main()
