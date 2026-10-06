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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * {@link WeTexture} 的容器族：{@code TEXB0003} 与 {@code TEXB0004}。
 *
 * <p>
 * 两个族的骨架相同（{@code TEXV} + {@code TEXI} + 7 个 int32 + 容器名），差别只在
 * 容器名之后那个 int32 块的宽度：0003 是 8 个（载荷落在 +32），0004 是 **9 个**
 * （载荷落在 +36）。这不是猜的——用户库里 83 个真实场景包，第一个 {@code .tex} 的
 * 内层容器分布是 {@code TEXB0003=64 / TEXB0004=17 / TEXB0002=1}，而真实文件里 0003
 * 的 JPEG 魔数在偏移 87、0004 的 PNG 魔数在 91，容器名又都在 55 结束，正好差 4 字节。
 * 不认 0004 的话那 17 个包（约 20%）至少有一层贴图被整层跳过。
 * </p>
 *
 * <p>
 * {@code TEXB0002} 是**另一个族**：它的载荷既不是 PNG 也不是 JPEG（原始/压缩位图），
 * 所以这里只钉住「仍然明确拒绝」，而不是假装支持。
 * </p>
 */
final class WeTextureContainerVariantTest
{
	/** 最小可辨认的 PNG 头，当作载荷用：尺寸字段由头部单独给，解析器不看载荷内容。 */
	private static final byte[] PNG = {(byte)0x89, 'P', 'N', 'G', 0x0D, 0x0A,
		0x1A, 0x0A, 1, 2, 3, 4, 5, 6, 7, 8};

	@Test
	void readsTexb0003AndTexb0004() throws Exception
	{
		for(String container : new String[]{"TEXB0003", "TEXB0004"})
		{
			int fieldCount = container.endsWith("0004") ? 9 : 8;
			WeTexture texture =
				WeTexture.parse(texture(container, fieldCount));

			assertEquals(container, texture.container());
			assertEquals(64, texture.imageWidth());
			assertEquals(64, texture.imageHeight());
			assertArrayEquals(PNG, texture.payload(),
				container + " 的载荷应当整段取出来");
		}
	}

	/** 原始位图那个族仍然明确拒绝，不能悄悄当成 0003/0004 去读。 */
	@Test
	void stillRefusesTheRawContainer()
	{
		assertThrows(IOException.class,
			() -> WeTexture.parse(texture("TEXB0002", 8)));
	}

	// ------------------------------------------------------------------

	private static byte[] texture(String container, int fieldCount)
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

		for(int i = 0; i < fieldCount - 1; i++)
			int32(out, 0);

		// 末字段是主图字节数，后面（真实文件里）还跟着 mipmap
		int32(out, PNG.length);
		out.writeBytes(PNG);
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
