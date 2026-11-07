# -*- coding: utf-8 -*-
"""Restore the GUI framebuffer in 1.21.5 before compositing the NanoVG HUD.

1.21.5 removed RenderTarget.bindWrite, so endFrame() leaves framebuffer 0
bound and the HUD is drawn into the backbuffer that Minecraft overwrites.
Capture the FBO bound during the GUI callback and rebind it for composite().
"""
import sys
from pathlib import Path

OLD_TRY = (
    '\t\ttry(GlStateGuard ignored = GlStateGuard.capture())\n'
    '\t\t{\n'
    '\t\t\tif(!nvg.beginFrame())\n'
    '\t\t\t\treturn;\n'
)
NEW_TRY = (
    '\t\ttry(GlStateGuard ignored = GlStateGuard.capture())\n'
    '\t\t{\n'
    '\t\t\tint targetFbo =\n'
    '\t\t\t\tGL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);\n'
    '\t\t\tif(!nvg.beginFrame())\n'
    '\t\t\t\treturn;\n'
)

OLD_COMPOSITE = (
    '\t\t\tRenderTarget target = WURST.MC.getMainRenderTarget();\n'
    '\t\t\tGL11.glViewport(0, 0, target.width, target.height);\n'
    '\t\t\tnvg.composite();\n'
)
NEW_COMPOSITE = (
    '\t\t\tRenderTarget target = WURST.MC.getMainRenderTarget();\n'
    '\t\t\tGL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFbo);\n'
    '\t\t\tGL11.glViewport(0, 0, target.width, target.height);\n'
    '\t\t\tnvg.composite();\n'
)


def patch(project):
    p = Path(project) / 'src/main/java/net/wurstclient/hud2/HudManager.java'
    t = p.read_text(encoding='utf-8')
    if 'targetFbo' in t:
        print('already', project)
        return
    if OLD_TRY not in t or OLD_COMPOSITE not in t:
        raise SystemExit('anchor missing in %s' % p)
    if 'import org.lwjgl.opengl.GL30;' not in t:
        t = t.replace('import org.lwjgl.opengl.GL11;',
                      'import org.lwjgl.opengl.GL11;\n'
                      'import org.lwjgl.opengl.GL30;', 1)
    t = t.replace(OLD_TRY, NEW_TRY, 1)
    t = t.replace(OLD_COMPOSITE, NEW_COMPOSITE, 1)
    p.write_text(t, encoding='utf-8', newline='\n')
    print('patched', project)


if __name__ == '__main__':
    for arg in sys.argv[1:]:
        patch(arg)
