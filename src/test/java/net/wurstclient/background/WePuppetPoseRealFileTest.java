/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * 蒙皮数学的两条自证（真文件、真动画）。
 *
 * <p>
 * ① <b>绑定姿势必须复现网格</b>：动画第 0 帧就是绑定姿势（上一轮的测试实测每根轨道的第 0 帧
 * 位置等于它的绑定平移），所以 {@code skin} 应当是单位矩阵、蒙皮后的顶点要回到原位。
 * 这一条对矩阵约定（行向量、平移在第 4 行、四元数顺序、蒙皮公式的乘法次序）**极易证伪**
 * —— 只要哪一处反了，顶点就会散开，所以它是这一层的判据。
 * </p>
 *
 * <p>
 * ② <b>动画要真的动</b>：沿时间采样，打印顶点相对绑定姿势的最大位移 —— 眨眼那一段应当
 * 明显大于 0（否则要么采样读错了，要么这段动画跟这一层无关）。
 * </p>
 */
final class WePuppetPoseRealFileTest
{
	private static final String DEFAULT_PACKAGE =
		"C:\\Program Files (x86)\\Steam\\steamapps\\workshop\\content\\431960"
			+ "\\2989215532\\scene.pkg";

	private static final String MESH = "models/真寻_眼2_puppet.mdl";
	private static final long WANTED_ANIMATION = 138;

	private static Path packageFile()
	{
		String override = System.getProperty("wurstb.scene.pkg");
		return Path.of(override != null ? override : DEFAULT_PACKAGE);
	}

	@Test
	void bindPoseReproducesTheMeshAndTheAnimationMovesIt() throws Exception
	{
		Path file = packageFile();
		Assumptions.assumeTrue(Files.isRegularFile(file),
			"no Wallpaper Engine scene installed at " + file);

		byte[] bytes = WePackage.read(file).read(MESH);
		assertNotNull(bytes, "包里没有 " + MESH);

		WePuppetMesh mesh = WePuppetMesh.parse(bytes);
		WePuppetRig rig = WePuppetRig.parse(bytes);

		assertNotNull(mesh, "网格解不出来");
		assertNotNull(rig, "骨骼解不出来");

		WePuppetRig.Animation animation = rig.byId(WANTED_ANIMATION);

		assertNotNull(animation, "没有动画 " + WANTED_ANIMATION);
		System.out.println("[Probe] 网格 " + mesh.vertexCount() + " 顶点 / "
			+ mesh.triangleCount() + " 三角形；骨骼 " + rig.boneCount()
			+ "；动画 " + animation.id() + " fps=" + animation.fps() + " frames="
			+ animation.frames());

		float[] out = new float[mesh.vertexCount() * 2];
		float[] positions = mesh.positions();

		// ① 绑定姿势
		WePuppetPose.skin(mesh, WePuppetPose.skinMatrices(rig, animation, 0), out);
		double bindDelta = maxDelta(positions, out);
		System.out.println("[Probe] t=0（绑定姿势）最大顶点位移 = "
			+ String.format("%.4f", bindDelta) + " px");
		assertTrue(bindDelta < 1.0,
			"绑定姿势应当复现网格，实测最大位移 " + bindDelta + " px");

		// ② 沿时间采样：看这段动画到底动不动、什么时候动
		double peak = 0;
		float peakAt = 0;
		StringBuilder table = new StringBuilder();

		for(int step = 0; step <= 40; step++)
		{
			float seconds = animation.duration() * step / 40F;
			WePuppetPose.skin(mesh,
				WePuppetPose.skinMatrices(rig, animation, seconds), out);
			double delta = maxDelta(positions, out);

			if(delta > peak)
			{
				peak = delta;
				peakAt = seconds;
			}

			if(step % 4 == 0)
				table.append(String.format("%.2fs:%.1f  ", seconds, delta));
		}

		System.out.println("[Probe] 位移随时间的采样（px）：" + table);
		System.out.println("[Probe] 最大位移 " + String.format("%.1f", peak)
			+ " px，出现在 " + String.format("%.2f", peakAt) + " s");

		assertTrue(peak > 1.0,
			"这段动画应当让顶点动起来，实测最大位移只有 " + peak + " px");
	}

	private static double maxDelta(float[] a, float[] b)
	{
		double max = 0;

		for(int i = 0; i < a.length / 2; i++)
		{
			double dx = a[i * 2] - b[i * 2];
			double dy = a[i * 2 + 1] - b[i * 2 + 1];
			max = Math.max(max, Math.sqrt(dx * dx + dy * dy));
		}

		return max;
	}
}
