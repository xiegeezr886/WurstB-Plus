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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import net.wurstclient.background.WePackage.Entry;

/**
 * Tests the Wallpaper Engine readers.
 *
 * <p>
 * The fixtures are built here rather than taken from a real workshop file:
 * those live on Steam, and the point of these tests is the structure handling -
 * the length prefixed strings, the entry table, and above all that entry
 * offsets are relative to the end of the table (getting that wrong does not
 * fail loudly, it silently returns random bytes).
 */
final class WePackageTest
{
	private static void int32(ByteArrayOutputStream out, int value)
	{
		out.write(value & 0xFF);
		out.write(value >>> 8 & 0xFF);
		out.write(value >>> 16 & 0xFF);
		out.write(value >>> 24 & 0xFF);
	}

	private static void string(ByteArrayOutputStream out, String value)
	{
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		int32(out, bytes.length);
		out.writeBytes(bytes);
	}

	private static byte[] fakePackage()
	{
		byte[] first = "TEXV0005-TEXI0001".getBytes(StandardCharsets.UTF_8);
		byte[] second = new byte[40];
		for(int i = 0; i < second.length; i++)
			second[i] = (byte)i;

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		string(out, "PKGV0013");
		int32(out, 2);

		// 表里存相对偏移，这里先占位，等知道表尾位置再回填
		string(out, "materials/花瓣.tex");
		int32(out, 0);
		int32(out, first.length);
		string(out, "scene.json");
		int32(out, first.length);
		int32(out, second.length);

		out.writeBytes(first);
		out.writeBytes(second);
		return out.toByteArray();
	}

	@Test
	void itReadsTheEntryTable() throws IOException
	{
		WePackage pkg = WePackage.parse(fakePackage());

		assertEquals("PKGV0013", pkg.version());
		assertEquals(2, pkg.names().size());
		assertEquals("materials/花瓣.tex", pkg.names().get(0));
		assertEquals("scene.json", pkg.names().get(1));
		assertTrue(pkg.dataStart() > 0);
	}

	/**
	 * 偏移是相对条目表结束处的：读到的内容必须与写进去的一致，而不是文件里
	 * 任意一段字节。
	 */
	@Test
	void entriesAreRelativeToTheEndOfTheTable() throws IOException
	{
		WePackage pkg = WePackage.parse(fakePackage());

		assertArrayEquals("TEXV0005-TEXI0001".getBytes(StandardCharsets.UTF_8),
			pkg.read("materials/花瓣.tex"));

		byte[] second = pkg.read("scene.json");
		assertEquals(40, second.length);
		assertEquals(0, second[0]);
		assertEquals(39, second[39]);
	}

	@Test
	void unknownNamesReadAsNull() throws IOException
	{
		assertNull(WePackage.parse(fakePackage()).read("nope.png"));
	}

	@Test
	void itFiltersBySuffix() throws IOException
	{
		WePackage pkg = WePackage.parse(fakePackage());

		assertEquals(1, pkg.withSuffix(".tex").size());
		assertEquals(1, pkg.withSuffix(".JSON").size());
		assertEquals(0, pkg.withSuffix(".frag").size());

		Entry entry = pkg.withSuffix(".tex").get(0);
		assertEquals(0, entry.offset() - pkg.dataStart());
	}

	@Test
	void itRefusesMalformedPackages()
	{
		ByteArrayOutputStream bad = new ByteArrayOutputStream();
		string(bad, "NOPE0001");
		int32(bad, 1);
		assertThrows(IOException.class,
			() -> WePackage.parse(bad.toByteArray()));

		// 条目数离谱
		ByteArrayOutputStream huge = new ByteArrayOutputStream();
		string(huge, "PKGV0013");
		int32(huge, 5_000_000);
		assertThrows(IOException.class,
			() -> WePackage.parse(huge.toByteArray()));

		// 偏移越界
		ByteArrayOutputStream over = new ByteArrayOutputStream();
		string(over, "PKGV0013");
		int32(over, 1);
		string(over, "a.tex");
		int32(over, 9999);
		int32(over, 10);
		assertThrows(IOException.class,
			() -> WePackage.parse(over.toByteArray()));

		// 半截字符串
		assertThrows(IOException.class,
			() -> WePackage.parse(new byte[]{4, 0, 0, 0, 'P', 'K'}));
	}

	// 贴图那部分搬到 WeTextureTest 了：那边的夹具按实测布局写
}
