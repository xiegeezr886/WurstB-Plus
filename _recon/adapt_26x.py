# -*- coding: utf-8 -*-
"""Apply the 26.1/26.1.1/26.1.2 or 26.2 API adaptations to a HUD port."""
import re
import sys
from pathlib import Path

OLD_FILES = ['hud2/HudEditorScreen.java', 'hud2/HudElement.java',
             'hud2/HudLayout.java']


def patch(base, version):
    base = Path(base) / 'src/main/java/net/wurstclient'
    if not base.exists():
        raise SystemExit('missing %s' % base)
    for rel in OLD_FILES:
        p = base / rel
        if p.exists():
            p.unlink()
    for p in base.rglob('*.java'):
        t = p.read_text(encoding='utf-8')
        o = t
        t = t.replace('net.wurstclient.hud2.HudEditorScreen',
                      'net.wurstclient.hud2.GuiEditHUD')
        t = t.replace('HudEditorScreen', 'GuiEditHUD')
        if version == '26.2':
            t = re.sub(r'MC\.gui(\s*)\.getChat\(\)',
                       r'MC.gui.hud\1.getChat()', t)
            t = re.sub(r'\.MC\.gui(\s*)\.getBossOverlay\(\)',
                       r'.MC.gui.hud\1.getBossOverlay()', t)
            t = re.sub(r'(?<!\.)\bMC\.gui(\s*)\.getBossOverlay\(\)',
                       r'MC.gui.hud\1.getBossOverlay()', t)
            t = t.replace('WURST.MC.options.hideGui',
                          'WURST.MC.gui.hud.isHidden()')
            t = t.replace('WurstClient.MC.options.hideGui',
                          'WurstClient.MC.gui.hud.isHidden()')
            t = t.replace('MC.options.hideGui', 'MC.gui.hud.isHidden()')
            t = t.replace('getMainCamera()', 'mainCamera()')
            t = t.replace('WURST.MC.screen', 'WURST.MC.gui.screen()')
            t = t.replace('WurstClient.MC.screen',
                          'WurstClient.MC.gui.screen()')
            t = t.replace('MC.screen', 'MC.gui.screen()')
        else:
            t = t.replace('MC.gui.screen()', 'MC.screen')
            t = t.replace('WURST.MC.gui.screen()', 'WURST.MC.screen')
            t = t.replace('MC.gui.hud.isHidden()', 'MC.options.hideGui')
            t = t.replace('WURST.MC.gui.hud.isHidden()',
                          'WURST.MC.options.hideGui')
            t = t.replace('MC.gui.hud', 'MC.gui')
            t = t.replace('WURST.MC.gui.hud', 'WURST.MC.gui')
            t = t.replace('WurstClient.MC.gui.hud', 'WurstClient.MC.gui')
            t = t.replace('InputConstants.Type.KEYBOARD',
                          'InputConstants.Type.KEYSYM')
            t = t.replace('mainCamera()', 'getMainCamera()')
        if t != o:
            p.write_text(t, encoding='utf-8', newline='\n')
    print('adapted', base, version)


if __name__ == '__main__':
    patch(sys.argv[1], sys.argv[2])
