package net.wurstclient.util.render;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.wurstclient.WurstClient;

public final class AsyncTextureLoader
{
	private static final int MAX_FILE_SIZE = 64 * 1024 * 1024;
	private static final ExecutorService DECODER = new ThreadPoolExecutor(1, 2,
		30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), new DecoderThreadFactory(),
		new ThreadPoolExecutor.AbortPolicy());

	private AsyncTextureLoader() {}

	public static CompletableFuture<ResourceLocation> load(Path file,
		ResourceLocation location)
	{
		CompletableFuture<NativeImage> decoded = CompletableFuture.supplyAsync(() -> {
			try
			{
				return decode(file);
			}catch(IOException e)
			{
				throw new RuntimeException(e);
			}
		}, DECODER);
		CompletableFuture<ResourceLocation> result = new CompletableFuture<>();
		decoded.whenComplete((image, error) -> {
			if(error != null)
			{
				result.completeExceptionally(error);
				return;
			}
			WurstClient.MC.execute(() -> {
				try
				{
					WurstClient.MC.getTextureManager().register(location,
						new DynamicTexture(image));
					result.complete(location);
				}catch(Throwable uploadError)
				{
					image.close();
					result.completeExceptionally(uploadError);
				}
			});
		});
		return result;
	}

	private static NativeImage decode(Path file) throws IOException
	{
		try(FileChannel channel = FileChannel.open(file, StandardOpenOption.READ))
		{
			long size = channel.size();
			if(size <= 0 || size > MAX_FILE_SIZE)
				throw new IOException("Unsupported texture file size: " + size);
			ByteBuffer buffer = ThreadLocalPixelBuffer.acquire((int)size);
			while(buffer.hasRemaining() && channel.read(buffer) >= 0)
			{}
			buffer.flip();
			return NativeImage.read(buffer);
		}
	}

	public static void shutdown()
	{
		DECODER.shutdownNow();
	}

	private static final class DecoderThreadFactory implements ThreadFactory
	{
		private int index;

		@Override
		public Thread newThread(Runnable task)
		{
			Thread thread = new Thread(task,
				"WurstB-Texture-Decoder-" + ++index);
			thread.setDaemon(true);
			return thread;
		}
	}
}
