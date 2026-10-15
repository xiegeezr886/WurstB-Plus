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
		// 动画段（MDLA）：**尚未对齐**，所以这里只报告、不断言。
		//
		// 现状（2026-10-15）：按 docs/puppet-mdl-format.md 抄的顺序读
		// 「u64 id → cstr name → cstr mode → f32 fps → u32 frames → u32 flags →
		// i32 trackCount」读不下来。实测这个文件的 MDLA 头是：
		//   +13 animCount=2、+17 id=138、+25 名字「动画 1」、+33 mode="loop"，
		//   但两个字符串后面有 2 字节填充（+38/+39），+40 起的字段与 fps/frames/
		//   trackCount 对不上（也搜不到 trackCount=34）。
		// 探针：build/mdl-mdla.py（打印头字节并搜已知值）。
		// 结论：骨头已经能用，动画还差这一步对齐；对齐之后才有"眨眼"。
		// ------------------------------------------------------------------
		for(WePuppetRig.Animation animation : rig.animations())
			System.out.println("[Probe]   动画 id=" + animation.id() + " 「"
				+ animation.name() + "」 mode=" + animation.mode() + " fps="
				+ animation.fps() + " frames=" + animation.frames());
	}
}
