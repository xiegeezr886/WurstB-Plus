package net.wurstclient.hud2.compat;

/**
 * 26.3 texture bridge. RenderPearl no longer exposes a raw OpenGL texture id
 * for vanilla textures; NanoVG image drawing is therefore disabled until a
 * GpuTexture-backed path is added. The HUD text and cards are unaffected.
 */
public final class HudTextures
{
	private HudTextures() {}

	public static int id(String path)
	{
		return -1;
	}

	public static int localPlayerSkin()
	{
		return -1;
	}
}
