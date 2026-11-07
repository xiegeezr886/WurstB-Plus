"""Replace the stub HudTextures in 1.21+ builds with a real PNG -> GL texture bridge."""
import os
import sys

ROOT = r"C:\Users\ui863\Documents\trae_projects\VAP"
PKG = os.path.join("main", "java", "net", "wurstclient")
REL = os.path.join(PKG, "hud2", "compat", "HudTextures.java")

TEMPLATE = """package net.wurstclient.hud2.compat;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.%IDENTIFIER%;
/**
 * Version-specific bridge from a texture path to the OpenGL texture id that
 * NanoVG needs.
 *
 * <p>1.21+ no longer exposes raw texture ids on {@code AbstractTexture}, so the
 * PNG is decoded with {@link NativeImage} and uploaded into a small private GL
 * texture the first time it is used. NanoVG wraps that id in a cached image.
 */
public final class HudTextures
{
\tprivate static final Map<String, Integer> CACHE = new HashMap<>();
\tprivate static final Set<String> REPORTED = new HashSet<>();

\tprivate HudTextures() {}

\tpublic static int id(String path)
\t{
\t\tif(path == null || path.isBlank())
\t\t\treturn -1;

\t\tInteger cached = CACHE.get(path);
\t\tif(cached != null)
\t\t\treturn cached;

\t\tint texture = load(path);
\t\tif(texture <= 0)
\t\t{
\t\t\tif(REPORTED.add(path))
\t\t\t\tSystem.err.println(
\t\t\t\t\t"[HUD] Failed to load icon texture: " + path);
\t\t\treturn -1;
\t\t}

\t\tCACHE.put(path, texture);
\t\treturn texture;
\t}

\tprivate static int load(String path)
\t{
\t\t%IDENTIFIER% location = %IDENTIFIER%.tryParse(path);
\t\tif(location == null)
\t\t\treturn -1;

\t\tMinecraft minecraft = Minecraft.getInstance();
\t\tif(minecraft == null)
\t\t\treturn -1;

\t\ttry(InputStream in = minecraft.getResourceManager().open(location);
\t\t\tNativeImage image = NativeImage.read(in))
\t\t{
\t\t\treturn upload(image);
\t\t}catch(Exception e)
\t\t{
\t\t\treturn -1;
\t\t}
\t}

\tprivate static int upload(NativeImage image)
\t{
\t\tint width = image.getWidth();
\t\tint height = image.getHeight();
\t\tif(width <= 0 || height <= 0)
\t\t\treturn -1;

\t\tint[] pixels = image.getPixelsABGR();
\t\tByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4)
\t\t\t.order(ByteOrder.nativeOrder());
\t\tfor(int pixel : pixels)
\t\t{
\t\t\tbuffer.put((byte)(pixel & 0xFF));
\t\t\tbuffer.put((byte)((pixel >> 8) & 0xFF));
\t\t\tbuffer.put((byte)((pixel >> 16) & 0xFF));
\t\t\tbuffer.put((byte)((pixel >> 24) & 0xFF));
\t\t}
\t\tbuffer.flip();

\t\tint previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
\t\tint texture = GL11.glGenTextures();
\t\ttry
\t\t{
\t\t\tGL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
\t\t\tGL11.glTexParameteri(GL11.GL_TEXTURE_2D,
\t\t\t\tGL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
\t\t\tGL11.glTexParameteri(GL11.GL_TEXTURE_2D,
\t\t\t\tGL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
\t\t\tGL11.glTexParameteri(GL11.GL_TEXTURE_2D,
\t\t\t\tGL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP_TO_EDGE);
\t\t\tGL11.glTexParameteri(GL11.GL_TEXTURE_2D,
\t\t\t\tGL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP_TO_EDGE);
\t\t\tGL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width,
\t\t\t\theight, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
\t\t}finally
\t\t{
\t\t\tGL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
\t\t}

\t\treturn texture;
\t}

\tpublic static int localPlayerSkin()
\t{
\t\treturn -1;
\t}
}
"""


def roots():
    result = []
    for base in ("versions", os.path.join("fabric", "versions"),
                 os.path.join("neoforge", "versions")):
        path = os.path.join(ROOT, base)
        if os.path.isdir(path):
            for name in sorted(os.listdir(path)):
                result.append(os.path.join(path, name, "src"))
    return [r for r in result if os.path.isdir(os.path.join(r, PKG))]


def read(path):
    data = open(path, "rb").read()
    bom = data.startswith(b"\xef\xbb\xbf")
    if bom:
        data = data[3:]
    eol = b"\r\n" if data.count(b"\r\n") > data.count(b"\n") / 2 else b"\n"
    return data.replace(b"\r\n", b"\n").decode("utf-8"), eol, bom


def write(path, text, eol, bom):
    out = text.replace("\n", "\r\n").encode("utf-8") if eol == b"\r\n" \
        else text.encode("utf-8")
    if bom:
        out = b"\xef\xbb\xbf" + out
    open(path, "wb").write(out)


def main():
    apply = "--apply" in sys.argv
    patched = 0
    for root in roots():
        path = os.path.join(root, REL)
        if not os.path.isfile(path):
            continue
        text, eol, bom = read(path)
        if "NativeImage" in text:
            continue
        if "return -1;" not in text or "class HudTextures" not in text:
            continue

        title = os.path.join(root, PKG, "gui", "title", "WurstTitleMenu.java")
        identifier = "ResourceLocation"
        if os.path.isfile(title):
            head = open(title, encoding="utf-8", errors="replace").read()
            if "net.minecraft.resources.Identifier;" in head:
                identifier = "Identifier"

        new_text = TEMPLATE.replace("%IDENTIFIER%", identifier)
        if apply:
            write(path, new_text, eol, bom)
        patched += 1

    print(f"HudTextures {'patched' if apply else 'to patch'}: {patched}")


if __name__ == "__main__":
    main()
