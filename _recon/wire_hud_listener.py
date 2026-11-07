# -*- coding: utf-8 -*-
"""Register the ported HudManager as a GUIRenderListener in legacy/wrapper
projects. The 26.x projects already have this wiring."""
import sys
from pathlib import Path

SKIP = {'26.1', '26.1.1', '26.1.2', '26.2', '26.3'}


def mc_of(project):
    import re
    t = (project / 'gradle.properties').read_text(encoding='utf-8')
    m = re.search(r'^\s*minecraft_version\s*=\s*(.+?)\s*$', t, re.M)
    return m.group(1) if m else ''


def patch(target):
    target = Path(target)
    if mc_of(target) in SKIP:
        return
    root = target / 'src/main/java/net/wurstclient'
    listener = root / 'events/GUIRenderListener.java'
    lt = listener.read_text(encoding='utf-8')
    if 'net.wurstclient.util.render.GuiGraphicsExtractor' in lt:
        gfx = 'net.wurstclient.util.render.GuiGraphicsExtractor'
        gfx_import = 'import net.wurstclient.util.render.GuiGraphicsExtractor;'
    elif 'import net.minecraft.client.gui.GuiGraphicsExtractor;' in lt:
        gfx = 'net.minecraft.client.gui.GuiGraphicsExtractor'
        gfx_import = 'import net.minecraft.client.gui.GuiGraphicsExtractor;'
    else:
        gfx = 'net.minecraft.client.gui.GuiGraphics'
        gfx_import = 'import net.minecraft.client.gui.GuiGraphics;'

    hm = root / 'hud2/HudManager.java'
    t = hm.read_text(encoding='utf-8')
    if 'implements GUIRenderListener' in t:
        return
    # imports
    for imp in ['import net.wurstclient.event.EventManager;',
                'import net.wurstclient.events.GUIRenderListener;',
                gfx_import]:
        if imp not in t:
            t = t.replace('import net.wurstclient.WurstClient;',
                          'import net.wurstclient.WurstClient;\n' + imp, 1)
    t = t.replace('public final class HudManager\n{',
                  'public final class HudManager implements GUIRenderListener\n{', 1)

    # start registration
    anchor = '\t\tSystem.out.println("[HUD] Soar v7 HUD started'
    add = ('\t\tEventManager eventManager = WURST.getEventManager();\n'
           '\t\tif(eventManager != null)\n'
           '\t\t\teventManager.add(GUIRenderListener.class, this);\n')
    if 'eventManager.add(GUIRenderListener.class, this)' not in t and anchor in t:
        t = t.replace(anchor, add + anchor, 1)
    # stop removal
    anchor_stop = '\t\tstopping = true;\n'
    add_stop = ('\t\tEventManager eventManager = WURST.getEventManager();\n'
                '\t\tif(eventManager != null)\n'
                '\t\t\teventManager.remove(GUIRenderListener.class, this);\n')
    if anchor_stop in t and 'eventManager.remove(GUIRenderListener.class, this)' not in t:
        t = t.replace(anchor_stop, anchor_stop + add_stop, 1)
    # onRenderGUI before renderFrame
    anchor_rf = '\tpublic void renderFrame(float partialTicks)'
    add_rf = ('\t@Override\n'
              '\tpublic void onRenderGUI(' + gfx + ' graphics, float partialTicks)\n'
              '\t{\n\t\trenderFrame(partialTicks);\n\t}\n\n')
    if anchor_rf in t and 'onRenderGUI(' not in t:
        t = t.replace(anchor_rf, add_rf + anchor_rf, 1)
    hm.write_text(t, encoding='utf-8', newline='\n')
    print('wired', target)


if __name__ == '__main__':
    for p in sys.argv[1:]:
        patch(p)
