# -*- coding: utf-8 -*-
p = r'C:\Users\ui863\Documents\trae_projects\VAP-v1.5.0-1.20.1\README.md'
t = open(p, encoding='utf-8').read()
t = t.replace('1.5.0', '1.5.1')
section = """
---

## 许可

整体作品采用 **AGPL-3.0-or-later**，全文见 `LICENSE.txt`。源自 Wurst 的 GPL-3.0 部分保留在
`LICENSE-GPL-3.0.txt`；Forge MDK 模板部分为 LGPL-2.1，见 `LICENSE-Forge-MDK.txt`。
GPL-3.0 代码按兼容规则并入以 AGPL-3.0 发布的整体作品。v1.5.0 及更早的已发布版本仍按 GPL-3.0 授权。
"""
if '## 许可' not in t:
    t = t.rstrip() + '\n' + section
open(p, 'w', encoding='utf-8', newline='\n').write(t)
print('README updated')
