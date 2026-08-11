package net.wurstclient.clickgui2.screens;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

final class NeteaseImageCache implements AutoCloseable
{
	private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(8))
		.followRedirects(HttpClient.Redirect.NORMAL).build();

	private final Map<String, Entry> entries = new ConcurrentHashMap<>();
	private volatile boolean closed;

	Texture get(String url)
	{
		if(closed || url == null || url.isBlank())
			return null;
		Entry entry = entries.computeIfAbsent(url, this::load);
		return entry.texture;
	}

	boolean isClosed()
	{
		return closed;
	}

	private Entry load(String url)
	{
		Entry entry = new Entry(new ResourceLocation("wurst", "netease/"
			+ Integer.toUnsignedString(url.hashCode(), 16)));
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(15)).header("User-Agent",
				"Mozilla/5.0 WurstBPlus/1.6 NeteaseMusic").GET().build();
		HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
			.thenAccept(response -> decode(entry, response)).exceptionally(error -> null);
		return entry;
	}

	private void decode(Entry entry, HttpResponse<byte[]> response)
	{
		byte[] bytes = response.body();
		if(response.statusCode() / 100 != 2 || bytes == null || bytes.length == 0
			|| bytes.length > MAX_IMAGE_BYTES || closed)
			return;
		try
		{
			NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
			Minecraft client = Minecraft.getInstance();
			client.execute(() -> {
				if(closed)
				{
					image.close();
					return;
				}
				DynamicTexture dynamicTexture = new DynamicTexture(image);
				dynamicTexture.setFilter(true, false);
				client.getTextureManager().register(entry.location, dynamicTexture);
				entry.texture = new Texture(entry.location, image.getWidth(),
					image.getHeight(), sampleAccent(image));
			});
		}catch(Exception ignored)
		{}
	}

	private static final int DEFAULT_ACCENT = 0xFFEC4141;

	private static int sampleAccent(NativeImage image)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		if(width <= 0 || height <= 0)
			return DEFAULT_ACCENT;
		int stepX = Math.max(1, width / 8);
		int stepY = Math.max(1, height / 8);
		int best = DEFAULT_ACCENT;
		float bestScore = -1;
		for(int y = stepY / 2; y < height; y += stepY)
			for(int x = stepX / 2; x < width; x += stepX)
			{
				int pixel = image.getPixelRGBA(x, y);
				if(pixel >>> 24 < 160)
					continue;
				int red = pixel & 0xFF;
				int green = pixel >>> 8 & 0xFF;
				int blue = pixel >>> 16 & 0xFF;
				float max = Math.max(red, Math.max(green, blue));
				float min = Math.min(red, Math.min(green, blue));
				float luminance = (0.299F * red + 0.587F * green
					+ 0.114F * blue) / 255F;
				if(luminance < 0.14F || luminance > 0.9F)
					continue;
				float saturation = max == 0 ? 0 : (max - min) / max;
				float score = saturation
					* (1F - Math.abs(luminance - 0.45F) * 1.4F);
				if(score > bestScore)
				{
					bestScore = score;
					best = 0xFF000000 | red << 16 | green << 8 | blue;
				}
			}
		return best;
	}

	@Override
	public void close()
	{
		closed = true;
		Minecraft client = Minecraft.getInstance();
		for(Entry entry : entries.values())
			if(entry.texture != null)
				client.getTextureManager().release(entry.location);
		entries.clear();
	}

	record Texture(ResourceLocation location, int width, int height, int accent)
	{}

	private static final class Entry
	{
		private final ResourceLocation location;
		private volatile Texture texture;

		private Entry(ResourceLocation location)
		{
			this.location = location;
		}
	}
}
