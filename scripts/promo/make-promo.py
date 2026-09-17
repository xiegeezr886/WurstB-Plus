"""Render the WurstB+ Plus v1.5.0 promo video and encode it with ffmpeg.

1920x1080, 30 fps, ~88 s, 8 scenes.  Everything shown comes from the real v1.5
inventory in promo-facts.json (197 hacks, 54 commands, 14 HUD elements, 21
Minecraft versions, 61 release jars) - no invented numbers.

Audio is synthesized here: four-chord pad, eighth-note arpeggio, soft kick and
hats, gentle low-pass, fade in/out.

Drawing model: each scene renders *opaquely* into its own RGB float buffer, and
the render loop blends the outgoing and incoming buffers for the cross-fade.
Not drawing output is just slowness, never a crash.
"""
import io
import json
import math
import os
import shutil
import subprocess
import sys
import time
import wave

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H, FPS = 1920, 1080, 30
HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))          # .../WurstB-Plus-main
FACTS = json.load(io.open(os.path.join(HERE, "promo-facts.json"),
                          encoding="utf-8"))
OUTDIR = os.environ.get("PROMO_OUT", os.path.join(REPO, "build", "promo"))
OUT = os.path.join(OUTDIR, "WurstB-Plus-v1.5.0-promo.mp4")

BG = (10, 12, 17)
BG2 = (17, 20, 29)
ACCENT = (94, 217, 255)
ACCENT2 = (150, 120, 255)
ACCENT3 = (72, 231, 175)
WARM = (255, 196, 110)
TEXT = (238, 241, 247)
DIM = (140, 150, 168)
FAINT = (88, 97, 114)

F_CJK = r"C:\Windows\Fonts\msyh.ttc"
F_CJK_BD = r"C:\Windows\Fonts\msyhbd.ttc"
F_MONO = r"C:\Windows\Fonts\consola.ttf"

_fonts = {}
SPRITES = {}


def font(size, bold=False, mono=False):
    key = (size, bold, mono)
    if key not in _fonts:
        path = F_MONO if mono else (F_CJK_BD if bold else F_CJK)
        _fonts[key] = ImageFont.truetype(path, size, index=0)
    return _fonts[key]


# ---------------------------------------------------------------- sprites
class Sprite:
    """A cached RGB raster plus its alpha mask, blitted into a float buffer."""

    def __init__(self, rgb, alpha):
        self.rgb = rgb
        self.a = alpha

    def paste(self, canvas, x, y, alpha=1.0):
        if alpha <= 0.004:
            return
        h, w = self.rgb.shape[:2]
        x, y = int(round(x)), int(round(y))
        x0, y0 = max(0, x), max(0, y)
        x1, y1 = min(W, x + w), min(H, y + h)
        if x0 >= x1 or y0 >= y1:
            return
        sx, sy = x0 - x, y0 - y
        src = self.rgb[sy:sy + (y1 - y0), sx:sx + (x1 - x0)]
        a = (self.a[sy:sy + (y1 - y0), sx:sx + (x1 - x0)] * alpha)[..., None]
        dst = canvas[y0:y1, x0:x1]
        np.copyto(dst, dst * (1.0 - a) + src * a)


def _wrap(im, pad):
    arr = np.array(im, dtype=np.uint8)
    return Sprite(arr[..., :3].astype(np.float32),
                  arr[..., 3].astype(np.float32) / 255.0)


def text_sprite(text, size, color=TEXT, bold=False, mono=False, glow=0):
    key = ("t", text, size, color, bold, mono, glow)
    if key in SPRITES:
        return SPRITES[key]
    f = font(size, bold, mono)
    tmp = ImageDraw.Draw(Image.new("RGBA", (8, 8)))
    bbox = tmp.textbbox((0, 0), text, font=f)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    pad = 26 + glow * 3
    im = Image.new("RGBA", (tw + pad * 2, th + pad * 2), (0, 0, 0, 0))
    ImageDraw.Draw(im).text((pad - bbox[0], pad - bbox[1]), text, font=f,
                            fill=tuple(color) + (255,))
    if glow:
        im = Image.alpha_composite(im.filter(ImageFilter.GaussianBlur(glow)), im)
    s = _wrap(im, pad)
    SPRITES[key] = s
    return s


def text_size(text, size, bold=False, mono=False):
    s = text_sprite(text, size, TEXT, bold, mono)
    return s.rgb.shape[1] - 52, s.rgb.shape[0] - 52


def ellipsize(text, size, max_w, bold=False):
    """Trim to fit a card, so a long description can never spill past its box."""
    if text_size(text, size, bold)[0] <= max_w:
        return text, False
    last = (text[:-1], True)
    if text_size(text + "…", size, bold)[0] > max_w:
        for cut in range(len(text) - 1, 0, -1):
            cand = text[:cut] + "…"
            if text_size(cand, size, bold)[0] <= max_w:
                return cand, True
    return last


def card_sprite(w, h, fill=BG2, border=None, border_w=2, radius=18,
                alpha=255, shadow=0):
    key = ("c", w, h, fill, border, border_w, radius, alpha, shadow)
    if key in SPRITES:
        return SPRITES[key]
    pad = 30 if shadow else 4
    im = Image.new("RGBA", (w + pad * 2, h + pad * 2), (0, 0, 0, 0))
    ImageDraw.Draw(im).rounded_rectangle(
        [pad, pad, pad + w, pad + h], radius=radius, fill=tuple(fill) + (alpha,),
        outline=(tuple(border) + (alpha,)) if border else None, width=border_w)
    if shadow:
        im = Image.alpha_composite(im.filter(ImageFilter.GaussianBlur(shadow)), im)
    s = _wrap(im, pad)
    SPRITES[key] = s
    return s


def dot_sprite(radius, color, alpha=255, glow=0):
    key = ("d", radius, color, alpha, glow)
    if key in SPRITES:
        return SPRITES[key]
    pad = 10 + glow * 3
    im = Image.new("RGBA", (radius * 2 + pad * 2, radius * 2 + pad * 2),
                   (0, 0, 0, 0))
    ImageDraw.Draw(im).ellipse([pad, pad, pad + radius * 2, pad + radius * 2],
                               fill=tuple(color) + (alpha,))
    if glow:
        im = Image.alpha_composite(im.filter(ImageFilter.GaussianBlur(glow)), im)
    s = _wrap(im, pad)
    SPRITES[key] = s
    return s


def put(canvas, sprite, x, y, alpha=1.0):
    """Place a sprite by its visual top-left (sprites carry padding)."""
    pad = getattr(sprite, "_pad", None)
    sprite.paste(canvas, x, y, alpha)


# ---------------------------------------------------------------- helpers
def clamp(v, a=0.0, b=1.0):
    return a if v < a else (b if v > b else v)


def ease_out(t):
    return 1 - (1 - clamp(t)) ** 3


def ease_in_out(t):
    t = clamp(t)
    return t * t * (3 - 2 * t)


def lerp(a, b, t):
    return a + (b - a) * t


def reveal(t, i, stagger=0.12, dur=0.5):
    return ease_out((t - i * stagger) / dur)


def centered(canvas, text, y, size, color=TEXT, bold=False, alpha=1.0,
             glow=0, mono=False):
    s = text_sprite(text, size, color, bold, mono, glow)
    pad = 26 + glow * 3
    visual_w = s.rgb.shape[1] - pad * 2
    s.paste(canvas, (W - visual_w) / 2 - pad, y - pad, alpha)
    return visual_w


def centered_at(canvas, s, y, x_center, alpha=1.0, glow=0):
    pad = 26 + glow * 3
    visual_w = s.rgb.shape[1] - pad * 2
    s.paste(canvas, x_center - visual_w / 2 - pad, y - pad, alpha)


def rect(canvas, x, y, w, h, color, alpha, radius=0):
    if w <= 0 or h <= 0 or alpha <= 0.004:
        return
    card_sprite(int(w), int(h), fill=color, border=None, radius=radius).paste(
        canvas, x, y, alpha)


_BG = {}


def background(t):
    # a frame can land exactly on a scene boundary; keep the clock non-negative
    t = max(0.0, t)
    key = int(t * FPS)
    if key in _BG:
        return _BG[key].copy()
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    for y in range(0, H, 8):
        k = y / H
        d.rectangle([0, y, W, y + 8],
                    fill=(int(lerp(BG[0], BG2[0], k)), int(lerp(BG[1], BG2[1], k)),
                          int(lerp(BG[2], BG2[2], k))))
    step = 48
    ox, oy = int((t * 10) % step), int((t * 5) % step)
    for gy in range(-step, H + step, step):
        for gx in range(-step, W + step, step):
            x, y = gx + ox, gy + oy
            if ((x // step) + (y // step)) % 2 == 0:
                d.point((x, y), fill=(31, 36, 47))
    canvas = np.array(img, dtype=np.float32)

    g = dot_sprite(420, ACCENT2, alpha=46, glow=170)
    gx = W * 0.5 + math.sin(t * 0.33) * 250
    gy = H * 0.32 + math.cos(t * 0.25) * 110
    g.paste(canvas, gx - g.rgb.shape[1] / 2, gy - g.rgb.shape[0] / 2, 1.0)
    g2 = dot_sprite(370, ACCENT, alpha=36, glow=160)
    gx2 = W * 0.5 + math.sin(t * 0.33 + 2.4) * 300
    gy2 = H * 0.68 + math.sin(t * 0.21) * 100
    g2.paste(canvas, gx2 - g2.rgb.shape[1] / 2, gy2 - g2.rgb.shape[0] / 2, 1.0)

    _BG[key] = canvas
    if len(_BG) > 90:                      # keep the cache bounded
        for k in sorted(_BG)[:-60]:
            del _BG[k]
    return canvas.copy()


def vignette(canvas):
    if "v" not in SPRITES:
        yy, xx = np.mgrid[0:H, 0:W]
        r = np.sqrt(((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2)
        SPRITES["v"] = np.clip((r - 0.58) / 0.72, 0, 1) ** 1.7
    return canvas * (1.0 - SPRITES["v"] * 0.55)[..., None]


# ---------------------------------------------------------------- scenes
def scene_intro(c, t, dur):
    a = ease_out(t / 0.9)
    s = text_sprite("WurstB", 200, TEXT, bold=True, glow=24)
    pad = 26 + 72
    vw = s.rgb.shape[1] - pad * 2
    plus = text_sprite("+", 200, ACCENT, bold=True, glow=30)
    pvw = plus.rgb.shape[1] - pad * 2
    total = vw + pvw - 18
    x = (W - total) / 2
    dy = (1 - a) * 44
    s.paste(c, x - pad, 248 + dy - pad, a)
    plus.paste(c, x + vw - 18 - pad, 248 + dy - pad, a)

    centered(c, "PLUS", 470, 78, ACCENT2, bold=True, alpha=ease_out((t - .5) / .8))
    centered(c, "Minecraft 全能客户端", 570, 56, TEXT,
             alpha=ease_out((t - 1.0) / .8))
    centered(c, "1.20.2 – 26.2      Forge / NeoForge / Fabric", 656, 40, DIM,
             alpha=ease_out((t - 1.5) / .8))

    a5 = ease_out((t - 2.0) / .7)
    label = "v1.5.0  RELEASE"
    sp = text_sprite(label, 46, BG, bold=True)
    lvw = sp.rgb.shape[1] - 52
    bw, bh = lvw + 110, 96
    bx, by = (W - bw) / 2, 766
    rect(c, bx, by, bw, bh, ACCENT, a5 * 0.92, radius=48)
    centered_at(c, sp, by + (bh - (sp.rgb.shape[0] - 52)) / 2, W / 2, a5)

    centered(c, f"{FACTS['hack_count']} 个 Hack　·　{FACTS['command_count']} 条命令"
                f"　·　{FACTS['hud_count']} 个 HUD 元素",
             918, 40, DIM, alpha=ease_out((t - 2.6) / .8))


def scene_stats(c, t, dur):
    stats = [(FACTS["hack_count"], "Hack", ACCENT),
             (FACTS["command_count"], "命令", ACCENT2),
             (FACTS["hud_count"], "HUD 元素", ACCENT3),
             (len(FACTS["by_category"]), "功能分类", WARM)]
    n = len(stats)
    cw, gap = 400, 40
    x0 = (W - (cw * n + gap * (n - 1))) / 2
    for i, (value, label, col) in enumerate(stats):
        a = reveal(t, i, 0.16, 0.55)
        if a <= 0.01:
            continue
        rise = (1 - ease_out((t - i * 0.16) / 0.6)) * 36
        x, y = x0 + i * (cw + gap), 372 + rise
        card_sprite(cw, 300, fill=BG2, border=col, border_w=2, radius=26,
                    alpha=54, shadow=18).paste(c, x - 30, y - 30, a)
        shown = int(round(value * ease_out(clamp((t - i * .16 - .1) / .9))))
        s = text_sprite(str(shown), 132, col, bold=True, glow=16)
        centered_at(c, s, y + 52, x + cw / 2, a)
        s2 = text_sprite(label, 40, TEXT)
        centered_at(c, s2, y + 214, x + cw / 2, a * 0.95)
    centered(c, "全部内置，开箱即用", 790, 46, DIM, alpha=reveal(t, n, .16, .6))
    centered(c, "Baritone 与运行依赖已随包内嵌，无需额外安装", 868, 34, FAINT,
             alpha=reveal(t, n + 1, .16, .6))


def scene_categories(c, t, dur):
    items = sorted(FACTS["categories"].items(), key=lambda kv: -kv[1])[:6]
    cw, ch, gap, cols = 580, 190, 34, 3
    x0 = (W - (cw * cols + gap * (cols - 1))) / 2
    y0 = 250
    palette = [ACCENT, ACCENT2, ACCENT3, WARM, (255, 130, 150), (150, 200, 255)]
    for i, (cat, count) in enumerate(items):
        a = reveal(t, i, 0.12, 0.5)
        if a <= 0.01:
            continue
        col = palette[i % len(palette)]
        r, cc = divmod(i, cols)
        x = x0 + cc * (cw + gap)
        y = y0 + r * (ch + gap) + (1 - ease_out((t - i * .12) / .55)) * 28
        card_sprite(cw, ch, fill=BG2, border=FAINT, border_w=1, radius=22,
                    alpha=46, shadow=14).paste(c, x - 30, y - 30, a)
        rect(c, x + 26, y + 34, 6, ch - 68, col, a * 0.95, radius=3)
        put(c, text_sprite(FACTS["category_label"].get(cat, cat), 46, TEXT,
                           bold=True), x + 58, y + 30, a)
        cnt = text_sprite(str(count), 68, col, bold=True)
        put(c, cnt, x + cw - (cnt.rgb.shape[1] - 52) - 46, y + 22, a)
        blurb, cut = ellipsize(FACTS["category_blurb"].get(cat, ""), 26,
                               cw - 96 - 26)
        put(c, text_sprite(blurb, 26, DIM), x + 58, y + 100, a * 0.95)
    centered(c, "一九七个功能，分成八大类", 152, 34, FAINT,
             alpha=reveal(t, 0, .12, .6))


def scene_usage(c, t, dur):
    steps = [("01", "打开 GUI", "Right Shift 唤出 ClickGUI，搜索框直接输入功能名"),
             ("02", "启用功能", "点一下开关、绑定按键，配置自动保存"),
             ("03", "叠加组合", "冲突检测自动处理，多个功能不会互相打架"),
             ("04", "自定义 HUD", "拖动摆放，逐元素改颜色、字号与显示内容")]
    x0, y0 = 300, 322
    for i, (num, title, desc) in enumerate(steps):
        a = reveal(t, i, 0.22, 0.55)
        if a <= 0.01:
            continue
        x = x0 + (1 - ease_out((t - i * .22) / .6)) * 64
        y = y0 + i * 118
        put(c, text_sprite(num, 52, ACCENT, bold=True, mono=True), x, y, a)
        put(c, text_sprite(title, 50, TEXT, bold=True), x + 104, y + 2, a)
        put(c, text_sprite(ellipsize(desc, 31, 1120 - 300)[0], 31, DIM),
            x + 380, y + 12, a * 0.95)
        rect(c, x + 104, y + 58, 1120 * ease_out((t - i * .22 - .15) / .8), 2,
             ACCENT2, a * 0.45)
    centered(c, "按下 Right Shift 就开始", 236, 58, TEXT, bold=True,
             alpha=reveal(t, 0, .2, .6))
    centered(c, f"{FACTS['command_count']} 条命令覆盖批量与自动化场景", 860, 34,
             FAINT, alpha=reveal(t, 5, .22, .6))


def scene_automation(c, t, dur):
    picks = [("AutoFarmHack", "全自动种植与收获", ACCENT3),
             ("TreeBotHack", "自动砍树，无人值守", ACCENT),
             ("AutoFishHack", "自动钓鱼并收杆", ACCENT2),
             ("NukerHack", "批量破坏，可调范围", WARM),
             ("AutoLibrarianHack", "自动刷新村民交易", ACCENT2),
             ("NewChunksHack", "新区块探测，找矿找结构", ACCENT),
             ("TemplateToolHack", "选区记录与结构复制", ACCENT3),
             ("SpeedMineHack", "挖掘加速，自动换工具", WARM)]
    cw, ch, gap, cols = 430, 150, 26, 4
    x0 = (W - (cw * cols + gap * (cols - 1))) / 2
    y0 = 402
    for i, (cls, desc, col) in enumerate(picks):
        a = reveal(t, i, 0.11, 0.5)
        if a <= 0.01:
            continue
        r, cc = divmod(i, cols)
        x = x0 + cc * (cw + gap)
        y = y0 + r * (ch + gap) + (1 - ease_out((t - i * .11) / .55)) * 28
        card_sprite(cw, ch, fill=BG2, border=FAINT, border_w=1, radius=20,
                    alpha=48, shadow=12).paste(c, x - 30, y - 30, a)
        name = cls[:-4]
        put(c, text_sprite(ellipsize(name, 40, cw - 96, bold=True)[0], 40, TEXT,
                           bold=True), x + 30, y + 30, a)
        put(c, text_sprite(ellipsize(desc, 26, cw - 84)[0], 26, DIM),
            x + 30, y + 92, a * 0.95)
        dot_sprite(7, col, alpha=235, glow=9).paste(c, x + cw - 52, y + 36, a)
    centered(c, "把重复劳动交给客户端", 236, 60, TEXT, bold=True,
             alpha=reveal(t, 0, .2, .6))
    centered(c, "自动化 · 建造 · 挖矿 · 探测 · 杂项工具", 322, 36, DIM,
             alpha=reveal(t, 2, .14, .6))


MCS = ["1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6", "1.21", "1.21.1",
       "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8",
       "1.21.9", "1.21.10", "1.21.11", "26.1", "26.1.1", "26.1.2", "26.2"]


def scene_versions(c, t, dur):
    loaders = [("Forge", ACCENT), ("NeoForge", ACCENT2), ("Fabric", ACCENT3)]
    cell, gap = 76, 9
    x0 = (W - (len(MCS) * (cell + gap) - gap)) / 2
    y0 = 402
    for r, (lname, lcol) in enumerate(loaders):
        a = reveal(t, r + 1, 0.3, 0.6)
        y = y0 + r * (cell + gap)
        s = text_sprite(lname, 34, lcol, bold=True)
        s.paste(c, x0 - (s.rgb.shape[1] - 52) - 34 - 26, y + 10, a)
        for ci, mc in enumerate(MCS):
            on = not (lname == "Forge" and mc in ("1.20.5", "1.21.2"))
            d = ease_out((t - (ci * 0.035 + r * 0.28)) / 0.45)
            alpha = a * d * (0.95 if on else 0.10)
            card_sprite(cell, cell, fill=(lcol if on else (36, 40, 50)),
                        border=None, radius=14,
                        alpha=205 if on else 130).paste(c, x0 + ci * (cell + gap),
                                                        y, alpha)
    a = reveal(t, 3, 0.05, 0.7)
    for ci, mc in enumerate(MCS):
        s = text_sprite(mc, 21, DIM)
        w = s.rgb.shape[1] - 52
        s.paste(c, x0 + ci * (cell + gap) + (cell - w) / 2 - 26,
                y0 + 3 * (cell + gap) + 10, a * 0.9)
    centered(c, "一套源码，全版本矩阵", 200, 60, TEXT, bold=True,
             alpha=reveal(t, 0, .25, .6))
    centered(c, f"{len(MCS)} 个 Minecraft 版本 × 3 个加载器　=　"
                f"{FACTS['jar_count']} 个开箱可用的 jar",
             288, 38, DIM, alpha=reveal(t, 1, .25, .6))
    centered(c, "Forge 1.20.5 / 1.21.2 官方未发布，这两个版本只有 NeoForge 与 Fabric",
             848, 30, FAINT, alpha=reveal(t, 4, .05, .7))


def scene_waterfall(c, t, dur):
    names = FACTS["hack_names"]
    cols = 9
    per_col = math.ceil(len(names) / cols)
    line_h, x0 = 42, 152
    col_w = (W - x0 * 2) / cols
    span = per_col * line_h + 240
    y_off = -lerp(0, span, t / dur)
    head_a = ease_out(t / .5) * (1 - clamp((t - (dur - 1.4)) / 1.4))
    for ci in range(cols):
        chunk = names[ci * per_col:(ci + 1) * per_col]
        for i, name in enumerate(chunk):
            y = y_off + i * line_h + 200
            if y < 140 or y > H - 40:
                continue
            fade = clamp((H - 40 - y) / 130) * clamp((y - 140) / 130)
            col = [ACCENT, ACCENT2, ACCENT3, (216, 226, 240)][(ci * per_col + i) % 4]
            text_sprite(name, 30, col).paste(c, x0 + ci * col_w - 26, y - 26,
                                             fade * 0.92)
    centered(c, f"全部 {len(names)} 个 Hack", 62, 58, TEXT, bold=True,
             alpha=head_a)


def scene_download(c, t, dur):
    centered(c, "v1.5.0 已发布", 226, 96, TEXT, bold=True,
             alpha=ease_out(t / .7), glow=14)
    centered(c, f"{FACTS['jar_count']} 个 jar 构建完成，打包校验全部通过", 386, 46,
             ACCENT, bold=True, alpha=ease_out((t - .4) / .7))
    rows = [("下载地址", "github.com/xiegeezr886/WurstB-Plus/releases/tag/v1.5.0"),
            ("覆盖版本", f"Minecraft {MCS[0]} – {MCS[-1]}（{len(MCS)} 个版本）"),
            ("支持加载器", "Forge · NeoForge · Fabric　三加载器"),
            ("运行要求", "Java 21 / Java 25（26.x 版本）"),
            ("内置依赖", "Baritone 已随包内嵌，无需单独安装")]
    x0, y0 = 380, 520
    for i, (k, v) in enumerate(rows):
        a = reveal(t, i, 0.16, 0.5)
        if a <= 0.01:
            continue
        y = y0 + i * 78
        put(c, text_sprite(k, 32, FAINT), x0, y, a)
        put(c, text_sprite(v, 34, ACCENT if i == 0 else TEXT), x0 + 250, y - 2, a)
    centered(c, "下载即用 · 无广告 · 源代码公开", 962, 34, DIM,
             alpha=ease_out((t - 1.6) / .8))


SCENES = [("intro", 7.0, scene_intro),
          ("stats", 7.0, scene_stats),
          ("categories", 8.0, scene_categories),
          ("usage", 8.0, scene_usage),
          ("automation", 9.0, scene_automation),
          ("versions", 9.0, scene_versions),
          ("waterfall", 32.0, scene_waterfall),
          ("download", 8.0, scene_download)]
FADE = 0.5


def timeline(t):
    """(scene_index, local_time) for a global time.

    Strictly `<` so that a frame landing exactly on a scene boundary belongs to
    the *next* scene: with `<=` the boundary frame kept local_time == duration,
    which pushed the cross-fade partner to a negative local time and rendered
    the whole frame black.
    """
    acc = 0.0
    for i, (_n, d, _f) in enumerate(SCENES):
        if t < acc + d or i == len(SCENES) - 1:
            return i, t - acc
        acc += d
    return len(SCENES) - 1, 0.0


def render_frames(pipe, limit=None):
    total = sum(d for _, d, _ in SCENES)
    frames = int(round(total * FPS))
    print(f"rendering {frames} frames ({total:.1f}s, {len(SCENES)} scenes)",
          flush=True)
    t0 = time.time()
    black = []
    for n in range(frames):
        tg = n / FPS
        i, lt = timeline(tg)
        name, dur, fn = SCENES[i]
        # Each scene draws into its own buffer. Drawing both scenes into one
        # buffer makes the outgoing scene's own fade compound with the blend,
        # which turned every boundary frame nearly black.
        canvas = background(tg)
        fn(canvas, lt, dur)
        if lt > dur - FADE and i + 1 < len(SCENES):
            k = (lt - (dur - FADE)) / FADE            # 0..1
            nn, nd, nf = SCENES[i + 1]
            nlt = max(0.0, tg - sum(d for _, d, _ in SCENES[:i + 1]))
            nxt = background(tg)
            nf(nxt, nlt, nd)
            blend = ease_in_out(k)
            canvas = canvas * (1.0 - blend) + nxt * blend
        canvas = vignette(canvas)
        img = np.clip(canvas, 0, 255).astype(np.uint8)
        if img.max() < 60:                            # a black frame is a bug
            black.append(n)
            print(f"  WARNING frame {n} is nearly black (t={tg:.2f}s)", flush=True)
        pipe.write(img.tobytes())
        if n % 150 == 0:
            el = time.time() - t0
            print(f"  {n}/{frames} frames  {el:.0f}s  ({n / max(el, .01):.1f} fps)",
                  flush=True)
    print(f"frames rendered in {time.time() - t0:.0f}s  "
          f"({len(black)} nearly-black frames)", flush=True)
    pipe.close()
    return black


def preview(times):
    """Dump individual frames as PNG so the layout can be eyeballed."""
    os.makedirs(os.path.join(OUTDIR, "preview"), exist_ok=True)
    for tg in times:
        i, lt = timeline(tg)
        name, dur, fn = SCENES[i]
        canvas = background(tg)
        fn(canvas, lt, dur)
        canvas = vignette(canvas)
        img = Image.fromarray(np.clip(canvas, 0, 255).astype(np.uint8))
        p = os.path.join(OUTDIR, "preview", f"t{tg:05.1f}_{name}.png")
        img.save(p)
        px = np.array(img)
        print(f"{p}  mean={px.mean():.1f} std={px.std():.1f} "
              f"nonbg={(px.mean(axis=2) > 26).mean() * 100:.1f}%")
    return 0


# ---------------------------------------------------------------- audio
SR = 44100


def note(freq, dur, amp, decay=3.0, harm=(1.0, 0.35, 0.12)):
    n = int(dur * SR)
    t = np.arange(n) / SR
    env = np.exp(-decay * t / dur)
    sig = np.zeros(n, np.float32)
    for k, h in enumerate(harm, start=1):
        sig += (h * np.sin(2 * np.pi * freq * k * t)).astype(np.float32)
    return sig * env * amp


def make_audio(total):
    n = int(total * SR)
    mix = np.zeros(n, np.float32)
    beat = 0.5
    bar = beat * 4
    chords = [(220.00, [220.00, 261.63, 329.63]),
              (174.61, [174.61, 220.00, 261.63]),
              (261.63, [261.63, 329.63, 392.00]),
              (196.00, [196.00, 246.94, 293.66])]
    nbars = int(math.ceil(total / bar)) + 1
    for b in range(nbars):
        root, tones = chords[b % len(chords)]
        at = b * bar
        if at >= total:
            break
        sig = note(root / 2, bar, 0.155, 1.4, harm=(1.0, 0.22, 0.06))
        i = int(at * SR)
        mix[i:i + len(sig)] += sig[:max(0, n - i)]
        for k in range(8):
            f = tones[k % 3] * (2 if k % 4 == 3 else 1)
            sig = note(f, beat * 0.9, 0.082, 4.5)
            i = int((at + k * beat / 2) * SR)
            if i >= n:
                break
            mix[i:i + len(sig)] += sig[:max(0, n - i)]
        for k in (0, 2):
            i = int((at + k * beat) * SR)
            if i >= n:
                continue
            ln = int(0.16 * SR)
            tt = np.arange(ln) / SR
            f = 110 * np.exp(-26 * tt) + 44
            kick = (np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-16 * tt)
                    * 0.5).astype(np.float32)
            mix[i:i + ln] += kick[:max(0, n - i)]
        for k in (1, 3):
            i = int((at + k * beat) * SR)
            if i >= n:
                continue
            ln = int(0.05 * SR)
            rng = np.random.default_rng(b * 10 + k)
            hat = (rng.normal(0, 1, ln).astype(np.float32)
                   * np.exp(-70 * np.arange(ln) / SR) * 0.05)
            mix[i:i + ln] += hat[:max(0, n - i)]

    k = 6
    mix = np.convolve(mix, np.ones(k, np.float32) / k, mode="same").astype(np.float32)
    d = int(0.012 * SR)
    left = mix * 0.62
    right = mix * 0.62 + np.concatenate([np.zeros(d, np.float32), mix[:-d]]) * 0.38
    fi, fo = int(2.5 * SR), int(4.0 * SR)
    ramp_in = np.linspace(0, 1, fi, dtype=np.float32)
    ramp_out = np.linspace(1, 0, fo, dtype=np.float32)
    left[:fi] *= ramp_in
    right[:fi] *= ramp_in
    left[-fo:] *= ramp_out
    right[-fo:] *= ramp_out
    peak = max(np.abs(left).max(), np.abs(right).max())
    if peak > 0.95:
        left *= 0.95 / peak
        right *= 0.95 / peak
    inter = np.empty((len(left), 2), np.int16)
    inter[:, 0] = np.clip(left, -1, 1) * 32000
    inter[:, 1] = np.clip(right, -1, 1) * 32000
    return inter.tobytes()


# ---------------------------------------------------------------- main
def main():
    os.makedirs(OUTDIR, exist_ok=True)
    if "--preview" in sys.argv:
        scenetimes = []
        for nm, d, _f in SCENES:
            base = sum(x[1] for x in SCENES[:[s[0] for s in SCENES].index(nm)])
            scenetimes.append(base + d * 0.62)
        return preview(scenetimes)
    try:
        import imageio_ffmpeg
        ff = imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        ff = shutil.which("ffmpeg")
    if not ff:
        raise SystemExit("ffmpeg not found")
    total = sum(d for _, d, _ in SCENES)
    print(f"ffmpeg : {ff}")
    print(f"output : {OUT}")

    wav_path = os.path.join(OUTDIR, "track.wav")
    with wave.open(wav_path, "wb") as wf:
        wf.setnchannels(2)
        wf.setsampwidth(2)
        wf.setframerate(SR)
        wf.writeframes(make_audio(total))
    print(f"audio  : track.wav ({os.path.getsize(wav_path)/1048576:.1f} MB)")

    cmd = [ff, "-y", "-hide_banner", "-loglevel", "error",
           "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}",
           "-r", str(FPS), "-i", "pipe:0", "-i", wav_path,
           "-map", "0:v", "-map", "1:a",
           "-c:v", "libx264", "-preset", "medium", "-crf", "18",
           "-pix_fmt", "yuv420p", "-movflags", "+faststart",
           "-c:a", "aac", "-b:a", "192k", "-shortest", OUT]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stderr=subprocess.PIPE)
    render_frames(proc.stdin)
    err = proc.stderr.read().decode("utf-8", "replace")
    rc = proc.wait()
    if rc != 0:
        print("ffmpeg failed:\n" + err[-4000:])
        return 1
    print(f"encoded: {OUT} ({os.path.getsize(OUT)/1048576:.1f} MB)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
