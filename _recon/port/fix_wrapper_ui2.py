# -*- coding: utf-8 -*-
"""Second-pass wrapper-family UI fixes after the 1.5.1 port."""
import re
import sys
from pathlib import Path

WIDGETS = ('listGui', 'blockNameField', 'blockField', 'levelField',
           'priceField', 'hexValueField', 'redValueField', 'greenValueField',
           'blueValueField', 'valueField')


def patch_screens(root):
    for p in (root / 'clickgui2/screens').glob('*.java'):
        t = p.read_text(encoding='utf-8')
        original = t
        for widget in WIDGETS:
            t = t.replace(widget + '.render(context,',
                          widget + '.render(context.getInner(),')
        t = t.replace('renderDirtBackground(context);',
                      'renderBackground(context.getInner(), mouseX, mouseY, '
                      'partialTicks);')
        t = t.replace('new ResourceLocation("enchanted_book")',
                      'ResourceLocation.parse("enchanted_book")')
        if t != original:
            p.write_text(t, encoding='utf-8', newline='\n')
            print('screen', p.name)

    p = root / 'clickgui2/NavigatorScreen.java'
    t = p.read_text(encoding='utf-8')
    t = t.replace('searchBox.render(graphics,',
                  'searchBox.render(graphics.getInner(),')
    p.write_text(t, encoding='utf-8', newline='\n')

    # EditBlockScreen was merged without the wrapper/extractContents split.
    p = root / 'clickgui2/screens/EditBlockScreen.java'
    t = p.read_text(encoding='utf-8')
    pattern = re.compile(
        r'@Override\s*\n\s*public void render\(GuiGraphicsExtractor graphics,'
        r' int mouseX, int mouseY, float partialTicks\)\s*\n\s*\{\s*\n'
        r'\s*renderBackground\(context\.getInner\(\), mouseX, mouseY, '
        r'partialTicks\);\s*\n'
        r'\s*blockField\.render\(context(?:\.getInner\(\))?, mouseX, '
        r'mouseY, partialTicks\);\s*\n'
        r'\s*super\.render\(context\.getInner\(\), mouseX, mouseY, '
        r'partialTicks\);\s*\n')
    t = pattern.sub(
        '\t@Override\n\tpublic void render(GuiGraphics graphics, int mouseX, '
        'int mouseY, float partialTicks)\n\t{\n'
        '\t\textractContents(new GuiGraphicsExtractor(graphics), mouseX, '
        'mouseY,\n\t\t\tpartialTicks);\n\t}\n\n'
        '\tprivate void extractContents(GuiGraphicsExtractor context, '
        'int mouseX,\n\t\tint mouseY, float partialTicks)\n\t{\n'
        '\t\trenderBackground(context.getInner(), mouseX, mouseY, '
        'partialTicks);\n'
        '\t\tblockField.render(context.getInner(), mouseX, mouseY, '
        'partialTicks);\n'
        '\t\tsuper.render(context.getInner(), mouseX, mouseY, '
        'partialTicks);\n', t, count=1)
    if 'import net.minecraft.client.gui.GuiGraphics;' not in t:
        t = t.replace(
            'import net.wurstclient.util.render.GuiGraphicsExtractor;',
            'import net.minecraft.client.gui.GuiGraphics;\n'
            'import net.wurstclient.util.render.GuiGraphicsExtractor;')
    p.write_text(t, encoding='utf-8', newline='\n')

    # ClickGUI's row helpers must take the shim like Window.render does.
    p = root / 'clickgui2/ClickGuiScreen.java'
    t = p.read_text(encoding='utf-8')
    t = t.replace('renderRow(GuiGraphics graphics',
                  'renderRow(GuiGraphicsExtractor graphics')
    t = t.replace('fillRow(GuiGraphics graphics',
                  'fillRow(GuiGraphicsExtractor graphics')
    p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'gui/title/WurstTitleButton.java'
    t = p.read_text(encoding='utf-8')
    t = t.replace('void renderNanoVG(GuiGraphics graphics)',
                  'void renderNanoVG(GuiGraphicsExtractor graphics)')
    if 'import net.wurstclient.util.render.GuiGraphicsExtractor;' not in t:
        t = t.replace('import net.minecraft.client.gui.GuiGraphics;',
                      'import net.wurstclient.util.render.'
                      'GuiGraphicsExtractor;')
    p.write_text(t, encoding='utf-8', newline='\n')


def patch_misc(root):
    p = root / 'clickgui2/window/Window.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        t = t.replace('.pose().translate(x1 + 2, y1 + 2, 0);',
                      '.pose().translate(x1 + 2, y1 + 2);')
        t = t.replace('.pose().scale(0.6f, 0.6f, 1f);',
                      '.pose().scale(0.6f, 0.6f);')
        p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'clickgui2/components/SliderComponent.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        if 'import com.mojang.blaze3d.platform.InputConstants;' not in t:
            t = t.replace(
                'import net.minecraft.client.Minecraft;',
                'import com.mojang.blaze3d.platform.InputConstants;\n'
                'import net.minecraft.client.Minecraft;')
        p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'clickgui2/FlatRenderer.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        if 'int safeRadius' not in t:
            t = t.replace(
                '\t\tint x2, int y2, int radius, FlatTheme theme, '
                'boolean focused)\n\t{\n',
                '\t\tint x2, int y2, int radius, FlatTheme theme, '
                'boolean focused)\n\t{\n\t\tint safeRadius = Math.max(0, '
                'radius);\n')
        p.write_text(t, encoding='utf-8', newline='\n')

    for name in ('mixin/AbstractSelectionListMixin.java',
                 'mixin/OptionsSubScreenMixin.java'):
        p = root / name
        if p.exists():
            t = p.read_text(encoding='utf-8')
            t = t.replace('\t\tlist.setRenderBackground(false);\n', '')
            t = t.replace('\t\tlist.setRenderTopAndBottom(false);\n', '')
            t = t.replace(
                '\t\tAbstractSelectionList<?> list =\n'
                '\t\t\t(AbstractSelectionList<?>)(Object)this;\n', '')
            p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'hack/HackList.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        if 'EntityCullingHack entityCullingHack' not in t:
            t = t.replace(
                '\tpublic final ExtraElytraHack extraElytraHack = '
                'new ExtraElytraHack();',
                '\tpublic final EntityCullingHack entityCullingHack = '
                'new EntityCullingHack();\n'
                '\tpublic final ExtraElytraHack extraElytraHack = '
                'new ExtraElytraHack();')
            p.write_text(t, encoding='utf-8', newline='\n')

    # Wrapper HudManager draws through the GUIRenderListener, so the extra
    # GameRenderer hook that calls renderFrame(partialTick) does not apply.
    p = root / 'mixin/GameRendererMixin.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        lines = t.splitlines()
        start = end = None
        for i, line in enumerate(lines):
            if 'Composites the HUD right after the vanilla GUI pass' in line:
                start = i - 1 if i > 0 and lines[i - 1].strip() == '/**' \
                    else i
            if start is not None and 'renderFrame(partialTick);' in line:
                end = i
        if start is not None and end is not None:
            while end < len(lines) and lines[end].strip() != '}':
                end += 1
            del lines[start:end + 1]
            p.write_text('\n'.join(lines) + '\n', encoding='utf-8',
                         newline='\n')


def patch_final(root):
    p = root / 'hud/TabGui.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        t = t.replace('import net.minecraft.client.gui.GuiGraphics;',
                      'import net.wurstclient.util.render.'
                      'GuiGraphicsExtractor;')
        t = t.replace('GuiGraphics ', 'GuiGraphicsExtractor ')
        p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'gui/title/WurstTitleButton.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        t = t.replace(
            'protected void renderWidget(GuiGraphicsExtractor graphics, '
            'int mouseX, int mouseY,',
            'protected void renderWidget(GuiGraphics graphics, int mouseX, '
            'int mouseY,')
        t = t.replace('import net.minecraft.client.gui.GuiGraphics;', '')
        t = t.replace('import net.wurstclient.util.render.'
                      'GuiGraphicsExtractor;',
                      'import net.minecraft.client.gui.GuiGraphics;\n'
                      'import net.wurstclient.util.render.'
                      'GuiGraphicsExtractor;')
        t = re.sub(r'(?<!GuiGraphicsExtractor)\bGuiGraphics\b',
                   'GuiGraphicsExtractor', t)
        t = t.replace(
            'import net.minecraft.client.gui.GuiGraphicsExtractor;',
            'import net.minecraft.client.gui.GuiGraphics;')
        t = t.replace(
            'protected void renderWidget(GuiGraphicsExtractor graphics, '
            'int mouseX, int mouseY,',
            'protected void renderWidget(GuiGraphics graphics, int mouseX, '
            'int mouseY,')
        p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'clickgui2/components/SliderComponent.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        if 'import com.mojang.blaze3d.platform.InputConstants;' not in t:
            t = t.replace(
                'import net.minecraft.client.gui.Font;',
                'import com.mojang.blaze3d.platform.InputConstants;\n'
                'import net.minecraft.client.gui.Font;')
        p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'clickgui2/screens/EditItemListScreen.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        t = t.replace('itemNameField.render(context,',
                      'itemNameField.render(context.getInner(),')
        p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'util/RenderUtils.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        t = t.replace(
            'private static MultiBufferSource.BufferSource getVCP()',
            'public static MultiBufferSource.BufferSource getVCP()')
        p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'gui/ButtonStyleRenderer.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        t = t.replace(
            'NanoVGUI.pass(graphics, () -> {',
            'NanoVGUI.pass(new net.wurstclient.util.render.'
            'GuiGraphicsExtractor(graphics), () -> {')
        p.write_text(t, encoding='utf-8', newline='\n')

    p = root / 'gui/MainMenuBackground.java'
    if p.exists():
        t = p.read_text(encoding='utf-8')
        t = t.replace(
            'NanoVGUI.pass(graphics,',
            'NanoVGUI.pass(new net.wurstclient.util.render.'
            'GuiGraphicsExtractor(graphics),')
        p.write_text(t, encoding='utf-8', newline='\n')


def patch(project):
    root = Path(project) / 'src/main/java/net/wurstclient'
    patch_screens(root)
    patch_misc(root)
    patch_final(root)
    print('fixed', project)


if __name__ == '__main__':
    for arg in sys.argv[1:]:
        patch(arg)
