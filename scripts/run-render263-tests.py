#!/usr/bin/env python3
"""Exercise production NanoVG rendering on desktop OpenGL (all v1.5.1 APIs).

Requires a desktop OpenGL session and JDK 21 or 25. Creates a hidden GLFW window;
Minecraft GPU/GUI objects are recording fixtures. The production context, fonts,
state guard, blitter and reset callback run unmodified. This does not launch
Minecraft or verify Mixin weaving. Dependencies are cached under build/.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import urllib.error
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]

FIXTURES = {
    'com/mojang/blaze3d/systems/RenderSystem.java': """package com.mojang.blaze3d.systems;
public class RenderSystem {
 public static void assertOnRenderThread() {}
 public static Device getDevice() { return new Device(); }
 public static class Device {
  public com.mojang.renderpearl.api.textures.GpuTexture createTexture(String n,int u,com.mojang.renderpearl.api.GpuFormat f,int w,int h,int d,int m) { return new com.mojang.renderpearl.backend.opengl.GlTexture(w,h); }
  public com.mojang.renderpearl.api.textures.GpuTextureView createTextureView(com.mojang.renderpearl.api.textures.GpuTexture t) { return new com.mojang.renderpearl.api.textures.GpuTextureView(t); }
  public com.mojang.renderpearl.api.textures.GpuSampler createSampler(com.mojang.renderpearl.api.textures.AddressMode a,com.mojang.renderpearl.api.textures.AddressMode b,com.mojang.renderpearl.api.textures.FilterMode c,com.mojang.renderpearl.api.textures.FilterMode d,int e,java.util.OptionalDouble f) { return new com.mojang.renderpearl.api.textures.GpuSampler(); }
 }
}""",
    'com/mojang/renderpearl/api/GpuFormat.java': """package com.mojang.renderpearl.api; public enum GpuFormat { RGBA8_UNORM }""",
    'com/mojang/renderpearl/api/textures/AddressMode.java': """package com.mojang.renderpearl.api.textures; public enum AddressMode { CLAMP_TO_EDGE }""",
    'com/mojang/renderpearl/api/textures/FilterMode.java': """package com.mojang.renderpearl.api.textures; public enum FilterMode { LINEAR }""",
    'com/mojang/renderpearl/api/textures/GpuSampler.java': """package com.mojang.renderpearl.api.textures; public class GpuSampler { public void close() {} }""",
    'com/mojang/renderpearl/api/textures/GpuTexture.java': """package com.mojang.renderpearl.api.textures; public class GpuTexture { public static final int USAGE_COPY_DST=1, USAGE_TEXTURE_BINDING=2; public void close() {} }""",
    'com/mojang/renderpearl/api/textures/GpuTextureView.java': """package com.mojang.renderpearl.api.textures; public class GpuTextureView { public final GpuTexture texture; public GpuTextureView(GpuTexture t) { texture=t; } public void close() {} }""",
    'com/mojang/renderpearl/backend/opengl/GlTexture.java': """package com.mojang.renderpearl.backend.opengl;
import org.lwjgl.opengl.GL11;
public class GlTexture extends com.mojang.renderpearl.api.textures.GpuTexture {
 public static int created,closed; public final int width,height; private boolean dead; private final int id=GL11.glGenTextures();
 public GlTexture(int w,int h) { width=w; height=h; created++; int old=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D); GL11.glBindTexture(GL11.GL_TEXTURE_2D,id); GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,w,h,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(java.nio.ByteBuffer)null); GL11.glBindTexture(GL11.GL_TEXTURE_2D,old); }
 public int glId() { return id; }
 public void close() { if(dead) throw new AssertionError("double close"); dead=true; closed++; GL11.glDeleteTextures(id); }
}""",
    'net/minecraft/client/gui/GuiGraphicsExtractor.java': """package net.minecraft.client.gui;
public class GuiGraphicsExtractor {
 private final net.minecraft.client.renderer.state.gui.GuiRenderState guiRenderState;
 public GuiGraphicsExtractor(net.minecraft.client.renderer.state.gui.GuiRenderState s) { guiRenderState=s; }
 public int guiWidth() { return 2; } public int guiHeight() { return 2; }
}""",
    'net/minecraft/client/gui/render/TextureSetup.java': """package net.minecraft.client.gui.render;
public record TextureSetup(com.mojang.renderpearl.api.textures.GpuTextureView view) {
 public static TextureSetup singleTexture(com.mojang.renderpearl.api.textures.GpuTextureView v,com.mojang.renderpearl.api.textures.GpuSampler s) { return new TextureSetup(v); }
}""",
    'net/minecraft/client/renderer/RenderPipelines.java': """package net.minecraft.client.renderer; public class RenderPipelines { public static final Object GUI_TEXTURED=new Object(); }""",
    'net/minecraft/client/renderer/state/gui/BlitRenderState.java': """package net.minecraft.client.renderer.state.gui;
public record BlitRenderState(Object pipeline,net.minecraft.client.gui.render.TextureSetup texture,org.joml.Matrix3x2f pose,int x0,int y0,int x1,int y1,float u0,float u1,float v0,float v1,int color,Object scissor) {}""",
    'net/minecraft/client/renderer/state/gui/GuiRenderState.java': """package net.minecraft.client.renderer.state.gui;
public class GuiRenderState extends net.wurstclient.mixin.NanoVgGuiRenderStateMixin {
 public final java.util.List<BlitRenderState> elements=new java.util.ArrayList<>();
 public void addGuiElement(BlitRenderState s) { elements.add(s); }
 public void reset() { elements.clear(); try { var m=net.wurstclient.mixin.NanoVgGuiRenderStateMixin.class.getDeclaredMethod("wurst$releaseSnapshots",org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class); m.setAccessible(true); m.invoke(this,new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("reset",false)); } catch(Exception e) { throw new RuntimeException(e); } }
}""",
    'net/minecraft/client/Minecraft.java': """package net.minecraft.client;
public class Minecraft { public static final Window window=new Window(); public Window getWindow() { return window; } public static class Window { public int width=320; public int getWidth() { return width; } public int getHeight() { return 160; } public double getGuiScale() { return 1; } } }""",
    'net/wurstclient/WurstClient.java': """package net.wurstclient; public class WurstClient { public static net.minecraft.client.Minecraft MC=new net.minecraft.client.Minecraft(); }""",
    'net/wurstclient/hud2/compat/HudTextures.java': """package net.wurstclient.hud2.compat; public class HudTextures { public static int id(String path) { return 0; } }""",
    'net/wurstclient/hud2/nanovg/NanoVGNativeLoader.java': """package net.wurstclient.hud2.nanovg; public class NanoVGNativeLoader { public static boolean ensureLoaded() { return true; } }""",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', default='fabric/versions/26.3')
    parser.add_argument('--java-home', type=Path, default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--java-release', choices=('21', '25'), default='21')
    parser.add_argument('--lwjgl-version', default='3.3.3')
    args = parser.parse_args()
    suffix = '.exe' if os.name == 'nt' else ''
    java = str(args.java_home.resolve() / 'bin' / ('java' + suffix)) if args.java_home else shutil.which('java')
    javac = str(args.java_home.resolve() / 'bin' / ('javac' + suffix)) if args.java_home else shutil.which('javac')
    if not java or not javac:
        parser.error('JDK 21+ is required; pass --java-home')
    work = ROOT / 'build/render263-tests' / args.project.replace('/', '-')
    src, classes = work / 'src', work / 'classes'
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir(parents=True, exist_ok=True)
    base = ROOT / args.project
    from render_test_variants import configure
    fixtures, test_source, production = configure(base, FIXTURES)
    if src.exists():
        shutil.rmtree(src)
    for name, text in fixtures.items():
        path = src / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding='utf-8')
    java_base = base / 'src/main/java/net/wurstclient'
    if (java_base / 'hud2/nanovg/NanoVgBlitter.java').exists():
        config = base / 'src/main/resources' / ('wurstpenguin.mixins.json' if args.project.replace('\\', '/').startswith('fabric') else 'wurst.mixins.json')
        assert 'NanoVgGuiRenderStateMixin' in json.loads(config.read_text(encoding='utf-8'))['client'], 'reset callback must be registered'
        renderer = (java_base / 'mixin/GameRendererMixin.java').read_text(encoding='utf-8')
        assert 'method = "close()V"' in renderer and 'NanoVgBlitter.close()' in renderer, 'shutdown must release textures'
    test_path = src / 'Render263GlTest.java'
    test_path.write_text(test_source, encoding='utf-8')
    deps = ROOT / 'build/render263-tests/deps'
    deps.mkdir(parents=True, exist_ok=True)
    cache = ROOT / '.dsh-tmp/gradle-home/caches/modules-2/files-2.1'
    arch = platform.machine().lower()
    arm = arch in ('aarch64', 'arm64')
    classifier = ('natives-windows' if os.name == 'nt' else 'natives-macos' if platform.system() == 'Darwin' else 'natives-linux') + ('-arm64' if arm else '')
    jars = []
    for module in ('lwjgl', 'lwjgl-opengl', 'lwjgl-glfw', 'lwjgl-nanovg'):
        for variant in ('', '-' + classifier):
            name = module + '-' + args.lwjgl_version + variant + '.jar'
            cached = list((cache / 'org.lwjgl' / module / args.lwjgl_version).glob('*/' + name))
            path = cached[0] if cached else deps / name
            if not path.exists():
                relative = 'org/lwjgl/' + module + '/' + args.lwjgl_version + '/' + name
                try:
                    response = urllib.request.urlopen('https://libraries.minecraft.net/' + relative, timeout=45)
                except urllib.error.HTTPError as error:
                    if error.code != 404:
                        raise
                    response = urllib.request.urlopen('https://repo.maven.apache.org/maven2/' + relative, timeout=45)
                with response:
                    path.write_bytes(response.read())
            jars.append(path)
    for name, url in (
        ('joml-1.10.9.jar', 'https://repo.maven.apache.org/maven2/org/joml/joml/1.10.9/joml-1.10.9.jar'),
        ('sponge-mixin.jar', 'https://maven.fabricmc.net/net/fabricmc/sponge-mixin/0.17.4+mixin.0.8.7/sponge-mixin-0.17.4+mixin.0.8.7.jar')):
        path = deps / name
        if not path.exists():
            with urllib.request.urlopen(url, timeout=45) as response:
                path.write_bytes(response.read())
        jars.append(path)
    cp = os.pathsep.join(map(str, jars))
    # Explicit extraction avoids the Windows runtime's shared-temp writable
    # check and keeps concurrently tested LWJGL versions isolated.
    native_dir = work / 'natives' / args.lwjgl_version
    native_dir.mkdir(parents=True, exist_ok=True)
    for jar in jars:
        if classifier not in jar.name:
            continue
        with zipfile.ZipFile(jar) as archive:
            for name in archive.namelist():
                if name.endswith(('.dll', '.so', '.dylib')):
                    target = native_dir / Path(name).name
                    data = archive.read(name)
                    if not target.exists() or target.read_bytes() != data:
                        target.write_bytes(data)
    subprocess.run([javac, '-proc:none', '-encoding', 'UTF-8', '--release', args.java_release, '-cp', cp, '-d', str(classes), *map(str, src.rglob('*.java')), *map(str, production)], check=True)
    result = subprocess.run([java, '--enable-native-access=ALL-UNNAMED', '-Djava.awt.headless=true', '-Dorg.lwjgl.librarypath=' + str(native_dir), '-Djava.library.path=' + str(native_dir), '-cp', str(classes) + os.pathsep + cp + os.pathsep + str(base / 'src/main/resources'), 'Render263GlTest'], text=True, capture_output=True, cwd=work)
    (work / 'result.txt').write_text(result.stdout + result.stderr, encoding='utf-8')
    (work / 'inputs.json').write_text(json.dumps({
        'project': args.project, 'lwjgl': args.lwjgl_version, 'java_release': args.java_release, 'exit_code': result.returncode,
        'production_sha256': {str(p.relative_to(ROOT)).replace('\\', '/'): hashlib.sha256(p.read_bytes()).hexdigest() for p in production},
    }, indent=2), encoding='utf-8')
    print(result.stdout + result.stderr)
    result.check_returncode()


if __name__ == '__main__':
    main()
