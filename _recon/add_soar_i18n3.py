# -*- coding: utf-8 -*-
"""zh_cn names/descriptions for the third Soar batch."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs

NAMES = {
    'fpsboost': ('FPS 优化', '启用更省性能的画面设置。'),
    'minimalviewbobbing': ('最小视角晃动', '尽量关闭视角晃动。'),
    'clientspoofer': ('客户端伪装', '伪装发送给服务器的客户端品牌。'),
    'hypixel': ('Hypixel 工具', 'Hypixel 快捷命令（大厅/回城/重连）。'),
    'minemen': ('Minemen 工具', 'Minemen Club 快捷命令（回出生点/补弹）。'),
    'packdisplay': ('资源包显示', 'HUD 显示当前资源包。'),
    'mechvibes': ('机械键盘音', '按键和鼠标点击时播放机械键盘音效。'),
    'killeffects': ('击杀特效', '击杀目标时生成粒子特效。'),
    'killsounds': ('击杀音效', '击杀目标时播放音效。'),
    'shinypots': ('闪光药水', '为掉落的药水物品添加闪光效果。'),
    'clickeffect': ('点击特效', '点击方块或实体时生成粒子。'),
    'jumpcircle': ('跳跃圆环', '起跳时在脚下显示扩散圆环。'),
    'projectiletrail': ('投掷物拖尾', '为箭矢和投掷物绘制拖尾。'),
    'reachcircles': ('触及圆环', '显示攻击与交互距离圆环。'),
    'crosshair': ('自定义准星', '自定义准星的大小、间距和颜色。'),
}


def add(file, entries):
    text = file.read_text(encoding='utf-8')
    missing = [(k, v) for k, v in entries if '"%s"' % k not in text]
    if not missing:
        return 0
    lines = text.splitlines(keepends=True)
    anchor = next((i for i, l in enumerate(lines)
                   if '"description.wurst.hack.' in l), None)
    if anchor is None:
        return -1
    if not lines[anchor].rstrip().endswith(','):
        lines[anchor] = lines[anchor].rstrip('\r\n') + ',\n'
    lines[anchor + 1:anchor + 1] = [
        '%s: %s,\n' % (json.dumps(k, ensure_ascii=False),
                       json.dumps(v, ensure_ascii=False))
        for k, v in missing]
    new = ''.join(lines)
    json.loads(new)
    file.write_text(new, encoding='utf-8', newline='\n')
    return len(missing)


def main():
    root = REPO.resolve()
    stats = {}
    for p in project_dirs():
        if p.resolve() == root:
            continue
        zh = p / 'src/main/resources/assets/wurst/translations/zh_cn.json'
        if not zh.exists():
            continue
        entries = []
        for k, (name, desc) in NAMES.items():
            entries.append(('hack.name.' + k, name))
            entries.append(('description.wurst.hack.' + k, desc))
        n = add(zh, entries)
        stats[n] = stats.get(n, 0) + 1
    print('soar3 i18n', stats)


if __name__ == '__main__':
    main()
