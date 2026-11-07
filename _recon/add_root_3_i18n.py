# -*- coding: utf-8 -*-
import json
from pathlib import Path

p = Path(r'C:\Users\ui863\Documents\trae_projects\VAP\src\main\resources\assets\wurst\translations\zh_cn.json')
d = json.loads(p.read_text(encoding='utf-8'))
add = {
    'hack.name.bettertab': '更好的 Tab',
    'hack.name.bettertooltips': '更好的提示',
    'hack.name.nostatuseffects': '隐藏状态效果',
    'description.wurst.hack.bettertab': '自定义玩家列表的显示内容。',
    'description.wurst.hack.bettertooltips': '在物品提示中显示耐久、物品 ID 和食物数值。',
    'description.wurst.hack.nostatuseffects': '隐藏 HUD 上的状态效果图标。',
}
add = {k: v for k, v in add.items() if k not in d}
text = p.read_text(encoding='utf-8')
lines = text.splitlines(keepends=True)
anchor = next(i for i, l in enumerate(lines) if '"hack.name.' in l)
if not lines[anchor].rstrip().endswith(','):
    lines[anchor] = lines[anchor].rstrip('\r\n') + ',\n'
lines[anchor + 1:anchor + 1] = [
    '%s: %s,\n' % (json.dumps(k, ensure_ascii=False),
                   json.dumps(v, ensure_ascii=False))
    for k, v in add.items()]
new = ''.join(lines)
json.loads(new)
p.write_text(new, encoding='utf-8', newline='\n')
print('added', len(add))
