/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient;

import java.util.OptionalDouble;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * Custom render layers for the Wurst shader pipelines.
 *
 * <p>
 * 1.21.9 still builds render layers out of {@code RenderType.CompositeState}
 * and {@code RenderStateShard} constants ({@code RenderSetup} and the
 * {@code rendertype} package only arrive in 1.21.11), so the depth test, cull
 * and blend behaviour lives in the pipeline while layering, line width and
 * output target stay in the composite state.
 */
public final class WurstRenderLayers extends RenderStateShard
{
	private WurstRenderLayers()
	{
		super("wurst", () -> {}, () -> {});
	}
	
	/**
	 * Similar to {@link RenderType#lines()}, but with line width 2.
	 */
	public static final RenderType.CompositeRenderType LINES =
		createLines("wurst:lines", WurstShaderPipelines.DEPTH_TEST_LINES);
	
	/**
	 * Similar to {@link RenderType#lines()}, but with line width 2 and no
	 * depth test.
	 */
	public static final RenderType.CompositeRenderType ESP_LINES =
		createLines("wurst:esp_lines", WurstShaderPipelines.ESP_LINES);
	
	/**
	 * Similar to {@link RenderType#debugQuads()}, but with culling enabled.
	 */
	public static final RenderType.CompositeRenderType QUADS = RenderType
		.create("wurst:quads", 1536, false, true, WurstShaderPipelines.QUADS,
			RenderType.CompositeState.builder()
				.createCompositeState(false));
	
	/**
	 * Similar to {@link RenderType#debugQuads()}, but with culling enabled
	 * and no depth test.
	 */
	public static final RenderType.CompositeRenderType ESP_QUADS =
		RenderType.create("wurst:esp_quads", 1536, false, true,
			WurstShaderPipelines.ESP_QUADS,
			RenderType.CompositeState.builder()
				.createCompositeState(false));
	
	/**
	 * Similar to {@link RenderType#debugQuads()}, but with no depth test.
	 */
	public static final RenderType.CompositeRenderType ESP_QUADS_NO_CULLING =
		RenderType.create("wurst:esp_quads_no_culling", 1536, false, true,
			WurstShaderPipelines.ESP_QUADS_NO_CULLING,
			RenderType.CompositeState.builder()
				.setLightmapState(LIGHTMAP).createCompositeState(false));
	
	private static RenderType.CompositeRenderType createLines(String name,
		RenderPipeline pipeline)
	{
		return RenderType.create(name, 1536, false, true, pipeline,
			RenderType.CompositeState.builder()
				.setLineState(new LineStateShard(OptionalDouble.of(2)))
				.setLayeringState(VIEW_OFFSET_Z_LAYERING)
				.setOutputState(ITEM_ENTITY_TARGET)
				.createCompositeState(false));
	}
	
	/**
	 * Returns either {@link #QUADS} or {@link #ESP_QUADS} depending on the
	 * value of {@code depthTest}.
	 */
	public static RenderType getQuads(boolean depthTest)
	{
		return depthTest ? QUADS : ESP_QUADS;
	}
	
	/**
	 * Returns either {@link #LINES} or {@link #ESP_LINES} depending on the
	 * value of {@code depthTest}.
	 */
	public static RenderType getLines(boolean depthTest)
	{
		return depthTest ? LINES : ESP_LINES;
	}
	
	/**
	 * Returns either {@link #LINES} or {@link #ESP_LINES} depending on the
	 * value of {@code depthTest}. Line strip variant.
	 */
	public static RenderType getLineStrip(boolean depthTest)
	{
		return depthTest ? LINES : ESP_LINES;
	}
}
