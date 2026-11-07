# -*- coding: utf-8 -*-
"""zh_cn names/descriptions for the remaining 29 Soar modules."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs

NAMES = {
    'autogg': ('自动 GG', '在检测到胜利消息后发送配置的聊天文本。'),
    'forcemainhand': ('强制主手', '强制其他玩家以左手或右手作为主手渲染。'),
    'jumpresetindicator': ('跳跃重置指示', '显示受击后跳跃的时机是完美、过早还是过晚。'),
    'pitchdisplay': ('俯仰角显示', 'HUD 显示当前俯仰角。'),
    'snaptap': ('快速转向', '同时按住相反方向键时让最后按下的方向优先。'),
    'yawdisplay': ('偏航角显示', 'HUD 显示当前偏航角。'),
    'entityculling': ('实体剔除', '按距离、遮挡与隐身状态剔除实体渲染。'),
    'chunkanimator': ('区块动画', '高亮新加载的区块并启用原版区块淡入。'),
    'clearglass': ('清透玻璃', '隐藏玻璃面，方便观察玻璃后方。'),
    'clearwater': ('清透水', '移除水下覆盖层和水下迷雾。'),
    'colorsaturation': ('色彩调整', '屏幕色彩、亮度与对比度滤镜。'),
    'customhelditems': ('手持物品调整', '调整第一人称手持物品的位置与缩放。'),
    'damagetilt': ('伤害倾斜', '按击退方向修正受伤倾斜。'),
    'damagetint': ('受伤红晕', '低生命值时显示红色屏幕提示。'),
    'fpsspoofer': ('FPS 伪装', '在 F3 等界面显示乘以倍率的帧率。'),
    'glintcolor': ('附魔光效颜色', '调整附魔光效速度与强度，并在快捷栏显示彩色提示。'),
    'hitcolor': ('受击颜色', '命中实体时显示自定义颜色闪光。'),
    'hitdelayfix': ('攻击延迟修复', '清除未命中后的攻击延迟。'),
    'itemphysics': ('物品物理', '让掉落物在客户端旋转。'),
    'items2d': ('平面物品', '让掉落物朝向玩家，近似平面渲染。'),
    'minimaldamageshake': ('最小受伤抖动', '调整受伤时的镜头抖动强度。'),
    'mobends': ('弯曲动画', '增强玩家行走时的手臂与腿部摆动。'),
    'motionblur': ('动态模糊', '根据镜头移动速度绘制边缘拖影。'),
    'oldanimations': ('旧版动画', '1.8 风格的受击粒子、攻击指示器等选项。'),
    'overlayeditor': ('覆盖层编辑', '可隐藏南瓜和火焰覆盖层。'),
    'particlecustomizer': ('粒子定制', '为攻击添加额外暴击与锋利粒子。'),
    'rawinput': ('原始输入', '启用系统原始鼠标输入。'),
    'rearview': ('后视镜', 'HUD 显示身后的实体雷达。'),
    'skin3d': ('3D 皮肤', 'HUD 显示旋转的皮肤头部预览。'),
    'skinprotect': ('皮肤保护', '将本地玩家皮肤替换为默认皮肤。'),
    'slowswing': ('慢速挥手', '调整挥手动画时长。'),
    'soundmodifier': ('声音调整', '按原版声音分类调整音量。'),
    'soundsubtitles': ('声音字幕', 'HUD 显示声音字幕与左右方向。'),
    'viaversion': ('ViaVersion 信息', '显示服务器版本与协议，不包含协议转换后端。'),
    'waveycapes': ('波浪披风', '在移动玩家身后生成波浪披风粒子。'),
    'webbrowser': ('网页浏览器', '在系统浏览器中打开网址并显示 HUD 面板。'),
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
    print('soar7 i18n', stats)


if __name__ == '__main__':
    main()
