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
					image.getHeight());
			});
		}catch(Exception ignored)
		{}
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

	record Texture(ResourceLocation location, int width, int height)
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
