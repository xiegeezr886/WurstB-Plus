/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Tests the {@code .tex} reader against the layout that was measured on the real
 * workshop file (2359043440): NUL terminated version strings rather than length
 * prefixed ones, a TEXB0003 container of eight int32s whose last field is the
 * payload length, and a payload that is a plain PNG or JPEG stream.
 *
 * <p>
 * The two things worth guarding here are the string terminator and the payload
 * boundary. Getting the first one wrong is what made an earlier attempt report
 * that the file contained no textures at all; getting the second one wrong
 * silently decodes a truncated image.
 */
final class WeTextureTest
{
	/** A minimal PNG signature plus enough bytes to be a plausible payload. */
	private static final byte[] PNG = {(byte)0x89, 'P', 'N', 'G', 0x0D, 0x0A,
		0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 'R'};

	private static void int32(ByteArrayOutputStream out, int value)
	{
		out.write(value & 0xFF);
		out.write(value >>> 8 & 0xFF);
		out.write(value >>> 16 & 0xFF);
		out.write(value >>> 24 & 0xFF);
	}

	/** NUL terminated, not length prefixed - that is the whole point. */
	private static void cstring(ByteArrayOutputStream out, String value)
	{
		out.writeBytes(value.getBytes(StandardCharsets.UTF_8));
		out.write(0);
	}

	private static byte[] fakeTexture()
	{
		return fakeTexture(PNG);
	}

	private static byte[] fakeTexture(byte[] payload)
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		cstring(out, "TEXV0005");
		cstring(out, "TEXI0001");
		int32(out, WeTexture.FORMAT_RGBA8888);
		int32(out, 2);
		int32(out, 4096); // 显存尺寸，2 的幂
		int32(out, 4096);
		int32(out, 4000); // 真实尺寸
		int32(out, 2250);
		int32(out, 0xFF261E08);
		cstring(out, "TEXB0003");

		// 容器头八个 int32，末一个是载荷长度
		int32(out, 1);
		int32(out, 13);
		int32(out, 1);
		int32(out, 4000);
		int32(out, 2250);
		int32(out, 0);
		int32(out, 0);
		int32(out, payload.length);

		out.writeBytes(payload);
		return out.toByteArray();
	}

	@Test
	void itReadsTheHeader() throws IOException
	{
		WeTexture texture = WeTexture.parse(fakeTexture());

		assertEquals("TEXV0005", texture.version());
		assertEquals("TEXI0001", texture.infoVersion());
		assertEquals(WeTexture.FORMAT_RGBA8888, texture.format());
		assertEquals(2, texture.flags());
		assertEquals(4096, texture.textureWidth());
		assertEquals(4096, texture.textureHeight());
		assertEquals(4000, texture.imageWidth());
		assertEquals(2250, texture.imageHeight());
		assertEquals(0xFF261E08, texture.checksum());
		assertEquals("TEXB0003", texture.container());
	}

	/** 载荷必须正好是那 dataSize 个字节，不多不少。 */
	@Test
	void thePayloadIsExactlyTheDeclaredSize() throws IOException
	{
		WeTexture texture = WeTexture.parse(fakeTexture());

		assertArrayEquals(PNG, texture.payload());
		assertEquals(PNG.length, texture.dataSize());
		assertEquals(fakeTexture().length - PNG.length, texture.dataOffset());
		assertEquals(fakeTexture().length, texture.dataOffset() + texture.dataSize());
	}

	@Test
	void itRecognisesImagePayloads() throws IOException
	{
		WeTexture png = WeTexture.parse(fakeTexture());
		assertTrue(png.isStandardImage());
		assertTrue(png.isPng());
		assertFalse(png.isJpeg());

		byte[] jpegBytes =
			{(byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE1, 0x0A, 0x0C};
		WeTexture jpeg = WeTexture.parse(fakeTexture(jpegBytes));
		assertTrue(jpeg.isStandardImage());
		assertTrue(jpeg.isJpeg());
		assertFalse(jpeg.isPng());

		// 既不认识也不是图片：调用方应当跳过这一层
		WeTexture unknown = WeTexture.parse(fakeTexture(new byte[]{1, 2, 3}));
		assertFalse(unknown.isStandardImage());
		assertEquals(3, unknown.dataSize());
	}

	/**
	 * 载荷长度字段对不上时退回「读到底」，而不是交出一段截断的图片。
	 */
	@Test
	void aWrongPayloadSizeFallsBackToTheEndOfTheFile() throws IOException
	{
		byte[] data = fakeTexture();
		// 第八个 int32 就在载荷前面四字节起
		int sizeField = data.length - PNG.length - 4;
		data[sizeField] = 0x7F;
		data[sizeField + 1] = 0x00;
		data[sizeField + 2] = 0x00;
		data[sizeField + 3] = 0x00;

		WeTexture texture = WeTexture.parse(data);
		assertEquals(PNG.length, texture.dataSize());
		assertArrayEquals(PNG, texture.payload());
	}

	@Test
	void itRefusesMalformedTextures()
	{
		// 不是贴图
		ByteArrayOutputStream bad = new ByteArrayOutputStream();
		cstring(bad, "PKGV0013");
		assertThrows(IOException.class,
			() -> WeTexture.parse(bad.toByteArray()));

		// 尺寸为 0
		ByteArrayOutputStream zero = new ByteArrayOutputStream();
		cstring(zero, "TEXV0005");
		cstring(zero, "TEXI0001");
		for(int i = 0; i < 7; i++)
			int32(zero, 0);
		cstring(zero, "TEXB0003");
		assertThrows(IOException.class,
			() -> WeTexture.parse(zero.toByteArray()));

		// 字符串没有 NUL 终止符：必须报错而不是一路扫下去
		ByteArrayOutputStream unterminated = new ByteArrayOutputStream();
		unterminated.writeBytes(
			"TEXV0005TEXI0001".getBytes(StandardCharsets.UTF_8));
		assertThrows(IOException.class,
			() -> WeTexture.parse(unterminated.toByteArray()));

		// 没核对过的容器版本：拒绝，而不是照 TEXB0003 猜
		ByteArrayOutputStream other = new ByteArrayOutputStream();
		cstring(other, "TEXV0005");
		cstring(other, "TEXI0001");
		int32(other, 0);
		int32(other, 0);
		int32(other, 8);
		int32(other, 8);
		int32(other, 8);
		int32(other, 8);
		int32(other, 0);
		cstring(other, "TEXB0001");
		assertThrows(IOException.class,
			() -> WeTexture.parse(other.toByteArray()));
	}
}
