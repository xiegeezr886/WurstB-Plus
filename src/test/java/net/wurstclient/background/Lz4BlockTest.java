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

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * {@link Lz4Block}：手工构造的块流 + 精确断言。
 *
 * <p>
 * 这些流是照 LZ4 块格式逐字节搭的（token 的高 4 位是字面量长度、低 4 位是
 * {@code 匹配长度 − 4}，长度为 15 时后面跟扩展字节，每字节 255 就继续），所以断言的是
 * "格式说什么"而不是"我的实现输出什么"。
 * </p>
 */
final class Lz4BlockTest
{
	@Test
	void decodesLiteralsOnly()
	{
		// token = 0xF0 -> 字面量 15 + 扩展；扩展写成 5 => 一共 20 字节
		byte[] input = new byte[2 + 20];
		input[0] = (byte)0xF0;
		input[1] = 5;

		for(int i = 0; i < 20; i++)
			input[2 + i] = (byte)('a' + i % 26);

		byte[] out = Lz4Block.decompress(input, input.length, 20);

		for(int i = 0; i < 20; i++)
			assertEquals('a' + i % 26, out[i] & 0xFF, "第 " + i + " 个字节");
	}

	/** 一个字节的字面量 + 回引 9 字节（offset=1）=> 10 个相同字符。 */
	@Test
	void expandsAMatchWithOffsetOne()
	{
		byte[] input = {(byte)0x15, 'a', 1, 0};

		byte[] out = Lz4Block.decompress(input, input.length, 10);

		assertArrayEquals("aaaaaaaaaa".getBytes(StandardCharsets.ISO_8859_1),
			out);
	}

	/** 回引可以指向比当前匹配更早的内容，形成重复展开。 */
	@Test
	void expandsOverlappingMatches()
	{
		// 字面量 "abcd"（4） + 回引 offset=4 长度 8 => abcdabcdabcd
		byte[] input = {(byte)0x44, 'a', 'b', 'c', 'd', 4, 0};

		byte[] out = Lz4Block.decompress(input, input.length, 12);

		assertArrayEquals("abcdabcdabcd".getBytes(StandardCharsets.ISO_8859_1),
			out);
	}

	/** 匹配长度也能走扩展字节：15 + 4 = 19，再加扩展里的 10 => 29。 */
	@Test
	void readsLongMatchLengths()
	{
		// 字面量 4 字节 "wxyz"，token 低 4 位 = 15 => 匹配长度 = 15 + 扩展(10) + 4 = 29
		byte[] input = new byte[4 + 1 + 2 + 1];

		int at = 0;
		input[at++] = (byte)0x4F; // 字面量 4、匹配低 4 位 15
		input[at++] = 'w';
		input[at++] = 'x';
		input[at++] = 'y';
		input[at++] = 'z';
		input[at++] = 4; // offset = 4
		input[at++] = 0;
		input[at++] = 10; // 长度扩展 10 => 15 + 10 + 4 = 29

		byte[] out = Lz4Block.decompress(input, at, 4 + 29);

		String text = new String(out, StandardCharsets.ISO_8859_1);
		assertEquals("wxyz" + "wxyz".repeat(7) + "w", text);
	}

	/** 长度扩展的 0xFF 连续段：255 要连着读下去。 */
	@Test
	void continuesLengthAcross0xFFBytes()
	{
		// 字面量 15 + 255 + 30 = 300
		byte[] input = new byte[2 + 1 + 300];
		input[0] = (byte)0xF0;
		input[1] = (byte)0xFF;
		input[2] = 30;

		for(int i = 0; i < 300; i++)
			input[3 + i] = (byte)(i & 0x7F);

		byte[] out = Lz4Block.decompress(input, input.length, 300);

		assertEquals(300, out.length);
		assertEquals(0, out[0] & 0x7F);
		assertEquals(299 & 0x7F, out[299] & 0x7F);
	}

	@Test
	void refusesBrokenStreams()
	{
		// 回引越界：offset 指向还没输出过的位置
		assertThrows(IllegalArgumentException.class,
			() -> Lz4Block.decompress(new byte[]{(byte)0x10, 'a', 9, 0}, 4, 10));

		// offset = 0 永远非法
		assertThrows(IllegalArgumentException.class,
			() -> Lz4Block.decompress(new byte[]{(byte)0x10, 'a', 0, 0}, 4, 10));

		// 字面量比目标还长
		assertThrows(IllegalArgumentException.class,
			() -> Lz4Block.decompress(
				new byte[]{(byte)0x50, 'a', 'b', 'c', 'd', 'e'}, 6, 3));

		// 长度扩展被截断
		assertThrows(IllegalArgumentException.class,
			() -> Lz4Block.decompress(new byte[]{(byte)0xF0}, 1, 300));

		// 解出来的长度与目标不符
		assertThrows(IllegalArgumentException.class,
			() -> Lz4Block.decompress(new byte[]{(byte)0x10, 'a', 1, 0}, 4, 11));

		// 目标长度为 0 但流里还有一个空 token、目标却是 1：长度对不上
		assertThrows(IllegalArgumentException.class,
			() -> Lz4Block.decompress(new byte[]{0x00}, 1, 1));
	}

	/** 只有一个空 token（字面量 0、没有回引）时，合法的结果是空输出。 */
	@Test
	void acceptsAnEmptyTokenForAnEmptyTarget()
	{
		assertArrayEquals(new byte[0], Lz4Block.decompress(new byte[]{0x00}, 1, 0));
	}

	@Test
	void handlesTheEmptyStream()
	{
		assertArrayEquals(new byte[0], Lz4Block.decompress(new byte[0], 0, 0));
	}
}
