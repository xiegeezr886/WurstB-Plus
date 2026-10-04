/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wallpaper Engine 的 {@code scene.pkg} 读取器（纯 Java，可单测）。
 *
 * <p>
 * 格式（对着工坊 2359043440 的 14 MB 文件实测）：</p>
 *
 * <pre>
 * [int32 长度]["PKGV0013"]
 * [int32 条目数]
 * 每条：[int32 名字长度][名字(UTF-8)][int32 偏移][int32 长度]
 * </pre>
 *
 * <p>
 * <b>条目里的偏移是相对「条目表结束处」的</b>，不是相对文件头。漏掉这个基准的
 * 后果不是报错，而是把整张贴图读成随机字节——本轮就踩过一次，所以这里把
 * {@code dataStart} 显式存下来并做越界检查。</p>
 *
 * <p>
 * 典型场景包里有 {@code .json}（图层树）、{@code .tex}（贴图）、
 * {@code .frag}/{@code .vert}（GLSL 着色器，DirectX 那套 {@code .dxs} 之外
 * 还另有一份）与音频。</p>
 */
public final class WePackage
{
	/** 一条目录项；{@code offset} 已经是绝对偏移。 */
	public record Entry(String name, int offset, int length)
	{}

	private final byte[] data;
	private final String version;
	private final int dataStart;
	private final Map<String, Entry> entries;

	private WePackage(byte[] data, String version, int dataStart,
		Map<String, Entry> entries)
	{
		this.data = data;
		this.version = version;
		this.dataStart = dataStart;
		this.entries = entries;
	}

	public static WePackage read(Path file) throws IOException
	{
		return parse(Files.readAllBytes(file));
	}

	static WePackage parse(byte[] data) throws IOException
	{
		Cursor cursor = new Cursor(data);
		String magic = cursor.string();

		if(!magic.startsWith("PKGV"))
			throw new IOException("不是 Wallpaper Engine 场景包：" + magic);

		int count = cursor.int32();

		if(count < 0 || count > 1_000_000)
			throw new IOException("条目数不合理：" + count);

		// 表里存的是相对偏移，全部读完才知道基准在哪
		List<String> names = new ArrayList<>(count);
		List<int[]> raw = new ArrayList<>(count);

		for(int i = 0; i < count; i++)
		{
			names.add(cursor.string());
			raw.add(new int[]{cursor.int32(), cursor.int32()});
		}

		int start = cursor.position();
		Map<String, Entry> entries = new LinkedHashMap<>();

		for(int i = 0; i < count; i++)
		{
			int[] range = raw.get(i);
			int offset = start + range[0];
			int length = range[1];

			if(offset < 0 || length < 0 || offset + (long)length > data.length)
				throw new IOException("条目越界：" + names.get(i) + " @" + offset
					+ " +" + length);

			entries.put(names.get(i),
				new Entry(names.get(i), offset, length));
		}

		return new WePackage(data, magic, start, entries);
	}

	public String version()
	{
		return version;
	}

	/** 条目表结束处：条目偏移的基准，也是数据区起点。 */
	public int dataStart()
	{
		return dataStart;
	}

	public List<String> names()
	{
		return List.copyOf(entries.keySet());
	}

	public Entry entry(String name)
	{
		return entries.get(name);
	}

	/** 按名字取一段内容；名字不存在返回 {@code null}。 */
	public byte[] read(String name)
	{
		Entry entry = entries.get(name);

		if(entry == null)
			return null;

		byte[] out = new byte[entry.length()];
		System.arraycopy(data, entry.offset(), out, 0, entry.length());
		return out;
	}

	/** 按后缀筛条目（大小写不敏感），例如 {@code ".tex"}。 */
	public List<Entry> withSuffix(String suffix)
	{
		List<Entry> out = new ArrayList<>();

		for(Entry entry : entries.values())
			if(entry.name().toLowerCase().endsWith(suffix.toLowerCase()))
				out.add(entry);

		return out;
	}

	/** 小端游标；越界一律抛 {@link IOException}，不返回半截数据。 */
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
				throw new IOException("包在 " + position + " 处提前结束");

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

			String value =
				new String(data, position, length, StandardCharsets.UTF_8);
			position += length;
			return value;
		}
	}
}
