package net.wurstclient.util.render;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
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

	private AsyncTextureLoader() {}

	public static CompletableFuture<ResourceLocation> load(Path file,
		ResourceLocation location)
	{
		ExecutorService decoder = DecoderPool.get();
		CompletableFuture<NativeImage> decoded;
		try
		{
			decoded = CompletableFuture.supplyAsync(() -> {
				try
				{
					return decode(file);
				}catch(IOException e)
				{
					throw new RuntimeException(e);
				}
			}, decoder);

		}catch(RejectedExecutionException e)
		{
			// 队列满时 AbortPolicy 会抛到这里。不能让它穿给 load() 的调用方
			// （有界面在渲染线程上调它），所以转成"这个 future 失败"。
			decoded = new CompletableFuture<>();
			decoded.completeExceptionally(e);
		}

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
			int total = 0;
			while(total < size)
			{
				int read = channel.read(buffer);
				// read 返回 0 表示本次没读到数据，继续等；返回 -1 表示文件比
				// size() 说的短，再读下去会死循环，直接报错。
				if(read < 0)
					throw new IOException("Truncated texture file: " + file);
				total += read;
			}
			buffer.flip();
			return NativeImage.read(buffer);
		}
	}

	public static void shutdown()
	{
		DecoderPool.shutdown();
	}

	/**
	 * 解码线程池的持有者。
	 *
	 * <p>关服时 {@link #shutdown()} 会把池关掉，但 {@link #load(Path,
	 * ResourceLocation)} 之后还可能被调用（例如关服后又被打开的界面），所以池被关掉
	 * 之后按需重建，而不是让调用方永远拿到失败。
	 */
	private static final class DecoderPool
	{
		private static volatile ExecutorService pool = create();

		private static ExecutorService create()
		{
			// 保持 AbortPolicy：不能用 CallerRunsPolicy，队列满时它会让调用方自己
			//（可能是客户端线程）跑完整次解码。队列满的情况由 load() 捕获转成
			// 失败的 future，不会抛给调用方。
			return new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS,
				new ArrayBlockingQueue<>(32), new DecoderThreadFactory(),
				new ThreadPoolExecutor.AbortPolicy());
		}

		private static ExecutorService get()
		{
			ExecutorService current = pool;
			if(!current.isShutdown())
				return current;

			synchronized(DecoderPool.class)
			{
				if(pool.isShutdown())
					pool = create();
				return pool;
			}
		}

		private static void shutdown()
		{
			synchronized(DecoderPool.class)
			{
				pool.shutdownNow();
			}
		}
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
