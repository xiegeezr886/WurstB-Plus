package net.wurstclient.render.skia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

/**
 * Tests the row packing of the Skia region upload.
 *
 * <p>
 * The upload used to pass {@code rowBytes / 4} as
 * {@code GL_UNPACK_ROW_LENGTH}, which is only correct when Skia's rows happen
 * to carry no padding. When they do, the driver reads past the end of a row and
 * the NVIDIA OpenGL driver crashed on it. These tests pin the packing that
 * replaced that assumption.
 */
final class SkiaRegionRendererTest
{
	private static final int WIDTH = 3;
	private static final int HEIGHT = 2;
	private static final int ROW_BYTES = WIDTH * 4;

	/** Rows packed tightly already must come through byte for byte. */
	@Test
	void keepsTightRowsUnchanged()
	{
		ByteBuffer source = MemoryUtil.memAlloc(ROW_BYTES * HEIGHT);

		try
		{
			for(int i = 0; i < ROW_BYTES * HEIGHT; i++)
				source.put(i, (byte)(i + 1));

			ByteBuffer packed = SkiaRegionRenderer.packRows(
				MemoryUtil.memAddress(source), ROW_BYTES, WIDTH, HEIGHT);

			try
			{
				assertEquals(ROW_BYTES * HEIGHT, packed.remaining());

				for(int i = 0; i < ROW_BYTES * HEIGHT; i++)
					assertEquals((byte)(i + 1), packed.get(i), "字节 " + i);
			}finally
			{
				MemoryUtil.memFree(packed);
			}
		}finally
		{
			MemoryUtil.memFree(source);
		}
	}

	/**
	 * Padding between rows must be dropped, so the driver gets exactly
	 * {@code width * 4} bytes per row.
	 */
	@Test
	void dropsPaddingBetweenRows()
	{
		int padding = 8;
		int paddedRowBytes = ROW_BYTES + padding;
		ByteBuffer source = MemoryUtil.memAlloc(paddedRowBytes * HEIGHT);

		try
		{
			for(int row = 0; row < HEIGHT; row++)
			{
				byte pixel = (byte)(row == 0 ? 0x11 : 0x22);

				for(int i = 0; i < ROW_BYTES; i++)
					source.put(row * paddedRowBytes + i, pixel);

				for(int i = 0; i < padding; i++)
					source.put(row * paddedRowBytes + ROW_BYTES + i,
						(byte)0xEE);
			}

			ByteBuffer packed = SkiaRegionRenderer.packRows(
				MemoryUtil.memAddress(source), paddedRowBytes, WIDTH, HEIGHT);

			try
			{
				assertEquals(ROW_BYTES * HEIGHT, packed.remaining(),
					"打散后的长度必须正好是 宽 × 4 × 高");

				for(int row = 0; row < HEIGHT; row++)
				{
					byte expected = (byte)(row == 0 ? 0x11 : 0x22);

					for(int i = 0; i < ROW_BYTES; i++)
						assertEquals(expected,
							packed.get(row * ROW_BYTES + i),
							"行 " + row + " 字节 " + i);
				}

				for(int i = 0; i < packed.remaining(); i++)
					assertNotEquals((byte)0xEE, packed.get(i),
						"填充字节混进来了：" + i);
			}finally
			{
				MemoryUtil.memFree(packed);
			}
		}finally
		{
			MemoryUtil.memFree(source);
		}
	}
}
