/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * 木偶网格的最小解析（{@link WePuppetMesh}）。
 *
 * <p>
 * 字段偏移与步长是**从真实文件校出来的**（四条不变量，见类注释），这里用合成数据
 * 把它们钉死：位置在 +0、贴图坐标在 +72、步长 80、顶点段长度写在 80 处、顶点段从
 * 84 开始。任何一个偏移被改错，这些用例都会红 —— 真机文件不进单测（那是用户机器上
 * 的工坊内容，不能当测试依赖）。
 * </p>
 *
 * <p>
 * 最后一组是**判据**用例：最大索引越界必须被拒。它比长度字段更难伪造 —— 长度写对了
 * 但索引指向不存在的顶点，说明整个偏移假设是错的，这种文件宁可退回旧行为。
 * </p>
 */
final class WePuppetMeshTest
{
	@Test
	void parsesQuadBoundingBox()
	{
		// 一个 200x100 的矩形，四个角各取贴图一角
		byte[] data = mesh(new float[][]{{10, 20, 0.0f, 0.0f},
			{210, 20, 1.0f, 0.0f}, {210, 120, 1.0f, 1.0f},
			{10, 120, 0.0f, 1.0f}}, new int[]{0, 1, 2, 0, 2, 3});

		WePuppetMesh parsed = WePuppetMesh.parse(data);

		assertNotNull(parsed);
		assertEquals(4, parsed.vertexCount());
		assertEquals(2, parsed.triangleCount());
		assertEquals(10f, parsed.minX(), 0.001f);
		assertEquals(210f, parsed.maxX(), 0.001f);
		assertEquals(20f, parsed.minY(), 0.001f);
		assertEquals(120f, parsed.maxY(), 0.001f);
		assertEquals(200f, parsed.width(), 0.001f);
		assertEquals(100f, parsed.height(), 0.001f);
		assertEquals(0f, parsed.minU(), 0.001f);
		assertEquals(1f, parsed.maxU(), 0.001f);
		assertEquals(0f, parsed.minV(), 0.001f);
		assertEquals(1f, parsed.maxV(), 0.001f);

		// UV 正好铺满 0..1 时，贴图尺寸就是包围盒尺寸
		assertEquals(200f, parsed.textureWidth(), 0.001f);
		assertEquals(100f, parsed.textureHeight(), 0.001f);
	}

	/**
	 * 真实的「真寻_眼2」形状：贴图 774x874，网格只有 808x369。这里照抄那两个数，
	 * 钉住"尺寸取自网格而不是贴图"这条结论。
	 */
	@Test
	void meshSizeDiffersFromTextureSize()
	{
		byte[] data = mesh(new float[][]{{-394.2f, -174.5f, -0.021f, -0.021f},
			{413.8f, -174.5f, 1.023f, -0.021f},
			{413.8f, 194.4f, 1.023f, 1.024f},
			{-394.2f, 194.4f, -0.021f, 1.024f}},
			new int[]{0, 1, 2, 0, 2, 3});

		WePuppetMesh parsed = WePuppetMesh.parse(data);

		assertNotNull(parsed);
		assertEquals(808f, parsed.width(), 0.1f);
		assertEquals(368.9f, parsed.height(), 0.1f);

		// 除以 UV 跨度后正好回到贴图宽度 774 —— 反证网格顶点就是画布像素
		assertEquals(774f, parsed.textureWidth(), 1f);

		// 高度方向被压到 353，而贴图是 874 —— 按贴图铺会高出 2.47 倍，
		// 这就是"眼睛/眼白偏移"的来源
		assertEquals(353f, parsed.textureHeight(), 1f);
		assertEquals(2.47f, 874f / parsed.textureHeight(), 0.02f);
	}

	@Test
	void rejectsBadMagic()
	{
		byte[] data = mesh(new float[][]{{0, 0, 0, 0}, {1, 0, 1, 0}, {1, 1, 1, 1}},
			new int[]{0, 1, 2});
		data[3] = 'X';

		assertNull(WePuppetMesh.parse(data));
	}

	@Test
	void rejectsStrideThatDoesNotDivide()
	{
		byte[] data = mesh(new float[][]{{0, 0, 0, 0}, {1, 0, 1, 0}, {1, 1, 1, 1}},
			new int[]{0, 1, 2});
		writeInt(data, 80, 3 * 80 + 4);

		assertNull(WePuppetMesh.parse(data));
	}

	/** 长度字段写得漂亮，但索引指到不存在的顶点 —— 必须拒。 */
	@Test
	void rejectsIndexOutOfRange()
	{
		byte[] data = mesh(new float[][]{{0, 0, 0, 0}, {1, 0, 1, 0}, {1, 1, 1, 1}},
			new int[]{0, 1, 2});
		writeInt(data, 84 + 3 * 80 + 4, 7);

		assertNull(WePuppetMesh.parse(data));
	}

	@Test
	void rejectsTruncatedAndEmpty()
	{
		assertNull(WePuppetMesh.parse(null));
		assertNull(WePuppetMesh.parse(new byte[0]));
		assertNull(WePuppetMesh.parse("MDLV0023".getBytes(StandardCharsets.US_ASCII)));

		byte[] data = mesh(new float[][]{{0, 0, 0, 0}, {1, 0, 1, 0}, {1, 1, 1, 1}},
			new int[]{0, 1, 2});
		byte[] cut = new byte[data.length - 8];
		System.arraycopy(data, 0, cut, 0, cut.length);

		assertNull(WePuppetMesh.parse(cut));
	}

	@Test
	void rejectsIndexCountNotMultipleOfThree()
	{
		byte[] data = mesh(new float[][]{{0, 0, 0, 0}, {1, 0, 1, 0}, {1, 1, 1, 1}},
			new int[]{0, 1, 2});
		writeInt(data, 84 + 3 * 80, 4);

		assertNull(WePuppetMesh.parse(data));
	}

	/** 造一段 .mdl：头部 84 字节 + 顶点 + 索引长度 + 索引。 */
	private static byte[] mesh(float[][] vertices, int[] indices)
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] header = new byte[84];
		byte[] magic = "MDLV0023".getBytes(StandardCharsets.US_ASCII);
		System.arraycopy(magic, 0, header, 0, magic.length);
		writeInt(header, 76, 0x0180000F);
		writeInt(header, 80, vertices.length * 80);
		out.writeBytes(header);

		for(float[] vertex : vertices)
		{
			byte[] record = new byte[80];
			writeFloat(record, 0, vertex[0]);
			writeFloat(record, 4, vertex[1]);
			writeFloat(record, 8, 0);
			writeFloat(record, 12, 0);
			writeFloat(record, 16, 0);
			writeFloat(record, 20, 1);
			writeFloat(record, 24, 1);
			writeFloat(record, 32, 1);
			writeFloat(record, 56, 1);
			writeFloat(record, 72, vertex[2]);
			writeFloat(record, 76, vertex[3]);
			out.writeBytes(record);
		}

		byte[] length = new byte[4];
		writeInt(length, 0, indices.length * 2);
		out.writeBytes(length);

		byte[] indexBytes = new byte[indices.length * 2];

		for(int i = 0; i < indices.length; i++)
			writeShort(indexBytes, i * 2, indices[i]);

		out.writeBytes(indexBytes);
		return out.toByteArray();
	}

	private static void writeInt(byte[] data, int at, int value)
	{
		data[at] = (byte)value;
		data[at + 1] = (byte)(value >> 8);
		data[at + 2] = (byte)(value >> 16);
		data[at + 3] = (byte)(value >> 24);
	}

	private static void writeShort(byte[] data, int at, int value)
	{
		data[at] = (byte)value;
		data[at + 1] = (byte)(value >> 8);
	}

	private static void writeFloat(byte[] data, int at, float value)
	{
		writeInt(data, at, Float.floatToIntBits(value));
	}
}
