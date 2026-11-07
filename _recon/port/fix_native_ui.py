# -*- coding: utf-8 -*-
"""Adapt the ported 1.5.1 UI to the native 26.x render API.

26.x replaces GuiGraphics with GuiGraphicsExtractor throughout and renames
widget render methods to extractRenderState/extractBackground.
"""
import re
import sys
from pathlib import Path


def patch(project):
    root = Path(project) / 'src/main/java/net/wurstclient'

    for p in (root / 'clickgui2/screens').glob('*.java'):
        t = p.read_text(encoding='utf-8')
        original = t
        t = t.replace('renderBackground(context);',
                      'extractBackground(context, mouseX, mouseY, '
                      'partialTicks);')
        t = t.replace('super.render(context, mouseX, mouseY, partialTicks);',
                      'super.extractRenderState(context, mouseX, mouseY, '
                      'partialTicks);')
        t = re.sub(
            r'(\b\w+(?:Field|Gui|Box|Widget|Button|Button|List))\.render\('
            r'context, mouseX, mouseY, partialTicks\);',
            r'\1.extractRenderState(context, mouseX, mouseY, '
            r'partialTicks);', t)
        if t != original:
            p.write_text(t, encoding='utf-8', newline='\n')
            print('screen', p.name)

    p = root / 'clickgui2/NavigatorScreen.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        t = t.replace(
            'searchBox.render(graphics, mouseX, mouseY, partialTicks);',
            'searchBox.extractRenderState(graphics, mouseX, mouseY, '
            'partialTicks);')
        p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'hacks/KillauraHack.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        t = t.replace('import net.minecraft.world.item.UseAnim;',
                      'import net.minecraft.world.item.ItemUseAnimation;')
        t = t.replace('UseAnim.BLOCK', 'ItemUseAnimation.BLOCK')
        p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'hacks/aura/OpenAura.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        t = t.replace(
            'import net.minecraft.client.renderer.RenderType;',
            'import net.minecraft.client.renderer.rendertype.RenderType;')
        p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'hacks/scaffold/ScaffoldUtils.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        t = t.replace('RedstoneWireBlock', 'RedStoneWireBlock')
        p.write_text(t, encoding='utf-8', newline='\n')

    print('fixed', project)


if __name__ == '__main__':
    for arg in sys.argv[1:]:
        patch(arg)
