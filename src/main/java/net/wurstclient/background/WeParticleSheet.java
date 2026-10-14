/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

/**
 * 粒子贴图的帧格：几列、几行、一共几帧。**数字来自 {@code .tex-json} 的声明**
 * （见 {@link WeTextureSheet}），不再从像素里量。
 *
 * <p>
 * 起因：{@code particle/fog/fog1} 是 8×8 共 64 帧、每帧 128×128 的雾动画。我原来把整张
 * 1024×1024 当一帧采样 —— 一颗雾粒子画出 64 团白烟，屏上就是那片格子状的光晕。WE 的
 * {@code genericparticle.vert} 走 {@code SPRITESHEET} 分支，按寿命取**一帧**。</p>
 *
 * <p>
 * 帧号语义与 WE 的 {@code ComputeSpriteFrame()}（{@code assets/shaders/common_particles.h}）
 * 逐字对齐：{@code currentFrame = floor(lifetime * numFrames)}，上界夹在
 * {@code numFrames - 1}；行列是行优先 —— 第 {@code f} 帧在
 * {@code (f mod 列数, f / 列数)}。</p>
 *
 * <p>
 * <b>还没做的</b>：WE 在 {@code SPRITESHEETBLEND} 打开时会在相邻两帧之间按
 * {@code frac(lifetime * numFrames)} 做一次混合（{@code genericparticle.frag} 里对
 * {@code v_TexCoord.xy} 与 {@code v_TexCoord.zw} 取 {@code mix}）。实测到的粒子材质里
 * 没有谁声明这个 combo，所以这里按"取一帧"画；要补的话就是同一颗画两次、权重
 * {@code 1-t} 与 {@code t}。</p>
 */
public record WeParticleSheet(int columns, int rows, int frames)
{
	/** 单帧（不是图集）。 */
	public static final WeParticleSheet SINGLE = new WeParticleSheet(1, 1, 1);

	/** 分格必须装得下声明的帧数，否则行优先取值会越界。 */
	public WeParticleSheet
	{
		if(columns < 1 || rows < 1)
			throw new IllegalArgumentException(
				"帧格不能为空：" + columns + "x" + rows);

		if(frames < 1 || frames > columns * rows)
			throw new IllegalArgumentException("帧数 " + frames + " 放不进 "
				+ columns + "x" + rows + " 个格子");
	}

	public static WeParticleSheet of(WeTextureSheet sheet)
	{
		if(sheet == null || !sheet.isSheet())
			return SINGLE;

		return new WeParticleSheet(sheet.columns(), sheet.rows(),
			sheet.frames());
	}

	public boolean isSheet()
	{
		return frames > 1;
	}

	/** 某一帧在贴图里的列/行。 */
	public int columnOf(int frame)
	{
		return frame % columns;
	}

	public int rowOf(int frame)
	{
		return frame / columns;
	}

	/**
	 * 寿命进度 → 帧号（与 {@code ComputeSpriteFrame} 同语义：{@code floor(lifetime * 帧数)}）。
	 */
	public int frameOf(float lifeFraction)
	{
		int frame = (int)Math.floor(lifeFraction * frames);
		return Math.max(0, Math.min(frames - 1, frame));
	}
}
