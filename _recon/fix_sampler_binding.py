# -*- coding: utf-8 -*-
"""Unbind Minecraft's sampler object before NanoVG draws.

RenderPearl leaves a sampler bound to texture unit 0; NanoVG's textured text
and image quads then sample black. Unbinding the sampler restores text/images.
"""
import re
import sys
from pathlib import Path


def patch(project):
    root = Path(project) / 'src/main/java/net/wurstclient'
    p = root / 'hud2/nanovg/NanoVGContext.java'
    if not p.exists():
        return
    t = p.read_text(encoding='utf-8')
    if 'GL33.glBindSampler' in t:
        return
    if 'import org.lwjgl.opengl.GL13;' not in t:
        t = t.replace('import org.lwjgl.opengl.GL11;',
                      'import org.lwjgl.opengl.GL11;\n'
                      'import org.lwjgl.opengl.GL13;', 1)
    if 'import org.lwjgl.opengl.GL33;' not in t:
        t = t.replace('import org.lwjgl.opengl.GL30;',
                      'import org.lwjgl.opengl.GL30;\n'
                      'import org.lwjgl.opengl.GL33;', 1)
    anchor = '\t\tGL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer.fbo());\n'
    add = (anchor +
           '\t\tGL13.glActiveTexture(GL13.GL_TEXTURE0);\n'
           '\t\tGL33.glBindSampler(0, 0);\n')
    if anchor not in t:
        raise SystemExit('anchor missing in %s' % p)
    t = t.replace(anchor, add, 1)
    p.write_text(t, encoding='utf-8', newline='\n')
    print('sampler', project)


if __name__ == '__main__':
    for p in sys.argv[1:]:
        patch(p)
