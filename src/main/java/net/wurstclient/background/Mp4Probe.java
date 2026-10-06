/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * MP4 盒子的最小扫描：只回答「有没有视频轨道、是什么编码、多少个样本」。
 *
 * <p>
 * 视频背景曾经靠 JCodec 的解复用器回答这三个问题（见
 * {@code BackgroundVideo.probe}）。改用原生 FFmpeg 之后，解码这一侧不再需要纯
 * Java 的 MP4 解析器，但「放不了时到底为什么」仍然需要它：解码器只会说"打不开"，
 * 而用户需要知道的是这个文件里根本没有视频轨道、还是编码这台机器放不了。所以这里
 * 留一个只读文件头的小扫描器。
 * </p>
 *
 * <p>
 * 三个字段的位置都是 MP4 规范里固定的（偏移都相对盒子自身的起点）：
 * </p>
 *
 * <ul>
 * <li>{@code hdlr}：version/flags 在 +8，handler_type 在 <b>+16</b>
 * （"vide" 表示这条轨道是视频）</li>
 * <li>{@code stsd}：version/flags 在 +8、entry_count 在 +12，第一个样本条目从
 * +16 开始，它的 format（fourcc）在 <b>+20</b></li>
 * <li>{@code stsz}：version/flags 在 +8、sample_size 在 +12、sample_count 在
 * <b>+16</b>（视频轨道的一个样本就是一帧，与旧 JCodec 那条路上
 * {@code DemuxerTrackMeta.getTotalFrames()} 是同一个数）</li>
 * </ul>
 *
 * <p>
 * 只按偏移读几十个字节，不把文件读进内存（一段 4K 壁纸是上百 MB），也绝不抛异常
 * 给调用方——那是在探测里，那里连 {@link Throwable} 都不许漏出去。
 * </p>
 */
final class Mp4Probe
{
	/** 盒子套盒子：moov / trak / mdia / minf / stbl 是五层，留点余量。 */
	private static final int MAX_DEPTH = 8;

	/** 盒子头：4 字节长度 + 4 字节类型。 */
	private static final int BOX_HEADER = 8;

	/** 一个 4GB 以上的盒子当坏文件处理：真壁纸不会这样。 */
	private static final long MAX_BOX_SIZE = 1L << 32;

	private Mp4Probe()
	{
	}

	/**
	 * 扫描结果。
	 *
	 * @param parsed
	 *            盒子结构读得通（读不通时后面三个字段没有意义）
	 * @param hasVideoTrack
	 *            有一条 handler_type 是 "vide" 的轨道
	 * @param fourcc
	 *            视频轨道第一个样本条目的编码，未知时是空串
	 * @param sampleCount
	 *            视频轨道的样本（帧）数，未知时是 0
	 */
	record Result(boolean parsed, boolean hasVideoTrack, String fourcc,
		int sampleCount)
	{
		static Result unparsable()
		{
			return new Result(false, false, "", 0);
		}
	}

	/** 读不出来一律返回 {@link Result#unparsable()}，不抛异常。 */
	static Result read(Path file)
	{
		if(file == null)
			return Result.unparsable();

		try(FileChannel channel = FileChannel.open(file,
			StandardOpenOption.READ))
		{
			return read(channel);

		}catch(IOException | RuntimeException e)
		{
			return Result.unparsable();
		}
	}

	private static Result read(FileChannel channel) throws IOException
	{
		for(Box box : boxes(channel, 0, channel.size(), 0))
		{
			if(!"moov".equals(box.type()))
				continue;

			// 有 moov、但它里面没有视频轨道：这才是 NO_VIDEO_TRACK。连 moov 都没有
			// 的文件（只有 ftyp 的头）是「读不通」，那是另一回事
			Result video = videoTrack(channel, box);
			return video == null ? new Result(true, false, "", 0) : video;
		}

		return Result.unparsable();
	}

	// ------------------------------------------------------------------
	// 轨道
	// ------------------------------------------------------------------

	/** moov 里第一条视频轨道；没有视频轨道返回 null。 */
	private static Result videoTrack(FileChannel channel, Box moov)
		throws IOException
	{
		for(Box trak : boxes(channel, moov.payload(), moov.end(),
			moov.depth()))
		{
			if(!"trak".equals(trak.type()))
				continue;

			Result found = readTrak(channel, trak);

			if(found != null)
				return found;
		}

		return null;
	}

	/** 一条 trak：不是视频轨道返回 null。 */
	private static Result readTrak(FileChannel channel, Box trak)
		throws IOException
	{
		boolean video = false;
		String fourcc = "";
		int sampleCount = 0;

		for(Box mdia : boxes(channel, trak.payload(), trak.end(),
			trak.depth()))
		{
			if(!"mdia".equals(mdia.type()))
				continue;

			for(Box child : boxes(channel, mdia.payload(), mdia.end(),
				mdia.depth()))
			{
				if("hdlr".equals(child.type()))
					video = "vide".equals(handlerType(channel, child));
				else if("minf".equals(child.type()))
				{
					String[] info = readStbl(channel, child);
					fourcc = info[0];
					sampleCount = Integer.parseInt(info[1]);
				}
			}
		}

		if(!video)
			return null;

		return new Result(true, true, fourcc, sampleCount);
	}

	/**
	 * minf 里的 stbl 里的 stsd / stsz。
	 *
	 * @return {@code [fourcc, sampleCount]}，读不到时是空串与 "0"
	 */
	private static String[] readStbl(FileChannel channel, Box minf)
		throws IOException
	{
		String fourcc = "";
		int sampleCount = 0;

		for(Box stbl : boxes(channel, minf.payload(), minf.end(),
			minf.depth()))
		{
			if(!"stbl".equals(stbl.type()))
				continue;

			for(Box child : boxes(channel, stbl.payload(), stbl.end(),
				stbl.depth()))
			{
				if("stsd".equals(child.type()))
					fourcc = sampleFormat(channel, child);
				else if("stsz".equals(child.type()))
					sampleCount = sampleCount(channel, child);
			}
		}

		return new String[]{fourcc, Integer.toString(sampleCount)};
	}

	/** stsd 里第一个样本条目的 format：payload + version/flags + entry_count + 条目长度。 */
	private static String sampleFormat(FileChannel channel, Box stsd)
		throws IOException
	{
		long offset = stsd.payload() + 12;

		if(offset + 4 > stsd.end())
			return "";

		return type(channel, offset);
	}

	/** stsz 的 sample_count：payload + version/flags + sample_size。 */
	private static int sampleCount(FileChannel channel, Box stsz)
		throws IOException
	{
		long offset = stsz.payload() + 8;

		if(offset + 4 > stsz.end())
			return 0;

		return readInt(channel, offset);
	}

	/** hdlr 的 handler_type：payload + version/flags + pre_defined。 */
	private static String handlerType(FileChannel channel, Box hdlr)
		throws IOException
	{
		long offset = hdlr.payload() + 8;

		if(offset + 4 > hdlr.end())
			return "";

		return type(channel, offset);
	}

	// ------------------------------------------------------------------
	// 盒子
	// ------------------------------------------------------------------

	/**
	 * 一个盒子在文件里的位置。
	 *
	 * @param start
	 *            盒子起点（长度字段所在处）
	 * @param payload
	 *            内容起点（普通的 +8，64 位长度的 +16）
	 * @param end
	 *            盒子结束（不含）
	 * @param depth
	 *            嵌套层数，用来兜住畸形结构
	 */
	private record Box(String type, long start, long payload, long end,
		int depth)
	{
	}

	/**
	 * 枚举 [from, to) 里的同级盒子。结构不对时停止枚举，不抛异常。
	 *
	 * <p>
	 * 传进来的范围必须是**子盒子**的范围（{@link Box#payload()} 到
	 * {@link Box#end()}），别把父盒子自己的头也算进去。
	 * </p>
	 */
	private static List<Box> boxes(FileChannel channel, long from, long to,
		int depth) throws IOException
	{
		List<Box> boxes = new ArrayList<>();

		if(depth > MAX_DEPTH)
			return boxes;

		long offset = from;

		while(offset + BOX_HEADER <= to)
		{
			long size = readInt(channel, offset) & 0xFFFFFFFFL;
			String type = type(channel, offset + 4);
			long payload = offset + BOX_HEADER;

			if(size == 1)
			{
				// 长度字段写 1：真正的长度是后面 8 字节的 64 位整数
				if(offset + 16 > to)
					break;

				size = readLong(channel, offset + BOX_HEADER);
				payload = offset + 16;

			}else if(size == 0)
				// 长度写 0：一直到父范围末尾
				size = to - offset;

			if(size < payload - offset || size > MAX_BOX_SIZE)
				break;

			long end = Math.min(to, offset + size);

			if(end <= offset)
				break;

			boxes.add(new Box(type, offset, payload, end, depth + 1));
			offset = end;
		}

		return boxes;
	}

	// ------------------------------------------------------------------
	// 读文件
	// ------------------------------------------------------------------

	private static int readInt(FileChannel channel, long offset)
		throws IOException
	{
		return ByteBuffer.wrap(read(channel, offset, 4)).getInt();
	}

	private static long readLong(FileChannel channel, long offset)
		throws IOException
	{
		return ByteBuffer.wrap(read(channel, offset, 8)).getLong();
	}

	/** 4 个 ASCII 字符的盒子类型（统一小写）；读不成 4 个可打印字符时返回空串。 */
	private static String type(FileChannel channel, long offset)
		throws IOException
	{
		byte[] data = read(channel, offset, 4);

		for(byte b : data)
			if(b < 0x20 || b > 0x7E)
				return "";

		return new String(data, StandardCharsets.US_ASCII)
			.toLowerCase(Locale.ROOT);
	}

	private static byte[] read(FileChannel channel, long offset, int length)
		throws IOException
	{
		byte[] data = new byte[length];
		ByteBuffer buffer = ByteBuffer.wrap(data);

		while(buffer.hasRemaining())
		{
			int n = channel.read(buffer, offset + buffer.position());

			if(n < 0)
				throw new IOException("文件在 " + offset + " 处就结束了");

			if(n == 0)
				throw new IOException(
					"读不到 " + offset + " 处的 " + length + " 个字节");
		}

		return data;
	}
}
