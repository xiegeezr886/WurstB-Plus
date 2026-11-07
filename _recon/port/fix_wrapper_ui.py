# -*- coding: utf-8 -*-
"""Adapt the ported 1.5.1 UI to the wrapper family (1.21.6-1.21.11).

The wrapper projects keep a net.wurstclient.util.render.GuiGraphicsExtractor
shim over vanilla GuiGraphics. The 1.20.1 sources use plain GuiGraphics, so
merge results often lose the shim import or mix the two types.
"""
import re
import sys
from pathlib import Path

SHIM = 'net.wurstclient.util.render.GuiGraphicsExtractor'
SHIM_IMPORT = 'import net.wurstclient.util.render.GuiGraphicsExtractor;'
VANILLA_IMPORT = 'import net.minecraft.client.gui.GuiGraphics;'


def ensure_import(text, imp):
    if imp in text:
        return text
    lines = text.splitlines()
    package_index = next(i for i, l in enumerate(lines)
                         if l.startswith('package '))
    lines.insert(package_index + 1, '')
    lines.insert(package_index + 2, imp)
    return '\n'.join(lines) + '\n'


def patch_file(p):
    t = p.read_text(encoding='utf-8')
    original = t
    has_shim_type = 'GuiGraphicsExtractor' in t
    has_vanilla_type = re.search(r'(?<!GuiGraphicsExtractor)\bGuiGraphics\b', t)

    if has_shim_type:
        if 'import net.wurstclient.util.render.GuiGraphicsExtractor;' not in t:
            t = ensure_import(t, SHIM_IMPORT)
        # Files that wrap a vanilla screen need both types; everything else
        # should use the shim exclusively.
        if 'new GuiGraphicsExtractor(' not in t:
            t = t.replace(VANILLA_IMPORT + '\n', '')
            t = re.sub(r'(?<!GuiGraphicsExtractor)\bGuiGraphics\b',
                       'GuiGraphicsExtractor', t)
            t = re.sub(r'\n{3,}', '\n\n', t)
    elif has_vanilla_type and VANILLA_IMPORT not in t:
        t = ensure_import(t, VANILLA_IMPORT)

    if t != original:
        p.write_text(t, encoding='utf-8', newline='\n')
        print('ui', p)


def patch_screen_calls(path):
    p = Path(path)
    t = p.read_text(encoding='utf-8')
    original = t
    t = t.replace('renderBackground(context);',
                  'renderBackground(context.getInner(), mouseX, mouseY, '
                  'partialTicks);')
    t = t.replace('renderBackground(context, mouseX, mouseY, partialTicks);',
                  'renderBackground(context.getInner(), mouseX, mouseY, '
                  'partialTicks);')
    t = t.replace('super.render(context, mouseX, mouseY, partialTicks);',
                  'super.render(context.getInner(), mouseX, mouseY, '
                  'partialTicks);')
    if t != original:
        p.write_text(t, encoding='utf-8', newline='\n')
        print('calls', p)


def patch(project):
    root = Path(project) / 'src/main/java/net/wurstclient'

    for sub in ('clickgui2', 'gui'):
        directory = root / sub
        if not directory.exists():
            continue
        for p in directory.rglob('*.java'):
            patch_file(p)

    for p in (root / 'clickgui2/screens').glob('*.java'):
        patch_screen_calls(p)
    for name in ('NavigatorScreen.java', 'ClickGuiScreen.java'):
        p = root / 'clickgui2' / name
        if p.exists():
            patch_screen_calls(p)

    # 1.21.2 renamed UseAnim to ItemUseAnimation.
    p = root / 'hacks/KillauraHack.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        t = t.replace('import net.minecraft.world.item.UseAnim;',
                      'import net.minecraft.world.item.ItemUseAnimation;')
        t = t.replace('UseAnim.BLOCK', 'ItemUseAnimation.BLOCK')
        p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'mixin/OptionsSubScreenMixin.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        t = t.replace(
            'import net.minecraft.client.gui.screens.OptionsSubScreen;',
            'import net.minecraft.client.gui.screens.options.'
            'OptionsSubScreen;')
        p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'hacks/scaffold/ScaffoldUtils.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        t = t.replace('EnchantmentTableBlock', 'EnchantingTableBlock')
        p.write_text(t, encoding='utf-8', newline='\n')

    print('fixed', project)


if __name__ == '__main__':
    for arg in sys.argv[1:]:
        patch(arg)
