/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * {@link WeTexture} 的容器族：{@code TEXB0001/0002/0003/0004}。
 *
 * <p>
 * 容器名之后的 int32 **个数随版本变**：0001/0002 是 7 个、0003 是 8 个（多一个
 * freeImageFormat）、0004 是 9 个（再多一个 isVideoMp4）。字段顺序是
 * imageCount、[freeImageFormat]、[isVideoMp4]、mipmapCount、width、height、
 * compression、uncompressedSize、compressedSize —— 来自参考实现
 * {@code linux-wallpaperengine} 的 {@code Data/Parsers/TextureParser.cpp}，
 * 且实测像素起点 83/87/91 与 7/8/9 个 int32 逐一对上。
 * </p>
 */
final class WeTextureContainerVariantTest
{
	/** 最小可辨认的 PNG 头：尺寸由头部单独给，解析器不看载荷内容。 */
	private static final byte[] PNG = {(byte)0x89, 'P', 'N', 'G', 0x0D, 0x0A,
		0x1A, 0x0A, 1, 2, 3, 4, 5, 6, 7, 8};

	@Test
	void readsEveryContainerVersion() throws Exception
	{
		for(String container : new String[]{"TEXB0001", "TEXB0002",
			"TEXB0003", "TEXB0004"})
		{
			int fieldCount = container.endsWith("0004") ? 9
				: container.endsWith("0003") ? 8 : 7;
			WeTexture texture =
				WeTexture.parse(texture(container, fieldCount, 0));

			assertEquals(container, texture.container());
			assertEquals(64, texture.imageWidth(), container);
			assertEquals(64, texture.imageHeight(), container);
			assertArrayEquals(PNG, texture.payload(),
				container + " 的载荷应当整段取出来");
		}
	}

	/** 没见过的容器名要明确拒绝，不能瞎猜布局。 */
	@Test
	void refusesAnUnknownContainer()
	{
		assertThrows(IOException.class,
			() -> WeTexture.parse(texture("TEXB0099", 8, 0)));
	}

	/** 压缩方式只认得 0（原样）与 1（LZ4），别的要报出来而不是当没压缩。 */
	@Test
	void refusesAnUnknownCompression()
	{
		IOException error = assertThrows(IOException.class,
			() -> WeTexture.parse(texture("TEXB0003", 8, 2)));

		assertTrue(error.getMessage().contains("压缩"),
			"报错要说明是压缩方式的问题，实际：" + error.getMessage());
	}

	/**
	 * {@code compression == 1} 时走 LZ4：载荷是压缩流，解出来必须恰好是
	 * uncompressedSize 字节。
	 */
	@Test
	void inflatesLz4Mipmaps() throws Exception
	{
		// 让输出是 10 个 'a'：字面量 1 个 'a' + 回引 offset=1、长度 9
		byte[] stream = {(byte)0x15, 'a', 1, 0};

		WeTexture texture = parseWithLz4(stream, 10);

		assertEquals(10, texture.payload().length);
		assertArrayEquals("aaaaaaaaaa".getBytes(StandardCharsets.ISO_8859_1),
			texture.payload());
	}

	/**
	 * TEXB0004 的 **mp4 贴图**：isVideoMp4 非零时容器不降级，每个 mip 条目前面多出
	 * 「2 个 u32 + 以 NUL 结尾的 json 字符串 + 1 个 u32」，真正的条目头在那之后。
	 * 这里塞一段非空 json，确认解析器按那个长度跳过去。
	 */
	@Test
	void skipsTheMp4ExtraHeaderOnTexb0004() throws Exception
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		cstring(out, "TEXV0005");
		cstring(out, "TEXI0001");
		int32(out, 0); // format
		int32(out, 2); // flags
		int32(out, 64);
		int32(out, 64);
		int32(out, 64);
		int32(out, 64);
		int32(out, 0); // checksum
		cstring(out, "TEXB0004");

		int32(out, 1); // imageCount
		int32(out, 0); // freeImageFormat
		int32(out, 1); // isVideoMp4 <- 关键：非零才走 mp4 分支
		int32(out, 1); // mipmapCount
		int32(out, 0); // 这三个是"外层"的假条目头，必须被跳过
		int32(out, 0);
		int32(out, 0);

		// mp4 额外段
		int32(out, 7);
		int32(out, 8);
		cstring(out, "{\"fps\":30}");
		int32(out, 9);

		// 真正的条目头
		int32(out, 64); // 条目宽
		int32(out, 64); // 条目高
		int32(out, 0); // compression = 0
		int32(out, 0); // uncompressedSize（compression 0 时会被换成 compressedSize）
		int32(out, PNG.length); // compressedSize
		out.writeBytes(PNG);

		WeTexture texture = WeTexture.parse(out.toByteArray());

		assertEquals("TEXB0004", texture.container());
		assertArrayEquals(PNG, texture.payload(),
			"json 与非零 isVideoMp4 都要被正确跳过，载荷才是 PNG");
	}

	/**
	 * 对照组：同一份数据但 isVideoMp4 为 0（非 mp4 的 TEXB0004 会降级按 TEXB0003
	 * 解析），此时**没有**那段额外数据，末三个字段本身就是条目头。
	 */
	@Test
	void doesNotSkipAnythingWhenIsVideoMp4IsZero() throws Exception
	{
		WeTexture texture = WeTexture.parse(texture("TEXB0004", 9, 0));

		assertArrayEquals(PNG, texture.payload());
	}

	/** LZ4 流坏掉时要抛 IOException（而不是把噪声当像素交出去）。 */
	@Test
	void reportsBrokenLz4Streams() throws Exception
	{
		// 回引越界
		IOException error = assertThrows(IOException.class,
			() -> parseWithLz4(new byte[]{(byte)0x10, 'a', 9, 0}, 10));

		assertTrue(error.getMessage().contains("LZ4"),
			"报错要说明是 LZ4 的问题，实际：" + error.getMessage());
	}

	/**
	 * 视频贴图：载荷是一整段 MP4（`00 00 00 14 "ftyp"` 开头），要认出来而不是报成
	 * "不支持的载荷" —— 实测「流萤」包里 60 帧的两层就是这样，此前属于误报。
	 */
	@Test
	void recognisesMp4Payloads() throws Exception
	{
		byte[] mp4 = {0, 0, 0, 0x14, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0,
			0, 0, 1};

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		cstring(out, "TEXV0005");
		cstring(out, "TEXI0001");
		int32(out, 0);
		int32(out, 2);
		int32(out, 64);
		int32(out, 64);
		int32(out, 64);
		int32(out, 64);
		int32(out, 0);
		cstring(out, "TEXB0003");

		// imageCount, freeImageFormat, mipmapCount, 条目宽高
		int32(out, 1);
		int32(out, 0);
		int32(out, 1);
		int32(out, 64);
		int32(out, 64);
		int32(out, 0); // compression = 0：原样存放
		int32(out, 0);
		int32(out, mp4.length);
		out.writeBytes(mp4);

		WeTexture texture = WeTexture.parse(out.toByteArray());

		assertTrue(texture.isMp4(), "ftyp 开头应当被认成 mp4 贴图");
		assertTrue(!texture.isStandardImage(), "mp4 不是标准图片");

		// 对照组：PNG 载荷不该被误认成 mp4
		assertTrue(!WeTexture.parse(texture("TEXB0003", 8, 0)).isMp4());
	}

	// ------------------------------------------------------------------

	private static WeTexture parseWithLz4(byte[] stream, int uncompressedSize)
		throws IOException
	{
		byte[] file = texture("TEXB0003", 8, 1, uncompressedSize,
			stream.length, stream);
		return WeTexture.parse(file);
	}

	private static byte[] texture(String container, int fieldCount,
		int compression)
	{
		return texture(container, fieldCount, compression, 0, PNG.length, PNG);
	}

	/**
	 * 搭一个最小的 .tex：头部 + 容器名 + fieldCount 个 int32 + 载荷。
	 *
	 * <p>
	 * 末三个 int32 是 compression / uncompressedSize / compressedSize；前面几个
	 * （imageCount、freeImageFormat、isVideoMp4、mipmapCount、width、height）填 0
	 * 就行 —— 解析器不拿它们做判断。
	 * </p>
	 */
	private static byte[] texture(String container, int fieldCount,
		int compression, int uncompressedSize, int compressedSize,
		byte[] payload)
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		cstring(out, "TEXV0005");
		cstring(out, "TEXI0001");

		// format, flags, textureW, textureH, imageW, imageH, checksum
		int32(out, 0);
		int32(out, 2);
		int32(out, 64);
		int32(out, 64);
		int32(out, 64);
		int32(out, 64);
		int32(out, 0);

		cstring(out, container);

		for(int i = 0; i < fieldCount - 3; i++)
			int32(out, 0);

		int32(out, compression);
		int32(out, uncompressedSize);
		int32(out, compressedSize);
		out.writeBytes(payload);
		return out.toByteArray();
	}

	private static void cstring(ByteArrayOutputStream out, String value)
	{
		out.writeBytes(value.getBytes(StandardCharsets.ISO_8859_1));
		out.write(0);
	}

	private static void int32(ByteArrayOutputStream out, int value)
	{
		out.write(value & 0xFF);
		out.write(value >> 8 & 0xFF);
		out.write(value >> 16 & 0xFF);
		out.write(value >> 24 & 0xFF);
	}
}
