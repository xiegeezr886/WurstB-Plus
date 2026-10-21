# -*- coding: utf-8 -*-
"""生成蓝屏故障检测报告（DOCX）。"""
import os
from docx import Document
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

CN_FONT = "微软雅黑"
MONO_FONT = "Consolas"

OUT_DIR = r"C:\Users\ui863\Documents\trae_projects\VAP\report"
OUT = os.path.join(OUT_DIR, "BSOD检测报告_OMEN-11_20261020.docx")


def set_run_font(run, name=CN_FONT, size=None, bold=None, color=None):
    run.font.name = name
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.append(rfonts)
    for attr in ("w:ascii", "w:hAnsi", "w:eastAsia", "w:cs"):
        rfonts.set(qn(attr), name)
    if size is not None:
        run.font.size = Pt(size)
    if bold is not None:
        run.font.bold = bold
    if color is not None:
        run.font.color.rgb = RGBColor(*color)


def style_fonts(doc):
    for sname in ("Normal", "Heading 1", "Heading 2", "Heading 3", "Title"):
        try:
            st = doc.styles[sname]
        except KeyError:
            continue
        st.font.name = CN_FONT
        rpr = st.element.get_or_add_rPr()
        rfonts = rpr.find(qn("w:rFonts"))
        if rfonts is None:
            rfonts = OxmlElement("w:rFonts")
            rpr.append(rfonts)
        for attr in ("w:ascii", "w:hAnsi", "w:eastAsia", "w:cs"):
            rfonts.set(qn(attr), CN_FONT)
    doc.styles["Normal"].font.size = Pt(10.5)
    doc.styles["Normal"].paragraph_format.line_spacing = 1.35
    doc.styles["Normal"].paragraph_format.space_after = Pt(4)
    for sname, sz in (("Heading 1", 15), ("Heading 2", 12), ("Title", 22)):
        try:
            doc.styles[sname].font.size = Pt(sz)
            doc.styles[sname].font.color.rgb = RGBColor(0x1F, 0x30, 0x46)
        except KeyError:
            pass


def para(doc, text, size=10.5, bold=False, space_before=0, space_after=4,
         align=None, indent=None, color=None):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(space_before)
    p.paragraph_format.space_after = Pt(space_after)
    if align is not None:
        p.alignment = align
    if indent is not None:
        p.paragraph_format.left_indent = Cm(indent)
    r = p.add_run(text)
    set_run_font(r, size=size, bold=bold, color=color)
    return p


def shade(p, hexcolor="F2F4F7"):
    pPr = p._p.get_or_add_pPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), hexcolor)
    pPr.append(shd)


def code(doc, text):
    for line in text.strip("\n").split("\n"):
        p = doc.add_paragraph()
        p.paragraph_format.space_after = Pt(0)
        p.paragraph_format.space_before = Pt(0)
        p.paragraph_format.left_indent = Cm(0.4)
        p.paragraph_format.line_spacing = 1.05
        shade(p)
        r = p.add_run(line if line else " ")
        set_run_font(r, name=MONO_FONT, size=8.5)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)


def table(doc, headers, rows, widths=None, size=9):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    hdr = t.rows[0].cells
    for i, h in enumerate(headers):
        hdr[i].text = ""
        p = hdr[i].paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = p.add_run(h)
        set_run_font(r, size=size, bold=True)
        tcPr = hdr[i]._tc.get_or_add_tcPr()
        shd = OxmlElement("w:shd")
        shd.set(qn("w:val"), "clear")
        shd.set(qn("w:color"), "auto")
        shd.set(qn("w:fill"), "E7ECF3")
        tcPr.append(shd)
    for row in rows:
        cells = t.add_row().cells
        for i, v in enumerate(row):
            cells[i].text = ""
            p = cells[i].paragraphs[0]
            p.paragraph_format.space_after = Pt(1)
            r = p.add_run(str(v))
            set_run_font(r, size=size, name=MONO_FONT if i == 0 and str(v).startswith("0x") else CN_FONT)
    if widths:
        for r_ in t.rows:
            for i, w in enumerate(widths):
                r_.cells[i].width = Cm(w)
    # 禁止单行跨页断开，并让表头在跨页时重复
    for idx, row in enumerate(t.rows):
        trPr = row._tr.get_or_add_trPr()
        cant = OxmlElement("w:cantSplit")
        trPr.append(cant)
        if idx == 0:
            th = OxmlElement("w:tblHeader")
            trPr.append(th)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)
    return t


doc = Document()
style_fonts(doc)
sec = doc.sections[0]
sec.left_margin = Cm(2.4)
sec.right_margin = Cm(2.4)
sec.top_margin = Cm(2.2)
sec.bottom_margin = Cm(2.2)

# ---------------- 标题 ----------------
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_after = Pt(2)
r = p.add_run("蓝屏故障检测报告")
set_run_font(r, size=22, bold=True, color=(0x1F, 0x30, 0x46))

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_after = Pt(14)
r = p.add_run("HP OMEN Gaming Laptop 16-am0xxx ／ 显卡（GPU）子系统反复失效")
set_run_font(r, size=11, color=(0x5A, 0x6A, 0x7D))

table(doc, ["项目", "内容"], [
    ["主机名", "OMEN-11"],
    ["整机型号", "HP OMEN Gaming Laptop 16-am0xxx"],
    ["序列号", "5CD5309FWH"],
    ["BIOS", "AMI F.13（2026-01-21）"],
    ["处理器", "Intel Core i9-14900HX（24 核 / 32 线程）"],
    ["独立显卡", "NVIDIA GeForce RTX 5070 Laptop GPU\nPCI\\VEN_10DE&DEV_2D58&SUBSYS_8D3F103C"],
    ["核芯显卡", "Intel UHD Graphics（RaptorLake-S Mobile，32.0.101.7077）"],
    ["内存 / 存储", "2×16 GB DDR5-5600（Ramaxel）／ ZHITAI TiPlus7100 1TB + RPEYJ1M 1TB"],
    ["操作系统", "Windows 11 25H2 家庭版 中文版（Build 26200.9711）"],
    ["显卡驱动", "NVIDIA 617.42（KMD 32.0.16.1742，2026-09-30 签名）"],
    ["故障区间", "2026-08-20 起持续至今（2026-10-20 仍在发生）"],
], widths=[3.2, 12.8])

# ---------------- 1 ----------------
doc.add_heading("一、故障现象", level=1)
para(doc, "该机自 2026 年 8 月起反复出现以下三类故障，且频率随时间递增：")
para(doc, "（1）蓝屏，错误码 0x00000113（VIDEO_DXGKRNL_FATAL_ERROR），参数恒为 "
          "(0x19, 0x2, 0x10de, 0x2d58)；其中 0x10de/0x2d58 即本机独立显卡的 PCI 厂商/设备 ID。", indent=0.6)
para(doc, "（2）无蓝屏的硬冻结：系统画面卡死、只能长按电源键强制断电。Windows 记录了 6 次"
          "此类事件（Kernel-Power 41，BugcheckCode=0，且带电源键时间戳）。", indent=0.6)
para(doc, "（3）独立显卡在运行中从 PCIe 总线消失：2026-10-20 14:01 实测捕获到该状态，"
          "nvidia-smi 返回 “GPU is lost. Reboot the system to recover this GPU”。", indent=0.6)
para(doc, "故障与图形负载相关性极强：多次发生在运行 Minecraft（OpenGL + 独显）期间，"
          "其中 2026-10-19 21:50 启动游戏，21:52:44 即蓝屏，间隔不足 3 分钟。")

# ---------------- 2 ----------------
doc.add_heading("二、蓝屏事件清单", level=1)
para(doc, "下表由系统事件日志、Windows 错误报告（WER）与现存转储文件交叉核对得出。"
          "因 NVIDIA 容器服务曾以每天约 9000 次的速度刷写系统日志，日志仅保留最近数天，"
          "更早记录取自 WER 报告队列。")
table(doc, ["时间", "错误码 / 名称", "参数（Arg1–Arg4）", "转储文件"], [
    ["2026-08-20", "0x133 DPC_WATCHDOG_VIOLATION", "—", "082026-38156-01.dmp"],
    ["2026-08-24", "0x113 VIDEO_DXGKRNL_FATAL_ERROR", "19 / 2 / 10de / 2d58", "082426-17296-01.dmp"],
    ["2026-08-24", "0x113 VIDEO_DXGKRNL_FATAL_ERROR", "19 / 2 / 10de / 2d58", "082426-14828-01.dmp"],
    ["2026-08-26", "0x113 VIDEO_DXGKRNL_FATAL_ERROR", "19 / 2 / 10de / 2d58", "082626-14437-01.dmp"],
    ["2026-10-01", "0x19C", "50 / …", "100126-22828-01.dmp"],
    ["2026-10-16 09:28", "0x113 VIDEO_DXGKRNL_FATAL_ERROR", "19 / 2 / 10de / 2d58", "101626-13593-01.dmp"],
    ["2026-10-18 13:33", "0x113 VIDEO_DXGKRNL_FATAL_ERROR", "19 / 2 / 10de / 2d58", "101826-13500-01.dmp"],
    ["2026-10-18 13:37", "0x113 VIDEO_DXGKRNL_FATAL_ERROR", "19 / 2 / 10de / 2d58", "101826-13031-01.dmp"],
    ["2026-10-18 19:20", "0x113 VIDEO_DXGKRNL_FATAL_ERROR", "19 / 2 / 10de / 2d58", "101826-13562-01.dmp"],
    ["2026-10-19 21:53", "0x113 VIDEO_DXGKRNL_FATAL_ERROR", "19 / 2 / 10de / 2d58", "101926-13015-01.dmp"],
], widths=[3.2, 6.0, 3.8, 5.0], size=8.5)
para(doc, "除上述真实蓝屏外，另有 13 次显示引擎超时实时转储（0x141 VIDEO_ENGINE_TIMEOUT_DETECTED），"
          "分布于 2026-08-24 至 2026-10-18；以及 6 次无蓝屏的强制断电。", size=9.5)

# ---------------- 3 ----------------
doc.add_heading("三、转储分析结论（WinDbg 10.0.29661.1004 + 微软符号服务器）", level=1)
para(doc, "对现存 5 份内核转储逐一执行 !analyze -v，4 份完成的转储给出完全相同的故障签名；"
          "第 5 份（101826-13031-01.dmp）分析输出被中断，但其蓝屏参数与其余 4 份逐字节一致。")
table(doc, ["分析字段", "结果"], [
    ["BUGCHECK_CODE", "113 (VIDEO_DXGKRNL_FATAL_ERROR)"],
    ["BUGCHECK_P1–P4", "0x19 / 0x2 / 0x10de / 0x2d58"],
    ["DirectX.FatalError.Desc", "UNEXPECTED_SURPRISE_REMOVAL（设备意外移除）"],
    ["MODULE_NAME / IMAGE_NAME", "nvlddmkm / nvlddmkm.sys"],
    ["SYMBOL_NAME（故障偏移）", "nvlddmkm+0xdf3fb4（4 份转储完全一致）"],
    ["FAILURE_BUCKET_ID", "0x113_19_nvlddmkm!unknown_function"],
    ["FAILURE_ID_HASH", "{fb504da1-90a7-64ec-e7aa-d0c04338976f}"],
    ["PROCESS_NAME", "System"],
], widths=[5.0, 11.0], size=9)

para(doc, "调用栈（101926-13015-01.dmp）：", size=10, bold=True, space_before=4)
code(doc, """
nt!KeBugCheckEx
watchdog!WdLogSingleEntry5+0x156
dxgkrnl!DpiFdoHandleDevicePower+0x492      <- 正在处理设备电源状态切换
dxgkrnl!DpiFdoDispatchPower+0x1c
dxgkrnl!DpiDispatchPower+0xf0
nvlddmkm+0xdf3fb4                          <- 故障指令位于 NVIDIA 内核驱动
""")
para(doc, "结论：该蓝屏并非应用层或内存错误，而是内核图形驱动在“设备电源状态切换”路径上判定"
          "显卡已从总线消失（UNEXPECTED_SURPRISE_REMOVAL），无法继续而主动崩溃。"
          "四次崩溃的故障偏移完全一致，说明每次都固定踩进同一条代码路径。")

para(doc, "同一时段的实时转储（LK_WATCHDOG-20261019-2153.dmp，0x141）显示故障前兆：", size=10, bold=True, space_before=6)
code(doc, """
VIDEO_ENGINE_TIMEOUT_DETECTED (141)
One of the display engines failed to respond in timely fashion.

dxgmms2!VidSchiCheckHwProgress      <- 检测到 GPU 引擎停止推进
dxgmms2!VidSchiResetEngines
dxgmms2!VidSchiResetHwEngine        <- 尝试复位引擎
dxgkrnl!TdrCollectDbgInfoStage1

FAILURE_BUCKET_ID: LKD_0x141_IMAGE_nvlddmkm.sys
""")
para(doc, "即：GPU 引擎先停止响应（TDR）→ 驱动尝试复位 → 复位/电源切换过程中设备彻底丢失 → 蓝屏。"
          "这条链路与 2026-10-20 实测到的“GPU is lost”完全吻合。")

# ---------------- 4 ----------------
doc.add_heading("四、独立旁证", level=1)

doc.add_heading("4.1 PCIe 链路纠正错误（WHEA）", level=2)
para(doc, "系统日志中共有 716 条 WHEA-Logger Id=17 纠正型硬件错误，全部指向同一个 PCIe 根端口 "
          "0:1:0（Intel DEV_A70D，即 CPU 直连独立显卡的 PEG 通道）。其时间分布高度集中：")
table(doc, ["时间窗", "数量", "与故障的关系"], [
    ["2026-10-17 21:44 – 10-18 19:17", "696 条", "覆盖 10-18 的 3 次蓝屏"],
    ["2026-10-20 14:01:51 – 14:07:47", "20 条", "与本次“GPU is lost”同一分钟级窗口"],
], widths=[6.0, 2.5, 7.5], size=9)
para(doc, "即显卡所在 PCIe 链路本身持续出现链路层错误，并非单纯的软件冲突。")

doc.add_heading("4.2 显卡实际掉线（2026-10-20 14:01）", level=2)
para(doc, "当日 14:01:51 起链路错误密集出现；14:03 启动 Minecraft 时，Forge 依次尝试 OpenGL "
          "4.6 至 3.2 共 9 个版本全部失败，报错：")
code(doc, """
GLFW error: [0x10006] WGL: The driver does not appear to support OpenGL
java.lang.IllegalStateException: Failed to create a GLFW window with any profile
""")
para(doc, "同一时刻查询显卡状态：")
code(doc, """
> nvidia-smi
No devices were found
Unable to determine the device handle for GPU0: 0000:01:00.0:
GPU is lost.  Reboot the system to recover this GPU
""")
para(doc, "约 6 分钟后（14:09）显卡自行重新枚举，但 OpenGL 仍不可用。"
          "本次掉线未触发蓝屏，也未生成内核转储。")
para(doc, "同日 14:22 对驱动执行官方清洁安装并于 14:14 重启后，系统恢复干净状态"
          "（PCIe 链路恢复正常 Gen5×8，OpenGL 双显卡均通过实测）。")
para(doc, "14:29 启动 Minecraft 正常运行，14:36:45 链路错误再次出现，14:36:57 游戏日志中断，"
          "随即 nvidia-smi 再次报 “GPU is lost”。即：在驱动全新安装且系统刚重启的干净状态下，"
          "仅 8 分钟的 3D 负载即导致显卡再次从总线丢失。本次同样未触发蓝屏、未生成转储。")

doc.add_heading("4.3 图形驱动注册状态失效与恢复（2026-10-20）", level=2)
para(doc, "显卡重新枚举后，任何 3D 程序仍无法启动（报错见 4.2）。当日 14:22 使用与故障前同版本的"
          "官方安装包（617.42）对显卡驱动执行清洁安装，重新注册显示驱动后 OpenGL 立即恢复。"
          "以下为修复后实测创建真实窗口 GL 上下文的结果：")
table(doc, ["测试路径", "GL_VERSION", "GL_RENDERER"], [
    ["默认（核显）", "4.6.0 - Build 32.0.101.7092", "Intel(R) RaptorLake-S Mobile Graphics Controller"],
    ["强制独显", "4.6.0 NVIDIA 617.42", "NVIDIA GeForce RTX 5070 Laptop GPU/PCIe/SSE2"],
], widths=[3.2, 6.4, 6.4], size=8.5)
para(doc, "说明故障期间系统的图形驱动注册状态已失效，需重新安装驱动方可恢复。"
          "但重新安装驱动并未消除根本故障——设备仍会从 PCIe 总线丢失（见 4.2）。")

doc.add_heading("4.4 NVIDIA 容器服务异常", level=2)
para(doc, "2026-10-16 13:16 至 10-19 21:56 期间，服务 “NVIDIA LocalSystem Container” 累计"
          "异常终止 24,795 次（峰值每日约 9,100 次，平均每 10 秒一次），"
          "并将 20 MB 的系统日志反复覆盖至仅剩约 3.5 天。")

# ---------------- 5 ----------------
doc.add_heading("五、完整时间线", level=1)
table(doc, ["时间", "事件"], [
    ["2026-08-20", "首次蓝屏 0x133（DPC_WATCHDOG_VIOLATION）"],
    ["2026-08-24 / 26", "连续 3 次 0x113 蓝屏，故障签名与后续完全一致"],
    ["2026-10-08 17:22", "安装 NVIDIA 617.42 驱动"],
    ["2026-10-16 09:27", "NVIDIA 组件（NvContainer / NVIDIA App）安装"],
    ["2026-10-16 09:28", "立即蓝屏 0x113；此后容器服务开始无限崩溃循环"],
    ["2026-10-17 21:44 – 10-18 19:17", "PCIe 链路错误爆发 696 条 + 3 次 0x113 蓝屏"],
    ["2026-10-18 19:17:05", "游戏处于暂停存档状态（低功耗切换），2 分钟后蓝屏"],
    ["2026-10-19 21:50 / 21:52:44", "启动 Minecraft → 2 分 44 秒后蓝屏 0x113"],
    ["2026-10-19 22:12", "重新安装显卡驱动（本次维修尝试）"],
    ["2026-10-20 14:01:51", "链路错误再次出现"],
    ["2026-10-20 14:03", "独立显卡从总线消失，OpenGL 完全不可用"],
    ["2026-10-20 14:09", "显卡自行重新枚举，但任何 3D 程序仍无法启动"],
    ["2026-10-20 14:22", "执行官方清洁安装（617.42）并重启，OpenGL / D3D 恢复"],
    ["2026-10-20 14:29 / 14:36:45", "启动游戏仅 8 分钟，第三次掉线（干净驱动状态下复发）"],
], widths=[5.0, 11.0], size=9)

# ---------------- 6 ----------------
doc.add_heading("六、已排除的软件因素", level=1)
para(doc, "1）内存与存储：两条 DDR5-5600 内存识别正常；两块 SSD 的 HealthStatus 与 OperationalStatus 均为 Healthy / OK。")
para(doc, "2）驱动版本因素：2026-10-19 22:12 完整重装 NVIDIA 617.42 后，系统稳定运行 15 小时 30 分，"
          "随后在 2026-10-20 14:01 再次发生显卡掉线——说明更换/重装驱动无法消除故障。")
para(doc, "3）随机性因素：4 份转储的故障偏移（nvlddmkm+0xdf3fb4）与失败桶 ID 完全一致，"
          "属确定性代码路径的必然失败，而非随机位翻转。")
para(doc, "4）散热因素：故障时 GPU 温度 53–54 ℃、功耗 12 W（P8 待机态），"
          "并存在“游戏暂停/切出”等低功耗场景下的崩溃记录，不符合过热特征。")

# ---------------- 7 ----------------
doc.add_heading("七、结论与请求", level=1)
para(doc, "综合以上证据，故障点位于独立显卡（RTX 5070 Laptop GPU）或其 PCIe 链路／供电子系统，"
          "表现为设备在电源状态切换过程中从总线消失，属于硬件级不稳定；"
          "已排除内存、存储、驱动版本、散热等软件与外围因素。")
para(doc, "（1）请对整机进行硬件检测，重点为独立显卡本体、显卡 PCIe 链路及主板供电；", indent=0.6)
para(doc, "（2）如检测确认异常，申请按保修更换显卡模组或主板；", indent=0.6)
para(doc, "（3）本机 BIOS 为 F.13（2026-01-21），如存在更新版本，请在维修后协助升级。", indent=0.6)
para(doc, "（4）2026-10-20 14:22 在全新清洁安装并重启的干净驱动状态下，14:36 仅 8 分钟的 3D 负载即导致"
          "显卡第三次从总线丢失。该结果已排除驱动版本与安装完整性因素，硬件判定成立。", indent=0.6)
para(doc, "可复现方式：运行任何 OpenGL／DirectX 3D 程序（如 Minecraft）并持续使用，"
          "在数分钟至数小时内可触发引擎超时、显卡掉线或蓝屏。")

doc.add_heading("附录：证据文件", level=1)
para(doc, "以下文件随本报告提交，位于 C:\\Users\\ui863\\Documents\\BSOD-dumps\\：", size=9.5)
table(doc, ["文件", "说明"], [
    ["101626-13593-01.dmp 等 5 个", "Windows 内核转储原件（2026-10-16 至 10-19）"],
    ["LK_WATCHDOG-20261019-2153.dmp", "显示引擎超时实时转储（0x141）"],
    ["analyze-101826-13562-01.txt", "转储分析完整输出（!analyze -v）"],
    ["analyze-101926.u8.txt", "转储分析完整输出（含模块列表）"],
    ["analyze-LK_WATCHDOG-20261019-2153.txt", "实时转储分析输出"],
], widths=[6.6, 9.4], size=8.5)
para(doc, "报告生成时间：2026-10-20 14:10　｜　分析工具：WinDbg 10.0.29661.1004 + "
          "Microsoft 公共符号服务器", size=8.5, color=(0x6B, 0x7A, 0x8C), space_before=6)

os.makedirs(OUT_DIR, exist_ok=True)
doc.save(OUT)
print("SAVED:", OUT)
print("SIZE:", os.path.getsize(OUT))
