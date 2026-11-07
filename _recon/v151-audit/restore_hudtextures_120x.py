"""Restore the original (working) HudTextures for 1.20.2-1.20.6 builds."""
import os

ROOT = r"C:\Users\ui863\Documents\trae_projects\VAP"
PKG = os.path.join("main", "java", "net", "wurstclient")
REL = os.path.join(PKG, "hud2", "compat", "HudTextures.java")

OLD = """/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.wurstclient.WurstClient;

/**
 * Version-specific bridge from a texture path to the OpenGL texture id that
 * NanoVG needs.
 *
 * <p>1.20.1 keeps raw texture ids on {@link AbstractTexture}. Newer versions
 * replace this file with a GpuTexture-based implementation.
 */
public final class HudTextures
{
	private HudTextures() {}

	public static int id(String path)
	{
		if(path == null || path.isBlank())
			return -1;

		try
		{
			ResourceLocation location = new ResourceLocation(path);
			AbstractTexture texture = Minecraft.getInstance()
				.getTextureManager().getTexture(location);
			return texture == null ? -1 : texture.getId();
		}catch(Exception e)
		{
			return -1;
		}
	}

	public static int localPlayerSkin()
	{
		if(WurstClient.MC.player == null)
			return -1;
		return id(WurstClient.MC.player.getSkin().texture().toString());
	}
}
"""

VERSIONS = {"1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6"}


def read(path):
    data = open(path, "rb").read()
    bom = data.startswith(b"\xef\xbb\xbf")
    if bom:
        data = data[3:]
    eol = b"\r\n" if data.count(b"\r\n") > data.count(b"\n") / 2 else b"\n"
    return eol, bom


def write(path, text, eol, bom):
    out = text.replace("\n", "\r\n").encode("utf-8") if eol == b"\r\n" \
        else text.encode("utf-8")
    if bom:
        out = b"\xef\xbb\xbf" + out
    open(path, "wb").write(out)


def main():
    roots = []
    for base in ("versions", os.path.join("fabric", "versions"),
                 os.path.join("neoforge", "versions")):
        path = os.path.join(ROOT, base)
        for name in sorted(os.listdir(path)):
            if name in VERSIONS:
                roots.append(os.path.join(path, name, "src"))

    restored = 0
    for root in roots:
        path = os.path.join(root, REL)
        if not os.path.isfile(path):
            continue
        eol, bom = read(path)
        write(path, OLD, eol, bom)
        restored += 1
    print("restored:", restored)


if __name__ == "__main__":
    main()
