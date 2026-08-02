package net.wurstclient.util.render;

import java.nio.ByteBuffer;

public final class ThreadLocalPixelBuffer
{
	private static final int DEFAULT_CAPACITY = 4 * 1024 * 1024;
	private static final ThreadLocal<ByteBuffer> BUFFERS =
		ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(DEFAULT_CAPACITY));

	private ThreadLocalPixelBuffer() {}

	public static ByteBuffer acquire(int requiredCapacity)
	{
		if(requiredCapacity < 0)
			throw new IllegalArgumentException("Negative buffer size");

		ByteBuffer buffer = BUFFERS.get();
		if(buffer.capacity() < requiredCapacity)
		{
			int capacity = Math.max(DEFAULT_CAPACITY,
				Integer.highestOneBit(requiredCapacity - 1) << 1);
			buffer = ByteBuffer.allocateDirect(capacity);
			BUFFERS.set(buffer);
		}
		buffer.clear();
		buffer.limit(requiredCapacity);
		return buffer;
	}

	public static void releaseForCurrentThread()
	{
		BUFFERS.remove();
	}
}
