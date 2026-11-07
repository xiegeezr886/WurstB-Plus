# -*- coding: utf-8 -*-
"""1.20.2-1.20.4 API fixes for the 1.5.1 clickgui screens/mixin.

- Screen.renderBackground(GuiGraphics) became renderBackground(G, int, int, float)
- EditBox.tick() was removed
- AbstractSelectionList.setRenderTopAndBottom() was removed
"""
import re
import sys
from pathlib import Path


def fix_screen(path):
    p = Path(path)
    t = p.read_text(encoding='utf-8')
    t = t.replace('renderBackground(context);',
                  'renderBackground(context, mouseX, mouseY, partialTicks);')
    t = re.sub(r'(?m)^\s*\w+\.tick\(\);\r?\n', '', t)
    p.write_text(t, encoding='utf-8', newline='\n')
    print('screen', path)


def fix_mixin(path):
    p = Path(path)
    t = p.read_text(encoding='utf-8')
    t = re.sub(
        r'(?m)^\s*// The vanilla top/bottom fade overlays read as dirt bands '
        r'on top of\r?\n\s*// the shared menu background\.\r?\n'
        r'\s*list\.setRenderTopAndBottom\(false\);\r?\n', '', t)
    p.write_text(t, encoding='utf-8', newline='\n')
    print('mixin', path)


if __name__ == '__main__':
    project = Path(sys.argv[1])
    screens = project / 'src/main/java/net/wurstclient/clickgui2/screens'
    for name in ('EditBlockScreen.java', 'EditColorScreen.java',
                 'EditSliderScreen.java', 'EditTextFieldScreen.java',
                 'SelectFileScreen.java'):
        fix_screen(screens / name)
    fix_mixin(project / 'src/main/java/net/wurstclient/mixin'
              / 'AbstractSelectionListMixin.java')
