/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * 真实木偶网格上的骨骼与动画（{@link WePuppetRig}）。
 *
 * <p>
 * 这一条同时校验两件事：<b>格式抄对了</b>（骨骼表读完必须正好落在 {@code sectionEnd} 上，
 * 落不上 {@link WePuppetRig#parse} 就返回 null），以及<b>场景要的那个动画真的在里面</b>
 * —— 场景对象上写着 {@code animationlayers[0].animation = 138}，这里就要在 {@code MDLA}
 * 里找到 id 138 且轨道数等于骨骼数。
 * </p>
 */
final class WePuppetRigRealFileTest
{
	private static final String DEFAULT_PACKAGE =
		"C:\\Program Files (x86)\\Steam\\steamapps\\workshop\\content\\431960"
			+ "\\2989215532\\scene.pkg";

	private static final String MESH = "models/真寻_眼2_puppet.mdl";

	/** 场景里 {@code 真寻_眼2} 的 {@code animationlayers[0].animation}。 */
	private static final long WANTED_ANIMATION = 138;

	private static Path packageFile()
	{
		String override = System.getProperty("wurstb.scene.pkg");
		return Path.of(override != null ? override : DEFAULT_PACKAGE);
	}

	@Test
	void itReadsTheBonesAndTheAnimationTheSceneAsksFor() throws Exception
	{
		Path file = packageFile();
		Assumptions.assumeTrue(Files.isRegularFile(file),
			"no Wallpaper Engine scene installed at " + file);

		WePackage pkg = WePackage.read(file);
		byte[] mesh = pkg.read(MESH);
		assertNotNull(mesh, "包里没有 " + MESH);

		WePuppetRig rig = WePuppetRig.parse(mesh);
		assertNotNull(rig, "骨骼表走不到 sectionEnd —— 说明字段顺序抄错了");

		System.out.println("[Probe] " + MESH + " = " + mesh.length + " bytes");
		System.out.println("[Probe] 骨骼 " + rig.boneCount() + " 根，动画 "
			+ rig.animations().size() + " 段");

		assertEquals(34, rig.boneCount());

		// 父索引必须都在自己之前（文档：parents come first）—— 这条也正是辨真伪的判据：
		// flags/parent 两种顺序里只有一种能让第一根骨骼的父是 0xFFFFFFFF
		int roots = 0;

		for(int i = 0; i < rig.boneCount(); i++)
		{
			int parent = rig.bones().get(i).parent();

			assertTrue(parent < i || parent == -1,
				"骨骼 " + i + " 的父索引 " + parent + " 不在自己之前");

			if(parent == -1)
				roots++;
		}

		assertEquals(1, roots, "只该有一根根骨骼");

		System.out.println("[Probe] 前几根骨骼：");

		for(int i = 0; i < Math.min(6, rig.boneCount()); i++)
		{
			WePuppetRig.Bone bone = rig.bones().get(i);

			System.out.println("[Probe]   [" + i + "] 「" + bone.name()
				+ "」 parent=" + bone.parent() + " flags=" + bone.flags()
				+ " bind 平移=(" + bone.bindMatrix()[12] + ", "
				+ bone.bindMatrix()[13] + ", " + bone.bindMatrix()[14] + ")");
		}

		// ------------------------------------------------------------------
		// 动画段（MDLA）：第一段就是场景要的 138，实测头为
		//   +17 u64 id=138、+25 「动画 1」、+34 "loop"、+39 f32 fps=60.0、
		//   +43 u32 frames=600、+47 flags=0、+51 trackCount=34、
		//   +55 起每根骨骼 [u32 flags][u32 blob=36*(F+1)][36*(F+1) 字节采样]
		// 第二段之后的头可能对不齐（每段动画后面还有版本相关的附加块，布局没有权威
		// 说明），所以解析器保留已读出的段、不再往后走 —— 场景要的这段在第一段。
		// ------------------------------------------------------------------
		WePuppetRig.Animation wanted = rig.byId(WANTED_ANIMATION);

		assertNotNull(wanted, "MDLA 里没有场景要的动画 id " + WANTED_ANIMATION);
		assertEquals(rig.boneCount(), wanted.tracks().size(), "轨道数 = 骨骼数");

		for(WePuppetRig.Track track : wanted.tracks())
			assertEquals(9 * (wanted.frames() + 1), track.samples().length,
				"每帧 9 个 float、共 F+1 帧");

		System.out.println("[Probe] 目标动画 id=" + wanted.id() + " 名字=「"
			+ wanted.name() + "」 mode=" + wanted.mode() + " fps=" + wanted.fps()
			+ " frames=" + wanted.frames() + " 时长=" + wanted.duration()
			+ "s 轨道=" + wanted.tracks().size());

		// 第 0 帧的**位置**应当等于骨骼的绑定平移（绑定位姿就是这个动画的第 0 帧）——
		// 这一条同时校验骨骼矩阵与采样偏移对不对
		int matching = 0;

		for(int i = 0; i < rig.boneCount(); i++)
		{
			WePuppetRig.Bone bone = rig.bones().get(i);
			WePuppetRig.Track track = wanted.tracks().get(i);
			double distance = Math.abs(track.sample(0, 0) - bone.bindMatrix()[12])
				+ Math.abs(track.sample(0, 1) - bone.bindMatrix()[13])
				+ Math.abs(track.sample(0, 2) - bone.bindMatrix()[14]);

			if(distance < 0.05)
				matching++;
			else if(i < 3)
				System.out.println("[Probe]   [" + i + "] 第 0 帧位置 ("
					+ track.sample(0, 0) + ", " + track.sample(0, 1) + ", "
					+ track.sample(0, 2) + ") vs 绑定平移 ("
					+ bone.bindMatrix()[12] + ", " + bone.bindMatrix()[13]
					+ ", " + bone.bindMatrix()[14] + ")");
		}

		System.out.println("[Probe] 第 0 帧位置 = 绑定平移的骨骼数：" + matching + "/"
			+ rig.boneCount());
		assertTrue(matching >= rig.boneCount() - 4,
			"第 0 帧位置应当基本等于绑定平移，实际只有 " + matching + " 根对上");

		WePuppetRig.Track first = wanted.tracks().get(0);
		StringBuilder sample = new StringBuilder();

		for(int index = 0; index < 9; index++)
			sample.append(String.format("%9.4f", first.sample(0, index)));

		System.out.println("[Probe] 第 0 帧骨骼 0 的 9 个分量"
			+ "（位置 xyz、欧拉 xyz、缩放 xyz）：" + sample);
	}
}
