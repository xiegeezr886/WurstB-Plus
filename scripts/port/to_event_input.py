# -*- coding: utf-8 -*-
"""Convert the copied 1.20.1 HUD editor input methods to the 1.21.9+ event API."""
import sys
from pathlib import Path


def convert(path):
    p = Path(path) / 'src/main/java/net/wurstclient/hud2/GuiEditHUD.java'
    t = p.read_text(encoding='utf-8')
    if 'MouseButtonEvent event' in t:
        return
    if 'import net.minecraft.client.input.MouseButtonEvent;' not in t:
        t = t.replace(
            'import net.minecraft.client.gui.screens.Screen;',
            'import net.minecraft.client.gui.screens.Screen;\n'
            'import net.minecraft.client.input.KeyEvent;\n'
            'import net.minecraft.client.input.MouseButtonEvent;', 1)
    t = t.replace(
        'public boolean mouseClicked(double mouseX, double mouseY, int button)\n'
        '\t{\n',
        'public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)\n'
        '\t{\n\t\tdouble mouseX = event.x();\n\t\tdouble mouseY = event.y();\n'
        '\t\tint button = event.button();\n\n', 1)
    t = t.replace('return super.mouseClicked(mouseX, mouseY, button);',
                  'return super.mouseClicked(event, doubleClick);')
    t = t.replace(
        'public boolean mouseReleased(double mouseX, double mouseY, int button)\n',
        'public boolean mouseReleased(MouseButtonEvent event)\n')
    t = t.replace('button == GLFW.GLFW_MOUSE_BUTTON_LEFT',
                  'event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT', 1)
    t = t.replace('return super.mouseReleased(mouseX, mouseY, button);',
                  'return super.mouseReleased(event);')
    t = t.replace(
        'public boolean mouseDragged(double mouseX, double mouseY, int button,\n'
        '\t\tdouble deltaX, double deltaY)\n\t{\n',
        'public boolean mouseDragged(MouseButtonEvent event, double deltaX,\n'
        '\t\tdouble deltaY)\n\t{\n\t\tdouble mouseX = event.x();\n'
        '\t\tdouble mouseY = event.y();\n\n', 1)
    t = t.replace('return super.mouseDragged(mouseX, mouseY, button, deltaX,\n'
                  '\t\t\tdeltaY);',
                  'return super.mouseDragged(event, deltaX, deltaY);')
    t = t.replace(
        'public boolean keyPressed(int keyCode, int scanCode, int modifiers)\n'
        '\t{\n', 'public boolean keyPressed(KeyEvent event)\n\t{\n', 1)
    t = t.replace('if(keyCode == GLFW.GLFW_KEY_ESCAPE)',
                  'if(event.key() == GLFW.GLFW_KEY_ESCAPE)')
    t = t.replace('if(keyCode == GLFW.GLFW_KEY_T)',
                  'if(event.key() == GLFW.GLFW_KEY_T)')
    t = t.replace('if(keyCode == GLFW.GLFW_KEY_R)',
                  'if(event.key() == GLFW.GLFW_KEY_R)')
    t = t.replace('return super.keyPressed(keyCode, scanCode, modifiers);',
                  'return super.keyPressed(event);')
    p.write_text(t, encoding='utf-8', newline='\n')
    print('event input', path)


if __name__ == '__main__':
    for p in sys.argv[1:]:
        convert(p)
