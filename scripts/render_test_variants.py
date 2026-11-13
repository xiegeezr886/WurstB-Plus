"""Adapt only Minecraft test boundaries to each version's real production API.

Production Java files are always compiled unchanged. Desktop Fabric's inactive
Skia experiments are represented by fail-fast stubs; GLES is not exercised.
"""
from pathlib import Path
import re


def configure(project, original):
    java = project / 'src/main/java/net/wurstclient'
    nano = java / 'hud2/nanovg'
    production = [nano / name for name in (
        'GlStateGuard.java', 'NanoVGContext.java', 'HudFont.java', 'HudFonts.java')]
    test = (Path(__file__).parent / 'tests/java/Render263GlTest.java').read_text(encoding='utf-8')
    blitter = nano / 'NanoVgBlitter.java'
    if blitter.exists():
        production += [blitter, java / 'mixin/NanoVgGuiRenderStateMixin.java']
        accessor = nano / 'GuiGraphicsExtractorAccessor.java'
        if accessor.exists():
            production.append(accessor)
        code = blitter.read_text(encoding='utf-8')
        replacements = []
        if 'com.mojang.blaze3d.opengl' in code:
            replacements += [('com.mojang.renderpearl.backend.opengl', 'com.mojang.blaze3d.opengl'),
                             ('com.mojang.renderpearl.api', 'com.mojang.blaze3d')]
        if 'TextureFormat.RGBA8' in code:
            replacements += [('com.mojang.blaze3d.GpuFormat', 'com.mojang.blaze3d.textures.TextureFormat'),
                             ('RGBA8_UNORM', 'RGBA8')]
        if 'client.gui.render.state' in code:
            replacements.append(('net.minecraft.client.renderer.state.gui', 'net.minecraft.client.gui.render.state'))
        if 'net.wurstclient.util.render.GuiGraphicsExtractor' in code:
            replacements.append(('net.minecraft.client.gui.GuiGraphicsExtractor', 'net.wurstclient.util.render.GuiGraphicsExtractor'))
        if 'submitBlitToCurrentLayer' in code:
            replacements.append(('addGuiElement', 'submitBlitToCurrentLayer'))
        elif 'addBlitToCurrentLayer' in code:
            replacements.append(('addGuiElement', 'addBlitToCurrentLayer'))
        fixtures = {}
        for name, source in original.items():
            for before, after in replacements:
                name = name.replace(before.replace('.', '/'), after.replace('.', '/'))
                source = source.replace(before, after)
            fixtures[name] = source
        for before, after in replacements:
            test = test.replace(before, after)
        if 'TextureFormat.RGBA8' in code:
            fixtures['com/mojang/blaze3d/textures/TextureFormat.java'] = 'package com.mojang.blaze3d.textures; public enum TextureFormat { RGBA8 }'
        if 'net.wurstclient.util.render.GuiGraphicsExtractor' in code:
            name = 'net/wurstclient/util/render/GuiGraphicsExtractor.java'
            fixtures[name] = fixtures[name].replace('package net.minecraft.client.gui;', 'package net.wurstclient.util.render;')
            state = 'net.minecraft.client.gui.render.state.GuiRenderState'
            fixtures[name] = fixtures[name].replace('public int guiWidth()', f'public {state} getRenderState() {{ return guiRenderState; }} public int guiWidth()')
        if 'GpuSampler' not in code:
            name = 'net/minecraft/client/gui/render/TextureSetup.java'
            fixtures[name] = fixtures[name].replace(',com.mojang.blaze3d.textures.GpuSampler s', '')
            name = 'com/mojang/blaze3d/textures/GpuTexture.java'
            fixtures[name] = fixtures[name].replace('public void close()', 'public void setAddressMode(AddressMode a) {} public void setTextureFilter(FilterMode f,boolean b) {} public void close()')
    else:
        # Keep common state, initialization, real text and failed-init tests.
        test = test[:test.index('\tprivate static int pixel(')] + test[test.index('\tprivate static void testGuard()'):]
        start = test.index('\t\tGuiRenderState state =')
        end = test.index('\t\tctx.destroy();', start)
        test = test[:start] + test[end:]
        test = '\n'.join(line for line in test.splitlines() if not line.startswith((
            'import com.mojang.', 'import net.minecraft.client.gui.',
            'import net.minecraft.client.renderer.state.')))
        test = test.replace('ctx.endFrame();', 'int framebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING); ctx.endFrame();')
        test = test.replace('ctx.getFramebufferFbo()', 'framebuffer')
        fixtures = {name: source for name, source in original.items() if name.startswith((
            'net/minecraft/client/Minecraft.', 'net/wurstclient/'))}
        fixtures['com/mojang/blaze3d/systems/RenderSystem.java'] = 'package com.mojang.blaze3d.systems; public class RenderSystem { public static void assertOnRenderThread() {} }'
        if 'SkiaHudSurface' in (nano / 'NanoVGContext.java').read_text(encoding='utf-8'):
            package = 'package net.wurstclient.hud2.nanovg; '
            fail = 'throw new AssertionError("inactive Skia path called on desktop");'
            methods = [
                'boolean isActive()', 'void fillRect(float x,float y,float w,float h,int c)',
                'void fillRoundRect(float x,float y,float w,float h,float r,int c)',
                'void strokeRoundRect(float x,float y,float w,float h,float r,float s,int c)',
                'void drawCircle(float x,float y,float r,int c)',
                'void drawLine(float x,float y,float a,float b,float s,int c)',
                'void drawText(String t,float x,float y,float s,int c,HudFont f)']
            fixtures['net/wurstclient/hud2/nanovg/SkiaHudSurface.java'] = package + 'public class SkiaHudSurface { public static SkiaHudSurface get() {' + fail + '} ' + ' '.join('public ' + m + ' {' + fail + '}' for m in methods) + '}'
            fixtures['net/wurstclient/hud2/nanovg/SkiaTextCache.java'] = package + 'public class SkiaTextCache { public static void queue(String t,float x,float y,int c,float s,HudFont f) {' + fail + '} }'
            fixtures['net/wurstclient/hud2/nanovg/SoftwareText.java'] = package + 'public class SoftwareText { public static boolean draw(NanoVGContext ctx,String t,float x,float y,int c,float s,HudFont f) {' + fail + '} }'
    manager = re.search(r'import (com\.mojang\.[\w.]+\.GlStateManager);', (nano / 'GlStateGuard.java').read_text(encoding='utf-8')).group(1)
    fixtures[manager.replace('.', '/') + '.java'] = 'package ' + manager.rsplit('.', 1)[0] + ''';
public class GlStateManager {
 private static int active;
 private static final int[] textures=new int[32];
 public static void _activeTexture(int unit) { int index=unit-org.lwjgl.opengl.GL13.GL_TEXTURE0; if(active!=index) { active=index; org.lwjgl.opengl.GL13.glActiveTexture(unit); } }
 public static void _bindTexture(int id) { if(textures[active]!=id) { textures[active]=id; org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,id); } }
 public static int boundTexture(int unit) { return textures[unit]; }
}'''
    test = 'import ' + manager + ';\n' + test
    return fixtures, test, production
