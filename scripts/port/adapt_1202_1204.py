# -*- coding: utf-8 -*-
"""Idempotent API adaptation for the 1.20.2-1.20.4 HUD port."""
import re
import sys
from pathlib import Path


def patch(target):
    base = Path(target) / 'src/main/java/net/wurstclient'
    if not base.exists():
        raise SystemExit('missing %s' % base)

    # Category.HUD
    cat = base / 'Category.java'
    t = cat.read_text(encoding='utf-8')
    if 'HUD(' not in t:
        t, n = re.subn(r'(\n\s*[A-Z_]+\([^\n]*\))(;)',
                       r'\1,\n\tHUD("HUD")\2', t, count=1)
        if n:
            cat.write_text(t, encoding='utf-8', newline='\n')

    # Remove the old hack-list update hook.
    hack = base / 'hack/Hack.java'
    t = hack.read_text(encoding='utf-8')
    if 'updateHudState' in t:
        t = t.replace('\t\t\t\tupdateHudState();\n', '')
        t = t.replace('\t\t\t\t\tupdateHudState();\n', '')
        t = re.sub(r'\tprivate void updateHudState\(\)\s*\{.*?\n\t\}\n',
                   '', t, flags=re.S)
        hack.write_text(t, encoding='utf-8', newline='\n')

    # Strip the deleted hack-list/logo members from IngameHUD.
    ingame = base / 'hud/IngameHUD.java'
    t = ingame.read_text(encoding='utf-8')
    t = re.sub(r'\tprivate final WurstLogo[^\n]*\n', '', t)
    t = re.sub(r'\tprivate final HackListHUD[^\n]*\n', '', t)
    t = re.sub(
        r'\tpublic HackListHUD getHackList\(\)\s*\{.*?\n\t\}\n',
        '', t, flags=re.S)
    t = re.sub(
        r'\tpublic WurstLogo getWurstLogo\(\)\s*\{.*?\n\t\}\n',
        '', t, flags=re.S)
    ingame.write_text(t, encoding='utf-8', newline='\n')

    # Compatibility shim for the old hack preview guards.
    shim = base / 'hud2/HackHudElement.java'
    if not shim.exists():
        shim.write_text(
            'package net.wurstclient.hud2;\n\n'
            'public final class HackHudElement\n{\n'
            '\tprivate HackHudElement() {}\n\n'
            '\tpublic static boolean isPreviewRendering()\n\t{\n'
            '\t\treturn false;\n\t}\n\n'
            '\tpublic static boolean isSuppressed(Object listener)\n\t{\n'
            '\t\treturn false;\n\t}\n\n'
            '\tpublic static void clearManaged() {}\n}\n',
            encoding='utf-8', newline='\n')

    # 1.20.2 scoreboard/skin APIs.
    compat = base / 'hud2/compat/HudCompat.java'
    t = compat.read_text(encoding='utf-8')
    t = re.sub(r'getDisplayObjective\(1\)',
               'getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR)',
               t)
    t = t.replace('info.getSkinLocation().toString()',
                  'info.getSkin().texture().toString()')
    t = t.replace('player.getSkinTextureLocation().toString()',
                  'player.getSkin().texture().toString()')
    compat.write_text(t, encoding='utf-8', newline='\n')

    # 1.20.2 scroll signature.
    editor = base / 'hud2/GuiEditHUD.java'
    t = editor.read_text(encoding='utf-8')
    t = t.replace(
        'public boolean mouseScrolled(double mouseX, double mouseY, double scrollY)',
        'public boolean mouseScrolled(double mouseX, double mouseY, '
        'double scrollX, double scrollY)')
    t = t.replace(
        'return super.mouseScrolled(mouseX, mouseY, scrollY);',
        'return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);')
    editor.write_text(t, encoding='utf-8', newline='\n')

    # Killaura no longer exposes getRange() in this band.
    target_mod = base / 'hud2/mods/TargetInfoMod.java'
    t = target_mod.read_text(encoding='utf-8')
    t = t.replace('searchRange = Math.max(searchRange, aura.getRange());',
                  'searchRange = Math.max(searchRange, 6);')
    target_mod.write_text(t, encoding='utf-8', newline='\n')

    # 1.20.2 debug state.
    manager = base / 'hud2/HudManager.java'
    t = manager.read_text(encoding='utf-8')
    t = t.replace('WURST.MC.options.renderDebug',
                  'WURST.MC.getDebugOverlay().showDebugScreen()')
    manager.write_text(t, encoding='utf-8', newline='\n')
    print('adapted', target)


if __name__ == '__main__':
    patch(sys.argv[1])
