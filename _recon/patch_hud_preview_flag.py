# -*- coding: utf-8 -*-
"""Make uiHidden()-style checks return false while the HUD editor renders a
ported HUD module."""
import re
import sys
from pathlib import Path

GUARD = ('\t\tif(net.wurstclient.hud2.HackHudElement.isPreviewRendering())\n'
         '\t\t\treturn false;\n\n')

roots = sys.argv[1:] or ['fabric/versions/1.21.11']
for root in roots:
    hacked = Path(root) / 'src/main/java/net/wurstclient/hacks'
    if not hacked.is_dir():
        print('skip', root)
        continue
    count = 0
    for src in hacked.glob('*.java'):
        text = src.read_text(encoding='utf-8')
        if 'isPreviewRendering()' in text:
            continue
        pattern = re.compile(
            r'(private boolean uiHidden\(\)\s*\{\s*\n)')
        new, n = pattern.subn(lambda m: m.group(1) + GUARD, text)
        if n:
            src.write_text(new, encoding='utf-8', newline='\n')
            count += n
    print('patched', root, count)
