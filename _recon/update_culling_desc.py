# -*- coding: utf-8 -*-
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs

n = 0
for p in project_dirs():
    if p.resolve() == REPO.resolve():
        continue
    f = p / 'src/main/resources/assets/wurst/translations/zh_cn.json'
    if not f.exists():
        continue
    t = f.read_text(encoding='utf-8')
    t2 = re.sub(
        r'("description\.wurst\.hack\.entityculling"\s*:\s*")[^"]*(")',
        r'\1按距离、遮挡与隐身状态剔除实体渲染。\2', t)
    if t2 != t:
        f.write_text(t2, encoding='utf-8', newline='\n')
        n += 1
print('updated', n)
