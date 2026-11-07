# -*- coding: utf-8 -*-
"""Generate version-family variants of the Soar HUD sources."""
import re
from pathlib import Path

ROOT = Path('fabric/versions/1.21.11/src/main/java/net/wurstclient')
OUT = Path('_recon/hud_variants')
OUT.mkdir(parents=True, exist_ok=True)

PLACEHOLDER_IMPORT = 'import net.wurstclient.util.render.GuiGraphicsExtractor;'
NATIVE_IMPORT = 'import net.minecraft.client.gui.GuiGraphicsExtractor;'


def read(rel):
    return (ROOT / rel).read_text(encoding='utf-8')


def write(name, text):
    (OUT / name).write_text(text, encoding='utf-8', newline='\n')


# ---------------------------------------------------------------- style
style = read('hud2/SoarHudStyle.java')
write('SoarHudStyle_extractor.java', style)
write('SoarHudStyle_native.java',
      style.replace(PLACEHOLDER_IMPORT, NATIVE_IMPORT))
legacy_style = style.replace(
    PLACEHOLDER_IMPORT, 'import net.minecraft.client.gui.GuiGraphics;')
legacy_style = legacy_style.replace('GuiGraphicsExtractor graphics',
                                    'GuiGraphics graphics')
legacy_style = legacy_style.replace(
    'graphics.text(font, text, x, y, color, false);',
    'graphics.drawString(font, text, x, y, color, false);')
write('SoarHudStyle_legacy.java', legacy_style)

# ---------------------------------------------------------------- adapter
adapter = read('hud2/HackHudElement.java')

write('HackHudElement_extractor.java', adapter)

native = adapter.replace(PLACEHOLDER_IMPORT, NATIVE_IMPORT)
# Native GuiGraphicsExtractor has no bounds tracking; use fallback sizes.
native = native.replace('\t\tgraphics.beginBoundsTracking();\n', '')
native = native.replace('\t\t\tgraphics.endBoundsTracking();\n', '')
native = re.sub(
    r'\n\t\tint minX = graphics\.getTrackedMinX\(\);.*?'
    r'measuredHeight = contentHeight \+ PANEL_PADDING \* 2;\n\t\t\}',
    '\n\t\tpanelVisible = true;', native, flags=re.S)
write('HackHudElement_native.java', native)

legacy = native.replace(
    PLACEHOLDER_IMPORT, 'import net.minecraft.client.gui.GuiGraphics;')
legacy = legacy.replace('GuiGraphicsExtractor graphics', 'GuiGraphics graphics')
legacy = legacy.replace('pose.pushMatrix();', 'pose.pushPose();')
legacy = legacy.replace('pose.popMatrix();', 'pose.popPose();')
legacy = legacy.replace(
    'pose.translate(x + PANEL_PADDING - originX - contentOffsetX,\n'
    '\t\t\ty + PANEL_PADDING - originY - contentOffsetY);',
    'pose.translate(x + PANEL_PADDING - originX - contentOffsetX,\n'
    '\t\t\ty + PANEL_PADDING - originY - contentOffsetY, 0);')
write('HackHudElement_legacy.java', legacy)

# ---------------------------------------------------------------- manager
manager = read('hud2/HudManager.java')
write('HudManager_extractor.java', manager)
write('HudManager_native.java', manager.replace(
    'net.wurstclient.util.render.GuiGraphicsExtractor',
    'net.minecraft.client.gui.GuiGraphicsExtractor'))
write('HudManager_legacy.java', manager.replace(
    'net.wurstclient.util.render.GuiGraphicsExtractor',
    'net.minecraft.client.gui.GuiGraphics'))

# ---------------------------------------------------------------- notification
notif = read('hud2/HudNotificationRenderer.java')
write('HudNotificationRenderer_extractor.java', notif)
write('HudNotificationRenderer_native.java',
      notif.replace(PLACEHOLDER_IMPORT, NATIVE_IMPORT))
notif_legacy = notif.replace(PLACEHOLDER_IMPORT,
                             'import net.minecraft.client.gui.GuiGraphics;')
notif_legacy = notif_legacy.replace('GuiGraphicsExtractor graphics',
                                    'GuiGraphics graphics')
notif_legacy = notif_legacy.replace('graphics.text(font,',
                                    'graphics.drawString(font,')
write('HudNotificationRenderer_legacy.java', notif_legacy)

# ---------------------------------------------------------------- editor
editor = read('hud2/HudEditorScreen.java')


def old_input(text):
    text = text.replace(
        'public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)\n'
        '\t{\n\t\tdouble mouseX = event.x();\n\t\tdouble mouseY = event.y();\n'
        '\t\tint button = event.button();\n',
        'public boolean mouseClicked(double mouseX, double mouseY, int button)\n'
        '\t{\n')
    text = text.replace('return super.mouseClicked(event, doubleClick);',
                        'return super.mouseClicked(mouseX, mouseY, button);')
    text = text.replace(
        'public boolean mouseReleased(MouseButtonEvent event)\n\t{\n',
        'public boolean mouseReleased(double mouseX, double mouseY, int button)\n\t{\n')
    text = text.replace('event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT',
                        'button == GLFW.GLFW_MOUSE_BUTTON_LEFT')
    text = text.replace('return super.mouseReleased(event);',
                        'return super.mouseReleased(mouseX, mouseY, button);')
    text = text.replace(
        'public boolean mouseDragged(MouseButtonEvent event,\n'
        '\t\tdouble deltaX, double deltaY)\n\t{\n'
        '\t\tdouble mouseX = event.x();\n\t\tdouble mouseY = event.y();\n',
        'public boolean mouseDragged(double mouseX, double mouseY, int button,\n'
        '\t\tdouble deltaX, double deltaY)\n\t{\n')
    text = text.replace(
        'return super.mouseDragged(event, deltaX, deltaY);',
        'return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);')
    text = text.replace(
        'public boolean keyPressed(KeyEvent event)\n\t{\n'
        '\t\tif(event.key() == GLFW.GLFW_KEY_ESCAPE)',
        'public boolean keyPressed(int keyCode, int scanCode, int modifiers)\n'
        '\t{\n\t\tif(keyCode == GLFW.GLFW_KEY_ESCAPE)')
    text = text.replace('return super.keyPressed(event);',
                        'return super.keyPressed(keyCode, scanCode, modifiers);')
    return text


# event-input extractor (1.21.9-1.21.11)
write('HudEditorScreen_wrapper_event.java', editor)

# old-input extractor (1.21.6-1.21.8)
wrapper_old = old_input(editor)
wrapper_old = wrapper_old.replace(
    'import net.minecraft.client.input.KeyEvent;\n', '')
wrapper_old = wrapper_old.replace(
    'import net.minecraft.client.input.MouseButtonEvent;\n', '')
write('HudEditorScreen_wrapper_old.java', wrapper_old)

# old-input GuiGraphics (1.20.2-1.21.5)
legacy_editor = old_input(editor)
legacy_editor = legacy_editor.replace(
    'import net.minecraft.client.input.KeyEvent;\n', '')
legacy_editor = legacy_editor.replace(
    'import net.minecraft.client.input.MouseButtonEvent;\n', '')
legacy_editor = legacy_editor.replace(PLACEHOLDER_IMPORT,
                                      'import net.minecraft.client.gui.GuiGraphics;')
legacy_editor = legacy_editor.replace('GuiGraphicsExtractor', 'GuiGraphics')
legacy_editor = legacy_editor.replace('graphics.text(font,',
                                      'graphics.drawString(font,')
legacy_editor = legacy_editor.replace('graphics.centeredText(',
                                      'graphics.drawCenteredString(')
legacy_editor = legacy_editor.replace(
    'renderContents(new GuiGraphics(graphics), mouseX, mouseY,\n'
    '\t\t\tpartialTicks);',
    'renderBackground(graphics, mouseX, mouseY, partialTicks);\n'
    '\t\trenderContents(graphics, mouseX, mouseY, partialTicks);')
write('HudEditorScreen_legacy.java', legacy_editor)

# native extractor + event input (26.x)
native_editor = editor.replace(PLACEHOLDER_IMPORT, NATIVE_IMPORT)
native_editor = native_editor.replace(
    'public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks)\n'
    '\t{\n\t\trenderContents(new GuiGraphicsExtractor(graphics), mouseX, mouseY,\n'
    '\t\t\tpartialTicks);\n\t}',
    'public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX,\n'
    '\t\tint mouseY, float partialTicks)\n\t{\n'
    '\t\trenderContents(graphics, mouseX, mouseY, partialTicks);\n\t}')
native_editor = native_editor.replace(
    'import net.minecraft.client.gui.GuiGraphics;\n', '')
write('HudEditorScreen_native.java', native_editor)

print('variants written to', OUT)
