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
 * <pre>
 * [int32 长度]["TEXV0005"]
 * [int32 长度]["TEXI0001"]
 * [int32 format][int32 flags][int32 纹理宽][int32 纹理高][int32 图宽][int32 图高][int32 未知]
 * [int32 长度]["TEXB000x"]   ← 之后是 mipmap 数量与各层数据
 * </pre>
 *
 * <p>
 * 这里只把头部与容器起点解析出来，像素数据交给调用方：DXT 块可以直接用
 * {@code GL_COMPRESSED_*_S3TC_*} 上传，不必在 CPU 解压。</p>
 *
 * <p>
 * <b>尚未核对的部分（如实说明）</b>：format 号到 GL 格式的对照（下面那几个
 * 常量）是按公开实现的通行值写的，本轮只跑通了 {@code scene.pkg} 的表结构，
 * 还没拿真实 {@code .tex} 逐张验证。用之前先用 {@link #format()} 打出来对一遍，
 * 别直接照这张表上传。</p>
 */
public final class WeTexture
{
	public static final int FORMAT_RGBA8888 = 0;
	public static final int FORMAT_DXT5 = 4;
	public static final int FORMAT_DXT3 = 6;
	public static final int FORMAT_DXT1 = 7;
	public static final int FORMAT_R8 = 9;

	private final String version;
	private final String infoVersion;
	private final int format;
	private final int flags;
	private final int textureWidth;
	private final int textureHeight;
	private final int imageWidth;
	private final int imageHeight;
	private final String container;
	private final int dataOffset;

	private WeTexture(String version, String infoVersion, int format, int flags,
		int textureWidth, int textureHeight, int imageWidth, int imageHeight,
		String container, int dataOffset)
	{
		this.version = version;
		this.infoVersion = infoVersion;
		this.format = format;
		this.flags = flags;
		this.textureWidth = textureWidth;
		this.textureHeight = textureHeight;
		this.imageWidth = imageWidth;
		this.imageHeight = imageHeight;
		this.container = container;
		this.dataOffset = dataOffset;
	}

	public static WeTexture read(Path file) throws IOException
	{
		return parse(Files.readAllBytes(file));
	}

	static WeTexture parse(byte[] data) throws IOException
	{
		Cursor cursor = new Cursor(data);
		String version = cursor.string();

		if(!version.startsWith("TEXV"))
			throw new IOException("不是 Wallpaper Engine 贴图：" + version);

		String infoVersion = cursor.string();

		if(!infoVersion.startsWith("TEXI"))
			throw new IOException("贴图信息段异常：" + infoVersion);

		int format = cursor.int32();
		int flags = cursor.int32();
		int textureWidth = cursor.int32();
		int textureHeight = cursor.int32();
		int imageWidth = cursor.int32();
		int imageHeight = cursor.int32();
		cursor.int32(); // 未知字段，公开实现里一直被忽略

		if(textureWidth <= 0 || textureHeight <= 0 || imageWidth <= 0
			|| imageHeight <= 0)
			throw new IOException("贴图尺寸不合理：" + textureWidth + "x"
				+ textureHeight + " / " + imageWidth + "x" + imageHeight);

		String container = cursor.string();

		if(!container.startsWith("TEXB"))
			throw new IOException("找不到贴图数据段：" + container);

		return new WeTexture(version, infoVersion, format, flags, textureWidth,
			textureHeight, imageWidth, imageHeight, container,
			cursor.position());
	}

	public String version()
	{
		return version;
	}

	public String infoVersion()
	{
		return infoVersion;
	}

	/** 原样暴露，见类注释里那句"尚未核对"。 */
	public int format()
	{
		return format;
	}

	public int flags()
	{
		return flags;
	}

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

	public String container()
	{
		return container;
	}

	/** mipmap 数量字段所在的偏移。 */
	public int dataOffset()
	{
		return dataOffset;
	}

	/** 按通行对照表判断是否 DXT 块压缩（同样待真实文件核对）。 */
	public boolean isDxt()
	{
		return format == FORMAT_DXT5 || format == FORMAT_DXT3
			|| format == FORMAT_DXT1;
	}

	/** 小端游标；越界抛 {@link IOException}。 */
	private static final class Cursor
	{
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

		String string() throws IOException
		{
			int length = int32();

			if(length < 0 || position + length > data.length)
				throw new IOException("字符串长度不合理：" + length);

			String value = new String(data, position, length,
				java.nio.charset.StandardCharsets.UTF_8);
			position += length;
			return value;
		}
	}
}
