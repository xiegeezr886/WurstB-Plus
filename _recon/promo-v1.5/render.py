#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
WurstB+ Plus v1.5 宣传片渲染器。

逐帧用 Pillow 绘制 -> numpy BGR -> ffmpeg 管道编码。
语法参考 PCL++ / BlockHelm 两支宣传片：白底大字陈述、真实 UI 定格、逐项点亮。
"""
import sys, os, math, glob, time, subprocess
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

sys.stdout.reconfigure(encoding='utf-8')

W, H, FPS = 1920, 1080, 30
ROOT = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(ROOT, '..', '..'))
FF = os.path.join(REPO, '_recon', 'python311', 'Lib', 'site-packages',
                  'imageio_ffmpeg', 'binaries', 'ffmpeg-win-x86_64-v7.1.exe')

FONTS = 'C:/Windows/Fonts/'
F_HEI = FONTS + 'msyhbd.ttc'
F_HEI_R = FONTS + 'msyh.ttc'
F_HEI_L = FONTS + 'msyhl.ttc'
F_LAT_B = FONTS + 'segoeuib.ttf'
F_LAT = FONTS + 'segoeui.ttf'
F_MONO_B = FONTS + 'consolab.ttf'
F_MONO = FONTS + 'consola.ttf'

INK = (24, 24, 27)
MUTED = (120, 120, 128)
FAINT = (170, 170, 178)
BLUE = (0, 122, 204)
GREEN = (22, 160, 86)
WHITE = (255, 255, 255)


# ---------------------------------------------------------------- 缓动与工具
def clamp(v, a=0.0, b=1.0):
    return a if v < a else b if v > b else v


def ease_out(t):
    t = clamp(t)
    return 1 - (1 - t) ** 3


def ease_in(t):
    t = clamp(t)
    return t ** 3


def ease_io(t):
    t = clamp(t)
    return 3 * t * t - 2 * t * t * t


def ease_back(t, s=1.7):
    t = clamp(t)
    return 1 + (s + 1) * (t - 1) ** 3 + s * (t - 1) ** 2


def seg(t, start, dur):
    """区间内 0..1 进度。"""
    return clamp((t - start) / dur)


def lerp(a, b, k):
    return a + (b - a) * k


_FONT_CACHE = {}


def font(path, size):
    key = (path, int(size))
    if key not in _FONT_CACHE:
        _FONT_CACHE[key] = ImageFont.truetype(path, int(size))
    return _FONT_CACHE[key]


def text_size(draw, s, f):
    b = draw.textbbox((0, 0), s, font=f)
    return b[2] - b[0], b[3] - b[1], b


def draw_text(img, xy, s, fpath, size, fill, anchor='lt', alpha=1.0, tracking=0):
    """带可选字距的文本绘制；alpha<1 时走临时图层。"""
    if not s:
        return
    f = font(fpath, size)
    if alpha >= 0.999 and tracking == 0:
        ImageDraw.Draw(img).text(xy, s, font=f, fill=fill, anchor=anchor)
        return
    layer = Image.new('RGBA', img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    col = tuple(fill[:3]) + (int(255 * clamp(alpha)),)
    if tracking == 0:
        d.text(xy, s, font=f, fill=col, anchor=anchor)
    else:
        x, y = xy
        for ch in s:
            d.text((x, y), ch, font=f, fill=col, anchor=anchor)
            x += d.textlength(ch, font=f) + tracking
    img.alpha_composite(layer)


class Canvas:
    def __init__(self, bg=(255, 255, 255)):
        self.img = Image.new('RGBA', (W, H), tuple(bg) + (255,))
        self.draw = ImageDraw.Draw(self.img)

    def rect(self, box, fill, radius=0, outline=None, width=2):
        self.draw.rounded_rectangle(box, radius=radius, fill=fill,
                                    outline=outline, width=width)

    def text(self, xy, s, fpath, size, fill, anchor='lt', alpha=1.0, tracking=0):
        draw_text(self.img, xy, s, fpath, size, fill, anchor, alpha, tracking)

    def paste(self, im, xy, alpha=1.0):
        if alpha >= 0.999:
            self.img.alpha_composite(im, (int(xy[0]), int(xy[1])))
        else:
            im = im.copy()
            a = im.split()[3].point(lambda v: int(v * clamp(alpha)))
            im.putalpha(a)
            self.img.alpha_composite(im, (int(xy[0]), int(xy[1])))

    def array(self):
        return np.asarray(self.img.convert('RGB'))[:, :, ::-1].copy()


# ---------------------------------------------------------------- 素材
def load_rgba(path):
    return Image.open(path).convert('RGBA')


def autocrop(im):
    b = im.split()[3].getbbox()
    return im.crop(b) if b else im


def fit_cover(im, w, h):
    """等比放大裁剪（cover）。"""
    k = max(w / im.width, h / im.height)
    nw, nh = int(im.width * k + 0.5), int(im.height * k + 0.5)
    im = im.resize((nw, nh), Image.LANCZOS)
    x = (nw - w) // 2
    y = (nh - h) // 2
    return im.crop((x, y, x + w, y + h))


def cover_window(im, w, h, cx, cy, zoom, pan=(0.0, 0.0)):
    """在 cover 图上以归一化中心 (cx,cy) 取窗，支持慢推镜。"""
    base = fit_cover(im, w, h)
    bw, bh = base.size
    k = 1.0 / max(zoom, 0.05)
    vw, vh = int(bw * k), int(bh * k)
    px = clamp(cx + pan[0], 0.5 * vw / bw, 1 - 0.5 * vw / bw)
    py = clamp(cy + pan[1], 0.5 * vh / bh, 1 - 0.5 * vh / bh)
    x0 = int(px * bw - vw / 2)
    y0 = int(py * bh - vh / 2)
    x0 = max(0, min(bw - vw, x0))
    y0 = max(0, min(bh - vh, y0))
    return base.crop((x0, y0, x0 + vw, y0 + vh)).resize((w, h), Image.LANCZOS)


def rounded_mask(w, h, r):
    m = Image.new('L', (w, h), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, w - 1, h - 1), radius=r, fill=255)
    return m


def window_shot(path, w, h, zoom=1.0, cx=0.5, cy=0.5, radius=18, shadow=True,
                blur_ui=True, trim=None):
    """把整张截图当作一个窗口卡片（圆角、投影）。trim=(l,t,r,b) 先裁掉素材边缘。"""
    im = load_rgba(path).convert('RGB')
    if trim:
        im = im.crop(trim)
    shot = cover_window(im, w, h, cx, cy, zoom)
    shot = shot.convert('RGBA')
    shot.putalpha(rounded_mask(w, h, radius))
    card = Image.new('RGBA', (w + 80, h + 80), (0, 0, 0, 0))
    if shadow:
        sh = Image.new('RGBA', card.size, (0, 0, 0, 0))
        ImageDraw.Draw(sh).rounded_rectangle((40, 44, 40 + w, 44 + h),
                                             radius=radius, fill=(20, 30, 50, 70))
        sh = sh.filter(ImageFilter.GaussianBlur(22))
        card.alpha_composite(sh)
    card.alpha_composite(shot, (40, 40))
    return card


LOGO = autocrop(load_rgba(os.path.join(REPO, 'src/main/resources/assets/wurst/textures/gui/wurstb_logo_white.png')))
# 白色版本 -> 深色版本，用于白底章节
LOGO_INK = Image.new('RGBA', LOGO.size, INK + (255,))
LOGO_INK.putalpha(LOGO.split()[3])

SHOT_TITLE = os.path.join(REPO, 'docs/title-menu-ingame.png')
SHOT_HUD = os.path.join(REPO, 'docs/hud-glass-ingame.png')
SHOT_MENU = os.path.join(REPO, 'docs/background-video-badges.png')
SHOT_VIDEO = os.path.join(REPO, 'docs/background-video-ingame.png')
SHOT_GEAR = os.path.join(REPO, 'docs/title-menu-ingame-gear.png')
# hud 截图左上角烧录了 v1.6.0 水印，按 v1.5 宣传片口径裁掉顶栏
HUD_TRIM = (0, 42, 1296, 672)


# ---------------------------------------------------------------- 排版
def big_line(cv, y, s, size, fill, fpath=F_HEI, alpha=1.0, anchor='mm', x=W / 2):
    cv.text((x, y), s, fpath, size, fill, anchor=anchor, alpha=alpha)


def glow(img, cx, cy, rw, rh, color=(70, 140, 255), strength=0.30):
    """在指定位置叠一层椭圆径向光晕（PCL++ 片里 UI 后面的那种蓝白辉光）。"""
    rw = int(rw)
    rh = int(rh)
    if rw < 4 or rh < 4:
        return
    small = 96
    yy, xx = np.mgrid[0:small, 0:small]
    d = np.sqrt(((xx - small / 2) / (small / 2)) ** 2 +
                ((yy - small / 2) / (small / 2)) ** 2)
    a = np.clip(1 - d, 0, 1) ** 2 * strength * 255
    layer = Image.new('RGBA', (small, small), tuple(color) + (0,))
    layer.putalpha(Image.fromarray(a.astype(np.uint8)))
    layer = layer.resize((rw, rh), Image.BICUBIC)
    img.alpha_composite(layer, (int(cx - rw / 2), int(cy - rh / 2)))


def segments(cv, cy, parts, size, fpath=F_HEI, alpha=1.0, tracking=0):
    """居中绘制多段彩色标题。parts = [(文本, 颜色), ...]"""
    f = font(fpath, size)
    widths = [cv.draw.textlength(s, font=f) + tracking * len(s) for s, _ in parts]
    x = W / 2 - sum(widths) / 2
    for (s, col), w in zip(parts, widths):
        cv.text((x, cy), s, fpath, size, col, anchor='lm', alpha=alpha, tracking=tracking)
        x += w


def chrome(cv, t, chapter=None):
    """左上角小标 + 右上角版本胶囊。"""
    cv.text((88, 64), 'WURSTB+ PLUS', F_LAT_B, 20, FAINT, tracking=4, alpha=0.9)
    if chapter:
        cv.text((88, 92), chapter, F_HEI_R, 18, FAINT, alpha=0.9)


def tag_pill(cv, right=W - 88, y=60, label='v1.5.0', dark=False):
    f = font(F_LAT_B, 20)
    tw = cv.draw.textlength(label, font=f)
    pad = 18
    box = (right - tw - pad * 2, y, right, y + 40)
    cv.rect(box, (0, 0, 0, 0), radius=20,
            outline=(210, 210, 218) if not dark else (70, 70, 78), width=2)
    cv.text(((box[0] + box[2]) / 2, y + 21), label, F_LAT_B, 20,
            MUTED if not dark else (150, 150, 158), anchor='mm')


def chapter_card(n, title, sub, prog):
    """B 片那种章节卡：白底、居中小字。"""
    cv = Canvas((250, 250, 252))
    k = ease_out(prog)
    cv.text((W / 2, H / 2 - 40), f'0{n}', F_MONO_B, 30, FAINT, anchor='mm', alpha=k)
    cv.text((W / 2, H / 2 + 10), title, F_HEI, 96, INK, anchor='mm', alpha=k)
    cv.text((W / 2, H / 2 + 90), sub, F_HEI_R, 30, MUTED, anchor='mm', alpha=k * 0.95)
    return cv


# ================================================================ 镜头
def s_cold_open(t, dur):
    """0.0-9.0s  A 片式大字陈述。"""
    cv = Canvas((255, 255, 255))
    # 三行事实依次落位，再归结为一个结论
    rows = [('WurstB+ Plus 1.5', 100, 0.35),
            ('23 个版本 · 3 种加载器 · 67 个工程', 76, 1.15)]
    for i, (s, size, st) in enumerate(rows):
        k = ease_out(seg(t, st, 0.55))
        if k <= 0:
            continue
        dy = (1 - k) * 26
        col = INK if i == 0 else (150, 150, 158)
        cv.text((W / 2, 330 + i * 122 + dy), s, F_HEI, size, col,
                anchor='mm', alpha=k)
    # 把“手工维护”整句划掉
    k2 = ease_out(seg(t, 2.5, 0.6))
    if k2 > 0:
        s = '手工维护呢？'
        f = font(F_HEI, 76)
        tw = cv.draw.textlength(s, font=f)
        cv.text((W / 2, 610), s, F_HEI, 76, (150, 150, 158), anchor='mm',
                alpha=k2 * 0.9)
        x0, x1 = W / 2 - tw / 2, W / 2 + tw / 2
        wipe = ease_io(seg(t, 2.75, 0.5))
        if wipe > 0:
            cv.draw.line((x0, 614, lerp(x0, x1, wipe), 614), fill=INK, width=7)
    # 结论
    k3 = ease_out(seg(t, 3.9, 0.55))
    if k3 > 0:
        glow(cv.img, W / 2, 770, 900, 300, color=(60, 130, 255), strength=0.16 * k3)
        cv.text((W / 2, 762), '不用了。', F_HEI, 128, INK, anchor='mm', alpha=k3)
    out = 1 - ease_in(seg(t, dur - 0.4, 0.4))
    if out < 1:
        cv.img.putalpha(int(255 * out))
    return cv


def s_title_ui(t, dur):
    """9.0-17.0s  B 片式：真实标题界面推镜。"""
    cv = Canvas((255, 255, 255))
    k = ease_out(seg(t, 0, 0.6))
    zoom = 1.04 - 0.06 * ease_io(seg(t, 0, dur))
    card = window_shot(SHOT_TITLE, 1180, 662, zoom=zoom, cx=0.5, cy=0.52)
    x = W / 2 - card.width / 2
    y = 110 + (1 - k) * 40
    cv.paste(card, (x, y), alpha=k)
    # 主张
    k2 = ease_out(seg(t, 0.9, 0.5))
    cv.text((W / 2, 900), '进来先看到它。', F_HEI, 64, INK, anchor='mm', alpha=k2)
    k3 = ease_out(seg(t, 1.5, 0.5))
    cv.text((W / 2, 976), '背景可以是静图、动图、视频，或者 Wallpaper Engine 场景。',
            F_HEI_R, 28, MUTED, anchor='mm', alpha=k3)
    chrome(cv, t, '标题界面')
    return cv


def s_footage(t, dur):
    """17.0-24.0s  实录 HUD：玻璃卡片小地图、按键、状态。"""
    cv = Canvas((255, 255, 255))
    k = ease_out(seg(t, 0, 0.5))
    zoom = 1.02 + 0.05 * ease_io(seg(t, 0, dur))
    card = window_shot(SHOT_HUD, 1100, 620, zoom=zoom, cx=0.52, cy=0.5)
    cv.paste(card, (W - card.width - 60, H / 2 - card.height / 2 + 30 + (1 - k) * 30), alpha=k)
    # 侧边标注（B 片的“功能点 + 说明”列）
    items = [('玻璃质感', 'HUD 卡片统一圆角、描边与主题色条'),
             ('实时读数', 'FPS · TPS · Ping · 坐标 · 速度 · 时钟'),
             ('小地图', '圆形地形、区块网格、玩家与实体')]
    for i, (h1, h2) in enumerate(items):
        st = 0.8 + i * 0.45
        kk = ease_out(seg(t, st, 0.45))
        yy = 340 + i * 150
        cv.text((110, yy), h1, F_HEI, 46, INK, alpha=kk)
        cv.text((110, yy + 62), h2, F_HEI_R, 24, MUTED, alpha=kk)
    chrome(cv, t, '游戏内 HUD')
    return cv


def s_pipeline(t, dur):
    """24.0-31.0s  一条源码 -> 三个加载器 -> 67 个产物。静态流程图，无渲染。"""
    cv = Canvas((255, 255, 255))
    k = ease_out(seg(t, 0.1, 0.5))
    cv.text((W / 2, 148), '一套源码，走到每个版本。', F_HEI, 72, INK, anchor='mm', alpha=k)
    cv.text((W / 2, 250), '改一次，67 个工程一起改。', F_HEI_R, 28, MUTED, anchor='mm', alpha=k)

    # 左：源码
    k1 = ease_out(seg(t, 0.5, 0.45))
    src = ['net.wurstclient', '  hacks/  197', '  hud/    glass', '  gui/    title',
           '  mixin/  67 份', '  ...']
    cv.rect((110, 340, 470, 720), (246, 246, 249), radius=24, outline=(228, 228, 233), width=2)
    cv.text((140, 380), 'src/main/java', F_MONO_B, 22, MUTED, alpha=k1)
    for i, s in enumerate(src):
        cv.text((140, 432 + i * 44), s, F_MONO, 24, INK, alpha=k1)

    # 中：加载器
    k2 = ease_out(seg(t, 1.0, 0.45))
    loaders = [('Forge', '1.20.1 – 26.3'), ('NeoForge', '1.20.1 – 26.3'),
               ('Fabric', '1.20.1 – 26.3')]
    for i, (name, rng) in enumerate(loaders):
        yy = 350 + i * 130
        cv.rect((590, yy, 930, yy + 106), (255, 255, 255), radius=22,
                outline=(214, 214, 222), width=3)
        cv.text((760, yy + 38), name, F_LAT_B, 34, INK, anchor='mm', alpha=k2)
        cv.text((760, yy + 76), rng, F_HEI_R, 22, MUTED, anchor='mm', alpha=k2)
    # 连线：源码 -> 主线 -> 三个加载器 -> 产物
    cv.draw.line((480, 530, 535, 530), fill=(196, 196, 205), width=4)
    cv.draw.line((535, 403, 535, 663), fill=(196, 196, 205), width=4)
    for i in range(3):
        yy = 403 + i * 130
        cv.draw.line((535, yy, 590, yy), fill=(196, 196, 205), width=4)

    # 右：产物
    k3 = ease_out(seg(t, 1.5, 0.5))
    cv.rect((1040, 340, 1810, 720), (246, 246, 249), radius=24, outline=(228, 228, 233), width=2)
    cv.text((1070, 380), 'WurstB+.Plus-1.5.0-<Loader>-<mc>.jar', F_MONO_B, 22, MUTED, alpha=k3)
    cols = [('Forge', ['1.20.1', '1.20.2', '1.21', '1.21.5', '1.21.11', '26.3']),
            ('NeoForge', ['1.20.1', '1.20.5', '1.21', '1.21.4', '26.1', '26.3']),
            ('Fabric', ['1.20.1', '1.20.5', '1.21.3', '1.21.11', '26.1.2', '26.3'])]
    for c, (tag, vers) in enumerate(cols):
        x = 1070 + c * 250
        cv.text((x, 440), tag, F_LAT_B, 24, INK, alpha=k3)
        for r, v in enumerate(vers):
            cv.text((x, 486 + r * 36), v, F_MONO, 22, MUTED, alpha=k3)
    for i in range(3):
        cv.draw.line((935, 403 + i * 130, 1035, 403 + i * 130), fill=(196, 196, 205), width=4)

    k4 = ease_out(seg(t, 2.3, 0.5))
    cv.text((W / 2, 818), '67 个资产，同一个 v1.5.0。', F_HEI, 46, INK, anchor='mm', alpha=k4)
    cv.text((W / 2, 880), '不用等移植，不用挑版本。', F_HEI_R, 26, MUTED, anchor='mm', alpha=k4)

    for i, lab in enumerate(['源码', '加载器', '产物']):
        kk = ease_out(seg(t, 0.3 + i * 0.3, 0.4))
        x = [290, 760, 1425][i]
        cv.text((x, 768), lab, F_HEI_R, 22, FAINT, anchor='mm', alpha=kk)

    chrome(cv, t, '版本矩阵')
    return cv


def s_ui_walk(t, dur):
    """31.0-39.0s  B 片式：连续 UI 钉桩 —— 标题菜单 / 坏图 / 齿轮。"""
    cv = Canvas((255, 255, 255))
    shots = [(SHOT_MENU, '背景选择', '静图 · 动图 · 视频 · WE 场景', 0.5, 0.5),
             (SHOT_VIDEO, '视频背景', '解码、帧时钟、自适应尺寸', 0.5, 0.5),
             (SHOT_GEAR, '标题菜单', '玻璃控件与原生控件统一', 0.76, 0.20)]
    active = min(2, int(t / (dur / 3)))
    # 顶部缩略条
    for i, (path, _, _, cx, cy) in enumerate(shots):
        k = ease_out(seg(t, 0.1 + i * 0.12, 0.4))
        card = window_shot(path, 210, 118, zoom=1.1, radius=10, shadow=False,
                           cx=cx, cy=cy)
        card = card.resize((int(card.width * 0.82), int(card.height * 0.82)), Image.LANCZOS)
        x = W / 2 - 300 + i * 220
        alpha = 1.0 if i == active else 0.35
        if i == active:
            cv.rect((x + 5, 118, x + card.width - 5, 258), None, radius=12,
                    outline=INK, width=3)
        cv.paste(card, (x, 100), alpha=k * alpha)
    # 主图
    path, h1, h2, cx, cy = shots[active]
    local = t - active * (dur / 3)
    kk = ease_out(seg(local, 0, 0.45))
    card = window_shot(path, 1180, 620, zoom=1.03, cx=cx, cy=cy)
    cv.paste(card, (W / 2 - card.width / 2, 300 + (1 - kk) * 30), alpha=kk)
    cv.text((W / 2, 982), h1, F_HEI, 50, INK, anchor='mm', alpha=kk)
    cv.text((W / 2, 1034), h2, F_HEI_R, 24, MUTED, anchor='mm', alpha=kk)
    chrome(cv, t, '外观')
    return cv


def s_hacks(t, dur):
    """40.0-48.0s  B 片式：功能点名，逐项点亮。"""
    cv = Canvas((255, 255, 255))
    k = ease_out(seg(t, 0, 0.5))
    cv.text((W / 2, 160), '八类功能，197 个条目。', F_HEI, 72, INK, anchor='mm', alpha=k)
    cv.text((W / 2, 262), '你能想到的，和想不到的，都在里面。', F_HEI_R, 30, MUTED,
            anchor='mm', alpha=k)
    cats = [('COMBAT', '战斗', 33), ('RENDER', '渲染', 48), ('MOVEMENT', '移动', 41),
            ('BLOCKS', '方块', 30), ('ITEMS', '物品', 10), ('OTHER', '其他', 16),
            ('FUN', '趣味', 12), ('CHAT', '聊天', 7)]
    for i, (en, zh, n) in enumerate(cats):
        st = 0.7 + i * 0.18
        kk = ease_out(seg(t, st, 0.4))
        col_i = i % 4
        row = i // 4
        x = 390 + col_i * 380
        y = 430 + row * 220
        cv.rect((x - 150, y - 62, x + 150, y + 62), (246, 246, 249), radius=20,
                outline=(228, 228, 233), width=2)
        cv.text((x, y - 16), en, F_LAT_B, 30, INK, anchor='mm', alpha=kk)
        cv.text((x, y + 28), f'{zh} · {n}', F_HEI_R, 24, MUTED, anchor='mm', alpha=kk)
    tail = ease_out(seg(t, 2.8, 0.5))
    cv.text((W / 2, 900), 'Killaura · MultiAura · Scaffold · X-Ray · AutoTotem · Baritone …',
            F_MONO_B, 30, MUTED, anchor='mm', alpha=tail)
    chrome(cv, t, '功能')
    return cv


def s_play(t, dur):
    """48.0-54.0s  实机游玩镜头：扫过两条缩略录像（无素材时用 UI 镜头替代）。"""
    cv = Canvas((255, 255, 255))
    k = ease_out(seg(t, 0, 0.5))
    cv.text((W / 2, 150), '然后，进游戏。', F_HEI, 84, INK, anchor='mm', alpha=k)
    shots = [SHOT_TITLE, SHOT_HUD]
    for i, path in enumerate(shots):
        st = 0.3 + i * 0.35
        kk = ease_out(seg(t, st, 0.55))
        w, h = 790, 444
        zoom = 1.08 - 0.06 * ease_io(seg(t, 0, dur))
        card = window_shot(path, w, h, zoom=zoom, cx=0.5, cy=0.5, radius=14)
        x = W / 2 - 820 + i * 850
        y = 330 + (1 - kk) * 50
        cv.paste(card, (x - 40, y - 40), alpha=kk)
    cap = ease_out(seg(t, 1.4, 0.5))
    cv.text((W / 2, 900), 'Fabric · Forge · NeoForge ｜ 1.20.1 → 26.3', F_HEI_R, 30,
            MUTED, anchor='mm', alpha=cap)
    chrome(cv, t, '实机')
    return cv


def s_outro(t, dur):
    """54.0-60.0s  收尾：logo + 下载信息。"""
    cv = Canvas((255, 255, 255))
    k = ease_out(seg(t, 0.1, 0.6))
    logo = LOGO_INK
    scale = 0.62 * (0.96 + 0.04 * ease_out(seg(t, 0, dur)))
    lw, lh = int(logo.width * scale), int(logo.height * scale)
    lg = logo.resize((lw, lh), Image.LANCZOS)
    cv.paste(lg, (W / 2 - lw / 2, 300 + (1 - k) * 20), alpha=k)
    # B+ 角标
    k2 = ease_out(seg(t, 0.55, 0.45))
    bx, by = W / 2 + lw / 2 - 30, 268
    cv.rect((bx - 64, by - 34, bx + 64, by + 34), INK, radius=34)
    cv.text((bx, by + 1), 'B+', F_LAT_B, 32, WHITE, anchor='mm', alpha=k2)
    k3 = ease_out(seg(t, 0.9, 0.5))
    cv.text((W / 2, 640), 'WurstB+ Plus v1.5.0', F_HEI, 54, INK, anchor='mm', alpha=k3)
    k4 = ease_out(seg(t, 1.3, 0.5))
    cv.text((W / 2, 716), '23 个版本 · 3 种加载器 · 67 个工程 · GPL-3.0', F_HEI_R, 28,
            MUTED, anchor='mm', alpha=k4)
    k5 = ease_out(seg(t, 1.8, 0.6))
    cv.rect((W / 2 - 330, 790, W / 2 + 330, 856), (246, 246, 249), radius=33,
            outline=(226, 226, 232), width=2)
    cv.text((W / 2, 824), 'github.com/xiegeezr886/WurstB-Plus', F_MONO_B, 28, INK,
            anchor='mm', alpha=k5)
    k6 = ease_out(seg(t, 2.4, 0.5))
    cv.text((W / 2, 940), 'Minecraft 是 Mojang Studios 的商标；本客户端与 Mojang / Microsoft 无关。',
            F_HEI_R, 20, FAINT, anchor='mm', alpha=k6)
    return cv


# ---------------------------------------------------------------- 时间线
# (开始秒, 时长, 函数)
SCENES = [
    (0.0, 9.0, s_cold_open),
    (9.0, 8.0, s_title_ui),
    (17.0, 7.0, s_footage),
    (24.0, 7.0, s_pipeline),
    (31.0, 8.0, s_ui_walk),
    (40.0, 8.0, s_hacks),
    (48.0, 6.0, s_play),
    (54.0, 6.0, s_outro),
]
TOTAL = SCENES[-1][0] + SCENES[-1][1]


def render_frame(t):
    # 场景切换处做很短的交叉淡化（叠化），避免硬切像 PPT
    for i, (start, dur, fn) in enumerate(SCENES):
        if start <= t < start + dur:
            cur = fn(t - start, dur)
            if i > 0 and t - start < 0.12:
                prev_start, prev_dur, prev_fn = SCENES[i - 1]
                prev = prev_fn(prev_dur - 0.001, prev_dur)
                k = ease_io((t - start) / 0.12)
                prev.img.putalpha(255)
                cur.img.putalpha(255)
                c = Canvas()
                c.img = Image.blend(prev.img, cur.img, k)
                c.draw = ImageDraw.Draw(c.img)
                return c
            return cur
    return Canvas()


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'out', 'wurstb-promo-v1.5.mp4')
    every = int(sys.argv[2]) if len(sys.argv) > 2 else 1   # 抽帧试渲染
    only = None
    if len(sys.argv) > 3:
        only = [int(x) for x in sys.argv[3].split(',')]
    if os.path.dirname(out):
        os.makedirs(os.path.dirname(out), exist_ok=True)
    os.makedirs(os.path.join(ROOT, 'out'), exist_ok=True)
    if only:
        for i in only:
            t = i / FPS
            render_frame(t).img.convert('RGB').save(os.path.join(ROOT, 'out', f'still_{i:05d}.png'))
        print('stills done')
        return
    n = int(TOTAL * FPS)
    preview = every > 1
    pw, ph = (480, 270) if preview else (W, H)
    cmd = [FF, '-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'bgr24',
           '-s', f'{W}x{H}', '-r', str(FPS / every), '-i', '-',
           '-vf', f'scale={pw}:{ph}:flags=lanczos', '-c:v', 'libx264',
           '-preset', 'veryfast' if preview else 'slow',
           '-crf', '28' if preview else '17', '-pix_fmt', 'yuv420p',
           '-movflags', '+faststart', out]
    p = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    t0 = time.time()
    for i in range(0, n, every):
        cv = render_frame(i / FPS)
        p.stdin.write(cv.array().tobytes())
        if i % 60 == 0:
            el = time.time() - t0
            done = i / every + 1
            total = len(range(0, n, every))
            eta = el / done * (total - done)
            print(f'{i:5d}/{n}  {100 * done / total:5.1f}%  {el:6.1f}s  eta {eta:6.1f}s', flush=True)
    p.stdin.close()
    p.wait()
    print('done', out, f'{time.time() - t0:.1f}s')


if __name__ == '__main__':
    main()
