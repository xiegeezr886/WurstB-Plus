# -*- coding: utf-8 -*-
from pathlib import Path

p = Path(__file__).with_name('add_setting_name_zh.py')
s = p.read_text(encoding='utf-8')
extra = {
    'Affect': '影响', 'Alpha': '透明度', 'Contrast': '对比度',
    'Density': '密度', 'Gravity': '重力', 'Hotbar': '快捷栏',
    'Hue': '色相', 'Saturation': '饱和度', 'Sharpness': '锋利',
    'Width': '宽度', 'Size': '大小', 'Stained': '染色',
    'Pushing': '推动', 'Spin speed': '旋转速度', 'Max': '最大数量',
    'URL': '网址', 'Show protocol': '显示协议', 'Show version': '显示版本',
    'Open on enable': '启用时打开', 'Affect self': '影响自己',
    'Only sprinting': '仅冲刺时', 'Block hit': '方块命中',
    'Hide attack indicator': '隐藏攻击指示器',
    'Old damage tilt': '旧版伤害倾斜', 'Always sharpness': '始终锋利粒子',
    'Always criticals': '始终暴击粒子', 'Sharpness amount': '锋利粒子数量',
    'Criticals amount': '暴击粒子数量', 'X scale': 'X 缩放',
    'Y scale': 'Y 缩放', 'Z scale': 'Z 缩放', 'Normal glass': '普通玻璃',
    'Stained glass': '染色玻璃', 'Hotbar overlay': '快捷栏覆盖',
    'Show players': '显示玩家', 'Show mobs': '显示生物',
    'Show other': '显示其他', 'Master': '主音量', 'Music': '音乐',
    'Records': '唱片', 'Weather': '天气', 'Blocks': '方块',
    'Hostile mobs': '敌对生物', 'Neutral mobs': '中立生物',
    'Ambient': '环境', 'Voice': '语音', 'Duration': '持续时间',
    'Highlight': '高亮', 'Through walls': '穿墙', 'Mode': '模式',
    'Amount': '强度', 'Range': '范围', 'Height': '高度', 'Scale': '缩放',
    'Background': '背景', 'Speed': '速度', 'Delay': '延迟',
    'Strength': '强度', 'Show name': '显示名称', 'Hide pumpkin': '隐藏南瓜',
    'Hide fire': '隐藏火焰', 'X': 'X', 'Y': 'Y', 'Z': 'Z',
}
lines = ''.join('    %r: %r,\n' % (k, v) for k, v in extra.items())
block = '\n}\n\nEXACT.update({\n' + lines + '})\n\nWORDS = {'
if 'EXACT.update({' not in s:
    s = s.replace('\n}\n\nWORDS = {', block, 1)
    p.write_text(s, encoding='utf-8', newline='\n')
    print('inserted')
else:
    print('already inserted')
