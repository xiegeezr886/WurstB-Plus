# -*- coding: utf-8 -*-
import re
import sys
sys.path.insert(0, r'C:\Users\ui863\Documents\trae_projects\VAP\_recon')
import add_setting_name_zh as m

out = []
name = 'Only when holding right click'
out.append('translate: ' + repr(m.translate(name)[0]))
out.append('snake: ' + m.snake(name))
p = r'C:\Users\ui863\Documents\trae_projects\VAP\fabric\versions\1.21.11\src\main\resources\assets\wurst\translations\zh_cn.json'
t = open(p, encoding='utf-8').read()
k = 'setting.name.' + m.snake(name)
out.append('occurrences: %d' % t.count('"' + k + '"'))
for line in t.splitlines():
    if '"' + k + '"' in line:
        out.append('line: ' + line)
pat = re.compile(r'("' + re.escape(k) + r'"\s*:\s*")[^"]*(")')
new = pat.sub(lambda mm: mm.group(1) + m.translate(name)[0] + mm.group(2), t)
for line in new.splitlines():
    if '"' + k + '"' in line:
        out.append('new: ' + line)
open(r'C:\Users\ui863\Documents\trae_projects\VAP\_recon\debug_setting.txt',
     'w', encoding='utf-8').write('\n'.join(out))
