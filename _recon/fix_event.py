# -*- coding: utf-8 -*-
"""1.21.9+ event-wrapper fixes on top of adapt_wrapper."""
import re
import sys
from pathlib import Path


def patch(base):
    root = Path(base) / 'src/main/java/net/wurstclient'
    editor = root / 'hud2/GuiEditHUD.java'
    t = editor.read_text(encoding='utf-8')
    t = t.replace('if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT)',
                  'if(event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT)', 1)
    t = t.replace('return super.mouseDragged(mouseX, mouseY, button, deltaX,\n'
                  '\t\t\t\tdeltaY);',
                  'return super.mouseDragged(event, deltaX, deltaY);')
    editor.write_text(t, encoding='utf-8', newline='\n')

    for p in root.rglob('*.java'):
        t = p.read_text(encoding='utf-8')
        o = t
        t = t.replace('getSkin().texture()', 'getSkin().body().texturePath()')
        t = t.replace('getGameProfile().getName()',
                      'getGameProfile().name()')
        t = t.replace('getGameProfile().getId()', 'getGameProfile().id()')
        if t != o:
            p.write_text(t, encoding='utf-8', newline='\n')

    strokes = root / 'hud2/mods/MouseStrokesMod.java'
    t = strokes.read_text(encoding='utf-8')
    t = re.sub(
        r'long window = MC\.getWindow\(\)\.getWindow\(\);\s*'
        r'boolean leftDown = GLFW\.glfwGetMouseButton\(window,\s*'
        r'GLFW\.GLFW_MOUSE_BUTTON_LEFT\) == GLFW\.GLFW_PRESS && !isEditing\(\);\s*'
        r'boolean rightDown = GLFW\.glfwGetMouseButton\(window,\s*'
        r'GLFW\.GLFW_MOUSE_BUTTON_RIGHT\) == GLFW\.GLFW_PRESS && !isEditing\(\);',
        'boolean leftDown = MC.options.keyAttack.isDown() && !isEditing();\n'
        '\t\tboolean rightDown = MC.options.keyUse.isDown() && !isEditing();',
        t)
    t = t.replace('import org.lwjgl.glfw.GLFW;\n\n', '')
    strokes.write_text(t, encoding='utf-8', newline='\n')
    print('fixed event', base)


if __name__ == '__main__':
    for b in sys.argv[1:]:
        patch(b)
