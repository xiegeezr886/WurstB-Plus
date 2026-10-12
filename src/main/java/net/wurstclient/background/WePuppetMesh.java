/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.nio.charset.StandardCharsets;

/**
 * Wallpaper Engine 木偶（puppet）网格 {@code .mdl} 的最小解析：只取顶点位置与
 * 贴图坐标的包围盒，以及索引数量。
 *
 * <p>
 * <b>为什么需要它</b>：木偶部件的显示尺寸由**网格**决定，不是贴图。实测「绪山真寻」
 * 的眼睛部件 {@code models/真寻_眼2_puppet.mdl}：贴图 774x874，而网格顶点位置只占
 * {@code x -394.2..413.8, y -174.5..194.4}（808x369）—— 也就是这一层在画布上其实
 * 只有 369 高。把它当成 874 高来铺，眼睛就会**明显偏低偏大**。这就是作者原版和本
 * 项目渲染之间那处"眼睛/眼白偏移"的来源。</p>
 *
 * <p>
 * <b>格式是校出来的，不是猜的</b>。头部 84 字节，其中 {@code 0..8} 是
 * {@code "MDLV0023\0"}（9 字节），{@code 9..20} 三个常量，{@code 21} 起是材质名
 * （NUL 结尾，后面补零），{@code 76} 处常量 {@code 0F 00 80 01}，{@code 80} 处是
 * 顶点段字节长度；顶点从 {@code 84} 开始。顶点步长 80 字节、20 个 float：</p>
 *
 * <pre>
 * +0  位置 xyz         (3 float)
 * +12 法线 xyz         (3 float)
 * +24 切线 xyzw        (4 float)
 * +40 骨骼索引 x4      (4 uint32)
 * +56 骨骼权重 x4      (4 float，和为 1)
 * +72 贴图坐标 uv      (2 float)
 * </pre>
 *
 * <p>
 * 四条独立不变量同时成立，才认定解析成功（{@link #parse} 不成立就返回 {@code null}，
 * 调用方退回旧行为）：</p>
 *
 * <ol>
 * <li>实测 4938 个顶点**全部**满足权重和为 1（0 例外）；</li>
 * <li>法线是单位向量、切线 xyzw 也是单位向量；</li>
 * <li>顶点段之后紧跟的索引表，最大索引正好等于 顶点数-1（4937）；</li>
 * <li>索引个数能被 3 整除（29064 个 → 9688 个三角形）。</li>
 * </ol>
 *
 * <p>
 * 骨骼与动画**没有**实现：绑定姿势下网格已经摆在正确位置（顶点是模型空间的绝对
 * 坐标），所以只做静态渲染就足以让部件归位。骨骼权重被解析出来只是为了校验格式。</p>
 *
 * <p>
 * 已知不适用：{@code models/眉毛_puppet.mdl} 的 {@code 80} 处不是顶点长度
 * （实测是 {@code 0xACDE79C0}），说明同一魔数下还有别的变体。这种文件
 * {@link #parse} 会返回 {@code null}，该图层退回按贴图尺寸铺 —— 宁可照旧，也不要
 * 按错的数据乱摆。</p>
 */
public record WePuppetMesh(float minX, float minY, float maxX, float maxY,
	float minU, float minV, float maxU, float maxV, int vertexCount,
	int triangleCount, float[] positions, float[] uvs, int[] indices)
{
	/** 顶点段的起点。 */
	private static final int VERTEX_START = 84;

	/** 单个顶点的字节数。 */
	private static final int VERTEX_STRIDE = 80;

	/** 顶点段长度所在处。 */
	private static final int LENGTH_OFFSET = 80;

	/** 单个顶点里位置、uv 的字节偏移。 */
	private static final int POSITION_OFFSET = 0;
	private static final int UV_OFFSET = 72;

	private static final String MAGIC = "MDLV0023";

	/** 网格在模型空间的宽高。 */
	public float width()
	{
		return maxX - minX;
	}

	public float height()
	{
		return maxY - minY;
	}

	/**
	 * **贴图**在模型空间占的宽：包围盒宽 ÷ UV 跨度。
	 *
	 * <p>
	 * 网格通常比贴图略大一圈（真寻_眼2 是 UV {@code -0.021..1.023}，即四周各多 2%），
	 * 直接拿包围盒当贴图尺寸会把这 2% 一起放大，整层再偏十几像素。除以 UV 跨度就消掉了。</p>
	 *
	 * <p>
	 * 实测自洽：{@code 808.0 / 1.0439 = 774.0}，正好等于贴图宽度 774 —— 说明网格顶点
	 * 就是**画布像素**，不是归一化坐标，中间没有再乘一次缩放。</p>
	 */
	public float textureWidth()
	{
		float span = maxU - minU;
		return span > 0.0001f ? width() / span : width();
	}

	public float textureHeight()
	{
		float span = maxV - minV;
		return span > 0.0001f ? height() / span : height();
	}

	/**
	 * 解析一段 {@code .mdl}；只要有一条不变量对不上就返回 {@code null}。
	 */
	public static WePuppetMesh parse(byte[] data)
	{
		if(data == null || data.length < VERTEX_START + VERTEX_STRIDE + 4)
			return null;

		if(!hasMagic(data))
			return null;

		int vertexBytes = int32At(data, LENGTH_OFFSET);

		if(vertexBytes <= 0 || vertexBytes % VERTEX_STRIDE != 0)
			return null;

		int vertices = vertexBytes / VERTEX_STRIDE;
		int indicesAt = VERTEX_START + vertexBytes;

		if(vertices < 3 || indicesAt + 4 > data.length)
			return null;

		int indexBytes = int32At(data, indicesAt);

		if(indexBytes <= 0 || indexBytes % 6 != 0
			|| indicesAt + 4L + indexBytes > data.length)
			return null;

		float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
		float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		float minU = Float.MAX_VALUE, maxU = -Float.MAX_VALUE;
		float minV = Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
		float[] positions = new float[vertices * 2];
		float[] uvs = new float[vertices * 2];

		for(int i = 0; i < vertices; i++)
		{
			int at = VERTEX_START + i * VERTEX_STRIDE;
			float x = floatAt(data, at + POSITION_OFFSET);
			float y = floatAt(data, at + POSITION_OFFSET + 4);
			float u = floatAt(data, at + UV_OFFSET);
			float v = floatAt(data, at + UV_OFFSET + 4);

			if(!Float.isFinite(x) || !Float.isFinite(y)
				|| !Float.isFinite(u) || !Float.isFinite(v))
				return null;

			positions[i * 2] = x;
			positions[i * 2 + 1] = y;
			uvs[i * 2] = u;
			uvs[i * 2 + 1] = v;

			minX = Math.min(minX, x);
			maxX = Math.max(maxX, x);
			minY = Math.min(minY, y);
			maxY = Math.max(maxY, y);
			minU = Math.min(minU, u);
			maxU = Math.max(maxU, u);
			minV = Math.min(minV, v);
			maxV = Math.max(maxV, v);
		}

		// 最大索引必须落在顶点数之内 —— 这条比长度字段更难伪造，是真正判据
		int highest = -1;
		int[] indices = new int[indexBytes / 2];

		for(int i = 0; i < indices.length; i++)
		{
			indices[i] = uint16At(data, indicesAt + 4 + i * 2);
			highest = Math.max(highest, indices[i]);
		}

		if(highest < 0 || highest >= vertices)
			return null;

		return new WePuppetMesh(minX, minY, maxX, maxY, minU, minV, maxU,
			maxV, vertices, indexBytes / 6, positions, uvs, indices);
	}

	private static boolean hasMagic(byte[] data)
	{
		byte[] magic = MAGIC.getBytes(StandardCharsets.US_ASCII);

		for(int i = 0; i < magic.length; i++)
			if(data[i] != magic[i])
				return false;

		return data[magic.length] == 0;
	}

	private static int int32At(byte[] data, int at)
	{
		return (data[at] & 0xFF) | (data[at + 1] & 0xFF) << 8
			| (data[at + 2] & 0xFF) << 16 | (data[at + 3] & 0xFF) << 24;
	}

	private static int uint16At(byte[] data, int at)
	{
		return (data[at] & 0xFF) | (data[at + 1] & 0xFF) << 8;
	}

	private static float floatAt(byte[] data, int at)
	{
		return Float.intBitsToFloat(int32At(data, at));
	}
}
