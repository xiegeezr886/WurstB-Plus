# -*- coding: utf-8 -*-
"""1.21.9+ entry API fixes."""
import sys
from pathlib import Path


def patch(project):
    p = (Path(project) / 'src/main/java/net/wurstclient/clickgui2/screens'
         / 'SelectFileScreen.java')
    t = p.read_text(encoding='utf-8')
    anchor = ('\t\tpublic void extractContent(GuiGraphicsExtractor context, '
              'int mouseX,\n\t\t\tint mouseY, boolean hovered, '
              'float tickDelta)\n\t\t{\n')
    if anchor in t and 'getContentX()' not in t:
        t = t.replace(
            anchor, anchor + '\t\t\tint x = getContentX();\n'
            '\t\t\tint y = getContentY();\n', 1)
        p.write_text(t, encoding='utf-8', newline='\n')
        print('selectfile', project)
    else:
        print('skip', project)


if __name__ == '__main__':
    for arg in sys.argv[1:]:
        patch(arg)
