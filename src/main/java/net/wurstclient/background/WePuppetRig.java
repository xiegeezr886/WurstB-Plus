/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 木偶网格的**骨骼与动画**（{@code MDLS} + {@code MDLA} 两段）。
 *
 * <p>
 * 字段顺序不是猜的，也不是本项目校的：抄自一份独立实现的反向工程文档（带 WE 二进制里的
 * 地址），全文见 {@code docs/puppet-mdl-format.md}，出处是
 * <a href="https://cdn.jsdelivr.net/gh/deepratna-awale/open-wallpaper-engine-mac@main/docs/models-plan.md">
 * open-wallpaper-engine-mac 的 models-plan.md</a>。要点：</p>
 *
 * <pre>
 * MDLS: cstr tag; u32 sectionEnd; u32 boneCount(≤128)
 *       bone[]: cstr name; u32 flags; u32 parent(0xFFFFFFFF=根); capped(64) localBindMatrix; cstr propsJSON
 * MDLA: cstr tag; u32 sectionEnd; i32 animCount
 *       anim[]: u64 id; cstr name; cstr mode("loop"/"mirror"/"single"); f32 fps;
 *               u32 frames F; u32 flags; i32 trackCount(=骨骼数)
 *               track[]: u32 trackFlags; blob 36*(F+1)   ← 每帧 9 个 float：
 *                        位置 xyz、欧拉角 xyz（弧度）、缩放 xyz
 * </pre>
 *
 * <p>
 * <b>自校验</b>：按上面的顺序读 {@code boneCount} 根骨骼之后，游标必须**正好**落在
 * {@code sectionEnd} 上；拿不准的地方（flags/parent 的先后）两种顺序都试，取能落在
 * {@code sectionEnd} 上的那一种。落不上就返回 {@code null}，调用方退回静态渲染 ——
 * 宁可画绑定姿势，也不按错的数据乱摆（这是本项目一贯的规矩）。</p>
 *
 * <p>
 * 之前卡住的原因也记在这里：我按 010 模板猜「9 字节头 + u32 长度」，于是怎么走都到不了
 * {@code MDLA}（走到 513,228 就断）。真实布局是 {@code cstr 名字 + flags + parent +
 * capped(64) 矩阵 + propsJSON}，且 {@code propsJSON} 长短不一 —— 前面九根骨骼的 203 字节
 * "步长"只是巧合同长。</p>
 */
public record WePuppetRig(List<Bone> bones, List<Animation> animations)
{
	/** 一根骨骼：名字、标志位、父索引（-1 = 根）、16 个 float 的 local 绑定矩阵。 */
	public record Bone(String name, int flags, int parent, float[] bindMatrix)
	{}

	/**
	 * 一段动画。
	 *
	 * @param frames
	 *            帧数 F；采样是 F+1 帧（含末帧）
	 * @param tracks
	 *            每根骨骼一条，长度必等于骨骼数
	 */
	public record Animation(long id, String name, String mode, float fps,
		int frames, List<Track> tracks)
	{
		/** 时长（秒）：文档写的 {@code duration = frames / fps}。 */
		public float duration()
		{
			return fps > 0 ? frames / fps : 0;
		}
	}

	/**
	 * 一根骨骼的采样轨道。
	 *
	 * @param samples
	 *            每帧 9 个 float：位置 xyz、欧拉角 xyz（弧度）、缩放 xyz
	 */
	public record Track(int flags, float[] samples)
	{
		public int frames()
		{
			return samples.length / 9 - 1;
		}

		/** 第 {@code frame} 帧的第 {@code index} 个分量（0..8）。 */
		public float sample(int frame, int index)
		{
			return samples[frame * 9 + index];
		}
	}

	private static final int NO_PARENT = -1;

	public static WePuppetRig parse(byte[] data)
	{
		if(data == null)
			return null;

		int mdls = indexOf(data, "MDLS", 0);

		if(mdls < 0)
			return null;

		Cursor cursor = new Cursor(data, mdls);
		String tag = cursor.cstring();

		if(tag == null || !tag.startsWith("MDLS"))
			return null;

		int sectionEnd = cursor.int32();

		if(sectionEnd <= 0 || sectionEnd > data.length)
			return null;

		int boneCount = cursor.int32();

		if(boneCount <= 0 || boneCount > 128)
			return null;

		List<Bone> bones = readBones(cursor, boneCount, sectionEnd);

		if(bones == null)
			return null;

		// MDLA 就在 MDLS 的 sectionEnd 上
		List<Animation> animations = readAnimations(data, sectionEnd, boneCount);

		return new WePuppetRig(List.copyOf(bones),
			animations == null ? List.of() : animations);
	}

	/**
	 * 读骨骼表。
	 *
	 * <p>
	 * 判据不是"正好落在 {@code sectionEnd}"—— 实测这个文件读完之后还差 2,870 字节，那是
	 * {@code MDLS} v2 以上的**可选块**（links／绑定矩阵／约束／IK 集合／每骨骼的
	 * {@code {vec3, mat4}} 与 u32 数组；这份文件是 v4，所以还有第三个 u32 数组与约束标志），
	 * 我们不需要它们。真正能辨真伪的是**父索引必须在自己之前**这条结构不变量：
	 * flags/parent 两种顺序都试，取满足该不变量更多的那一种（第一根骨骼的父是
	 * {@code 0xFFFFFFFF}，只有顺序正确时才成立）。
	 * </p>
	 */
	private static List<Bone> readBones(Cursor cursor, int boneCount,
		int sectionEnd)
	{
		List<Bone> best = null;
		int bestScore = -1;

		for(boolean parentFirst : new boolean[]{false, true})
		{
			Cursor attempt = cursor.copy();
			List<Bone> bones = new ArrayList<>(boneCount);
			int score = 0;
			boolean ok = true;

			for(int i = 0; i < boneCount; i++)
			{
				String name = attempt.cstring();
				int first = attempt.int32();
				int second = attempt.int32();

				if(name == null)
				{
					ok = false;
					break;
				}

				int flags = parentFirst ? second : first;
				int rawParent = parentFirst ? first : second;
				int parent = rawParent == 0xFFFFFFFF ? NO_PARENT : rawParent;

				if(parent == NO_PARENT ? i == 0 : parent >= 0 && parent < i)
					score++;

				int matrixBytes = attempt.int32();

				if(matrixBytes < 64 || matrixBytes > 4096)
				{
					ok = false;
					break;
				}

				float[] matrix = new float[16];

				for(int f = 0; f < 16; f++)
					matrix[f] = attempt.float32();

				// 矩阵块比 64 字节长时，多出来的部分跳过
				for(int skip = 64; skip < matrixBytes; skip++)
					attempt.uint8();

				if(attempt.cstring() == null) // propsJSON
				{
					ok = false;
					break;
				}

				bones.add(new Bone(name, flags, parent, matrix));
			}

			// 读得完、没越过段尾、且结构不变量的满足度更高
			if(ok && attempt.position() <= sectionEnd && score > bestScore)
			{
				best = List.copyOf(bones);
				bestScore = score;
			}
		}

		// 全部骨骼都该满足"父在自己之前"；少一根就不认
		return best != null && bestScore == boneCount ? best : null;
	}

	/**
	 * 读动画段。轨道的 blob 长度必须等于 {@code 36 × (帧数+1)}，且轨道数等于骨骼数 ——
	 * 这两条对不上就整段作废（只影响动画，静态绑定姿势照画）。
	 */
	private static List<Animation> readAnimations(byte[] data, int mdla,
		int boneCount)
	{
		if(mdla + 13 > data.length || indexOf(data, "MDLA", mdla) != mdla)
			return null;

		Cursor cursor = new Cursor(data, mdla);
		String tag = cursor.cstring();

		if(tag == null || !tag.startsWith("MDLA"))
			return null;

		int sectionEnd = cursor.int32();

		if(sectionEnd <= 0 || sectionEnd > data.length)
			return null;

		int animationCount = cursor.int32();

		if(animationCount <= 0 || animationCount > 64)
			return null;

		List<Animation> animations = new ArrayList<>(animationCount);

		try
		{
			for(int a = 0; a < animationCount; a++)
			{
				long id = cursor.uint32() | (long)cursor.uint32() << 32;
				String name = cursor.cstring();
				String mode = cursor.cstring();
				float fps = cursor.float32();
				int frames = cursor.int32();
				cursor.int32(); // flags
				int trackCount = cursor.int32();

				if(name == null || mode == null || frames < 0 || frames > 100000
					|| trackCount != boneCount)
					return null;

				List<Track> tracks = new ArrayList<>(trackCount);

				for(int t = 0; t < trackCount; t++)
				{
					int flags = cursor.int32();
					int bytes = cursor.int32();

					if(bytes != 36 * (frames + 1))
						return null;

					float[] samples = new float[9 * (frames + 1)];

					for(int i = 0; i < samples.length; i++)
						samples[i] = cursor.float32();

					tracks.add(new Track(flags, samples));
				}

				animations.add(new Animation(id, name,
					mode == null ? "" : mode, fps, frames, List.copyOf(tracks)));
			}

		}catch(RuntimeException e)
		{
			return null;
		}

		return List.copyOf(animations);
	}

	/** 骨骼数。 */
	public int boneCount()
	{
		return bones.size();
	}

	/** 按 id 找动画（场景对象的 {@code animationlayers[].animation} 就是这个 id）。 */
	public Animation byId(long id)
	{
		for(Animation animation : animations)
			if(animation.id() == id)
				return animation;

		return null;
	}

	private static int indexOf(byte[] data, String needle, int from)
	{
		byte[] pattern = needle.getBytes(StandardCharsets.US_ASCII);

		outer: for(int at = Math.max(0, from); at + pattern.length <= data.length;
			at++)
		{
			for(int i = 0; i < pattern.length; i++)
				if(data[at + i] != pattern[i])
					continue outer;

			return at;
		}

		return -1;
	}

	/** 越界一律抛 {@link IllegalStateException}：解析失败由上层兜住。 */
	private static final class Cursor
	{
		private final byte[] data;
		private int position;

		Cursor(byte[] data, int position)
		{
			this.data = data;
			this.position = position;
		}

		Cursor copy()
		{
			return new Cursor(data, position);
		}

		int position()
		{
			return position;
		}

		int uint8()
		{
			if(position + 1 > data.length)
				throw new IllegalStateException("越界");

			return data[position++] & 0xFF;
		}

		int int32()
		{
			if(position + 4 > data.length)
				throw new IllegalStateException("越界");

			int value = (data[position] & 0xFF) | (data[position + 1] & 0xFF) << 8
				| (data[position + 2] & 0xFF) << 16
				| (data[position + 3] & 0xFF) << 24;
			position += 4;
			return value;
		}

		long uint32()
		{
			return int32() & 0xFFFFFFFFL;
		}

		float float32()
		{
			return Float.intBitsToFloat(int32());
		}

		String cstring()
		{
			int start = position;
			int end = start;

			while(end < data.length && data[end] != 0)
				end++;

			if(end >= data.length || end - start > 1024)
				return null;

			String value = new String(data, start, end - start,
				StandardCharsets.UTF_8);
			position = end + 1;
			return value;
		}
	}
}
