package net.wurstclient.hud2.compat;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
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
	private static final Map<String, Integer> CACHE = new HashMap<>();
	private static final Set<String> REPORTED = new HashSet<>();

	private HudTextures() {}

	public static int id(String path)
	{
		if(path == null || path.isBlank())
			return -1;

		Integer cached = CACHE.get(path);
		if(cached != null)
			return cached;

		int texture = load(path);
		if(texture <= 0)
		{
			if(REPORTED.add(path))
				System.err.println(
					"[HUD] Failed to load icon texture: " + path);
			return -1;
		}

		CACHE.put(path, texture);
		return texture;
	}

	private static int load(String path)
	{
		ResourceLocation location = ResourceLocation.tryParse(path);
		if(location == null)
			return -1;

		Minecraft minecraft = Minecraft.getInstance();
		if(minecraft == null)
			return -1;

		try(InputStream in = minecraft.getResourceManager().open(location);
			NativeImage image = NativeImage.read(in))
		{
			return upload(image);
		}catch(Exception e)
		{
			return -1;
		}
	}

	private static int upload(NativeImage image)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		if(width <= 0 || height <= 0)
			return -1;

		int[] pixels = image.getPixelsABGR();
		ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4)
			.order(ByteOrder.nativeOrder());
		for(int pixel : pixels)
		{
			buffer.put((byte)(pixel & 0xFF));
			buffer.put((byte)((pixel >> 8) & 0xFF));
			buffer.put((byte)((pixel >> 16) & 0xFF));
			buffer.put((byte)((pixel >> 24) & 0xFF));
		}
		buffer.flip();

		int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
		int texture = GL11.glGenTextures();
		try
		{
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
				GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
				GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
				GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
				GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width,
				height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
		}finally
		{
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
		}

		return texture;
	}

	public static int localPlayerSkin()
	{
		return -1;
	}
}
