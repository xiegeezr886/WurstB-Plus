# -*- coding: utf-8 -*-
"""Final 1.21.5 composite fix: bind Minecraft's main target FBO directly.

1.21.5 removed RenderTarget.bindWrite; during the HUD callback no framebuffer
is bound, so NanoVG's composite went to framebuffer 0 and was overwritten by
the main target blit. GlTexture.getFbo(DirectStateAccess, depth) exposes the
FBO that wraps the main target's color texture.
"""
import sys
from pathlib import Path

OLD_TRY = (
    '\t\ttry(GlStateGuard ignored = GlStateGuard.capture())\n'
    '\t\t{\n'
    '\t\t\tint targetFbo =\n'
    '\t\t\t\tGL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);\n'
    '\t\t\tif(!nvg.beginFrame())\n'
)
NEW_TRY = (
    '\t\ttry(GlStateGuard ignored = GlStateGuard.capture())\n'
    '\t\t{\n'
    '\t\t\tif(!nvg.beginFrame())\n'
)

OLD_COMPOSITE = (
    '\t\t\tRenderTarget target = WURST.MC.getMainRenderTarget();\n'
    '\t\t\tGL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFbo);\n'
    '\t\t\tGL11.glViewport(0, 0, target.width, target.height);\n'
    '\t\t\tnvg.composite();\n'
)
NEW_COMPOSITE = (
    '\t\t\tRenderTarget target = WURST.MC.getMainRenderTarget();\n'
    '\t\t\tint targetFbo = 0;\n'
    '\t\t\ttry\n'
    '\t\t\t{\n'
    '\t\t\t\tGlDevice device = (GlDevice)RenderSystem.getDevice();\n'
    '\t\t\t\tGpuTexture depth = target.getDepthTexture();\n'
    '\t\t\t\ttargetFbo = ((GlTexture)target.getColorTexture()).getFbo(\n'
    '\t\t\t\t\tdevice.directStateAccess(),\n'
    '\t\t\t\t\tdepth instanceof GlTexture ? (GlTexture)depth : null);\n'
    '\t\t\t}catch(Throwable ignored2)\n'
    '\t\t\t{\n'
    '\t\t\t\ttargetFbo = 0;\n'
    '\t\t\t}\n'
    '\t\t\tGL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFbo);\n'
    '\t\t\tGL11.glViewport(0, 0, target.width, target.height);\n'
    '\t\t\tnvg.composite();\n'
)

IMPORTS = (
    'import com.mojang.blaze3d.opengl.GlDevice;\n'
    'import com.mojang.blaze3d.opengl.GlTexture;\n'
)


def patch(project):
    p = Path(project) / 'src/main/java/net/wurstclient/hud2/HudManager.java'
    t = p.read_text(encoding='utf-8')
    if 'GlTexture)target.getColorTexture()' in t:
        print('already', project)
        return
    if OLD_TRY not in t or OLD_COMPOSITE not in t:
        raise SystemExit('anchor missing in %s' % p)
    if 'import com.mojang.blaze3d.opengl.GlDevice;' not in t:
        anchor = 'import com.mojang.blaze3d.pipeline.RenderTarget;\n'
        if anchor not in t:
            raise SystemExit('import anchor missing in %s' % p)
        t = t.replace(anchor, IMPORTS + anchor, 1)
    if 'import com.mojang.blaze3d.systems.RenderSystem;' not in t:
        t = t.replace(IMPORTS + 'import com.mojang.blaze3d.pipeline.RenderTarget;\n',
                      IMPORTS
                      + 'import com.mojang.blaze3d.pipeline.RenderTarget;\n'
                      + 'import com.mojang.blaze3d.systems.RenderSystem;\n',
                      1)
    if 'import com.mojang.blaze3d.textures.GpuTexture;' not in t:
        t = t.replace(
            'import com.mojang.blaze3d.systems.RenderSystem;\n',
            'import com.mojang.blaze3d.systems.RenderSystem;\n'
            'import com.mojang.blaze3d.textures.GpuTexture;\n', 1)
    t = t.replace(OLD_TRY, NEW_TRY, 1)
    t = t.replace(OLD_COMPOSITE, NEW_COMPOSITE, 1)
    p.write_text(t, encoding='utf-8', newline='\n')
    print('patched', project)


if __name__ == '__main__':
    for arg in sys.argv[1:]:
        patch(arg)
