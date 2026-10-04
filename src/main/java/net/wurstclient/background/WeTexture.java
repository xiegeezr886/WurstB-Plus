/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Wallpaper Engine {@code .tex} 的头部解析（纯 Java，可单测）。
 *
 * <p>
 * 格式是<b>对着工坊 2359043440 的 scene.pkg 五张贴图逐字节核对出来的</b>
 * （2026-10）：</p>
 *
 * <pre>
 * "TEXV0005\0"                      9 字节，NUL 结尾
 * "TEXI0001\0"                      9 字节，NUL 结尾
 * int32 format                      RGBA8888 时为 0
 * int32 flags                       实测 2
 * int32 textureWidth                显存里的 2 的幂尺寸（4000 -&gt; 4096）
 * int32 textureHeight
 * int32 imageWidth                  真实像素尺寸
 * int32 imageHeight
 * int32 校验字段                     高字节 ff，低三字节随文件而变
 * "TEXB0003\0"                      9 字节，NUL 结尾
 * int32 x8                          x[3]=宽 x[4]=高，x[7]=载荷长度
 * 载荷                              x[7] 字节，正好到文件末尾
 * </pre>
 *
 * <p>
 * <b>字符串是 NUL 结尾而不是长度前缀</b>——这一点上一轮判断反了，结果去搜
 * 「TEXV」时什么都没搜到，还误以为新版 WE 换了容器。判别办法很简单：版本串
 * 后面紧跟的那个 0x00 就是终止符。</p>
 *
 * <p>
 * 更要紧的是：五张贴图的载荷<b>全都是完整的 PNG / JPEG 流</b>（PNG 魔数
 * {@code 89 50 4E 47}、JPEG 魔数 {@code FF D8}），{@code dataSize} 与「文件
 * 长度减去载荷起点」严格相等，Pillow 解出来的尺寸正好等于头部的
 * {@code imageWidth x imageHeight}。所以 {@code format == 0} 时不需要 DXT
 * 解压，直接当图片读即可——{@link #isStandardImage()} 就是按魔数判断这件事，
 * 而不是去信 {@code format} 的取值表。</p>
 *
 * <p>
 * 未证实的部分：{@code format} 其它取值（社区资料里 DXT1/3/5 之类）没有真实
 * 样本，所以这里只保留实测到的 {@link #FORMAT_RGBA8888}；{@code TEXB0003}
 * 之外的容器版本布局也不同，一律拒绝而不是猜。</p>
 */
public final class WeTexture
{
	/** 实测到的唯一格式值：载荷是一张标准图片。 */
	public static final int FORMAT_RGBA8888 = 0;

	/** 尺寸上限，用来挡住解析出来的离谱数字。 */
	private static final int MAX_DIMENSION = 65536;

	private final String version;
	private final String infoVersion;
	private final int format;
	private final int flags;
	private final int textureWidth;
	private final int textureHeight;
	private final int imageWidth;
	private final int imageHeight;
	private final int checksum;
	private final String container;
	private final int dataOffset;
	private final int dataSize;
	private final byte[] payload;

	private WeTexture(String version, String infoVersion, int format, int flags,
		int textureWidth, int textureHeight, int imageWidth, int imageHeight,
		int checksum, String container, int dataOffset, byte[] payload)
	{
		this.version = version;
		this.infoVersion = infoVersion;
		this.format = format;
		this.flags = flags;
		this.textureWidth = textureWidth;
		this.textureHeight = textureHeight;
		this.imageWidth = imageWidth;
		this.imageHeight = imageHeight;
		this.checksum = checksum;
		this.container = container;
		this.dataOffset = dataOffset;
		this.dataSize = payload.length;
		this.payload = payload;
	}

	public static WeTexture read(Path file) throws IOException
	{
		return parse(Files.readAllBytes(file));
	}

	static WeTexture parse(byte[] data) throws IOException
	{
		Cursor cursor = new Cursor(data);

		String version = cursor.cstring();
		if(!version.startsWith("TEXV"))
			throw new IOException("不是 Wallpaper Engine 贴图：" + version);

		String infoVersion = cursor.cstring();
		if(!infoVersion.startsWith("TEXI"))
			throw new IOException("贴图信息段异常：" + infoVersion);

		int format = cursor.int32();
		int flags = cursor.int32();
		int textureWidth = cursor.int32();
		int textureHeight = cursor.int32();
		int imageWidth = cursor.int32();
		int imageHeight = cursor.int32();
		int checksum = cursor.int32();

		if(textureWidth <= 0 || textureHeight <= 0 || imageWidth <= 0
			|| imageHeight <= 0)
			throw new IOException("贴图尺寸不合理：" + textureWidth + "x"
				+ textureHeight + " / " + imageWidth + "x" + imageHeight);

		if(textureWidth > MAX_DIMENSION || textureHeight > MAX_DIMENSION
			|| imageWidth > MAX_DIMENSION || imageHeight > MAX_DIMENSION)
			throw new IOException("贴图尺寸离谱：" + textureWidth + "x"
				+ textureHeight + " / " + imageWidth + "x" + imageHeight);

		String container = cursor.cstring();

		if(!container.startsWith("TEXB"))
			throw new IOException("找不到贴图数据段：" + container);

		if(!container.startsWith("TEXB0003"))
			throw new IOException("暂不支持的贴图容器：" + container
				+ "（只核对过 TEXB0003 的布局）");

		// TEXB0003 头：8 个 int32，末一个是载荷长度
		int[] fields = new int[8];
		for(int i = 0; i < fields.length; i++)
			fields[i] = cursor.int32();

		int offset = cursor.position();
		int available = data.length - offset;

		// 末字段与剩余字节核对：实测严格相等，对不上就退回读到底，别把数据截断
		int size = fields[7] > 0 && fields[7] <= available ? fields[7]
			: available;

		byte[] payload = new byte[size];
		System.arraycopy(data, offset, payload, 0, size);

		return new WeTexture(version, infoVersion, format, flags, textureWidth,
			textureHeight, imageWidth, imageHeight, checksum, container, offset,
			payload);
	}

	public String version()
	{
		return version;
	}

	public String infoVersion()
	{
		return infoVersion;
	}

	/** 实测到的取值见 {@link #FORMAT_RGBA8888}；其它取值未核对。 */
	public int format()
	{
		return format;
	}

	public int flags()
	{
		return flags;
	}

	/** 显存里的尺寸，2 的幂，通常比 {@link #imageWidth()} 大一点。 */
	public int textureWidth()
	{
		return textureWidth;
	}

	public int textureHeight()
	{
		return textureHeight;
	}

	public int imageWidth()
	{
		return imageWidth;
	}

	public int imageHeight()
	{
		return imageHeight;
	}

	/** 头部里那个没解释清楚的字段：高字节恒为 ff，低三字节随文件而变。 */
	public int checksum()
	{
		return checksum;
	}

	public String container()
	{
		return container;
	}

	/** 载荷在原文件里的起点，便于对照十六进制。 */
	public int dataOffset()
	{
		return dataOffset;
	}

	/** 载荷长度：实测正好是「文件长度减去载荷起点」。 */
	public int dataSize()
	{
		return dataSize;
	}

	/** 载荷本身（PNG/JPEG 流，或未核对过的压缩块）。 */
	public byte[] payload()
	{
		return payload;
	}

	/** 载荷是不是一张标准图片：按 PNG/JPEG 魔数判断，不看 {@code format}。 */
	public boolean isStandardImage()
	{
		return isPng() || isJpeg();
	}

	public boolean isPng()
	{
		return startsWith(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
	}

	public boolean isJpeg()
	{
		return startsWith(0xFF, 0xD8, 0xFF);
	}

	private boolean startsWith(int... magic)
	{
		if(payload.length < magic.length)
			return false;

		for(int i = 0; i < magic.length; i++)
			if((payload[i] & 0xFF) != (magic[i] & 0xFF))
				return false;

		return true;
	}

	/**
	 * 小端游标；越界一律抛 {@link IOException}，不返回半截数据。
	 *
	 * <p>
	 * 字符串按 NUL 结尾读，长度上限 {@link #MAX_STRING} 用来防止在损坏文件里
	 * 一路扫到内存尽头。
	 * </p>
	 */
	private static final class Cursor
	{
		private static final int MAX_STRING = 64;

		private final byte[] data;
		private int position;

		Cursor(byte[] data)
		{
			this.data = data;
		}

		int position()
		{
			return position;
		}

		int int32() throws IOException
		{
			if(position + 4 > data.length)
				throw new IOException("贴图在 " + position + " 处提前结束");

			int value = (data[position] & 0xFF) | (data[position + 1] & 0xFF) << 8
				| (data[position + 2] & 0xFF) << 16
				| (data[position + 3] & 0xFF) << 24;
			position += 4;
			return value;
		}

		String cstring() throws IOException
		{
			int start = position;
			int end = start;

			while(end < data.length && data[end] != 0)
				end++;

			if(end >= data.length)
				throw new IOException("贴图在 " + start + " 处的字符串没有结束符");

			if(end - start > MAX_STRING)
				throw new IOException("贴图在 " + start + " 处的字符串过长");

			String value = new String(data, start, end - start,
				java.nio.charset.StandardCharsets.UTF_8);
			position = end + 1;
			return value;
		}
	}
}
