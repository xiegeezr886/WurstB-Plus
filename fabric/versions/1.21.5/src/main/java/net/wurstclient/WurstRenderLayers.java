/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
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
 * 1.21.5 builds render layers out of {@code RenderType.CompositeState} and
 * {@code RenderStateShard} constants, but the per-layer shader/depth/blend
 * state now lives in the {@link RenderPipeline} passed to
 * {@code RenderType.create()}. Only layering, line width and the output target
 * remain in the composite state.
 */
public final class WurstRenderLayers extends RenderStateShard
{
	private WurstRenderLayers()
	{
		super("wurst", () -> {}, () -> {});
	}
	
	/**
	 * Similar to {@link RenderType#debugLineStrip(double)}, but as a
	 * non-strip version with support for transparency.
	 *
	 * @implNote Just like {@link RenderType#debugLineStrip(double)}, this
	 *           layer doesn't support any other line width than 1px. Changing
	 *           the line width number does nothing.
	 */
	public static final RenderType.CompositeRenderType ONE_PIXEL_LINES =
		create("wurst:1px_lines", WurstShaderPipelines.ONE_PIXEL_LINES,
			OptionalDouble.of(1), false);
	
	/**
	 * Similar to {@link RenderType#debugLineStrip(double)}, but with support
	 * for transparency.
	 *
	 * @implNote Just like {@link RenderType#debugLineStrip(double)}, this
	 *           layer doesn't support any other line width than 1px. Changing
	 *           the line width number does nothing.
	 */
	public static final RenderType.CompositeRenderType ONE_PIXEL_LINE_STRIP =
		create("wurst:1px_line_strip",
			WurstShaderPipelines.ONE_PIXEL_LINE_STRIP, OptionalDouble.of(1),
			false);
	
	/**
	 * Similar to {@link RenderType#lines()}, but with line width 2.
	 */
	public static final RenderType.CompositeRenderType LINES =
		create("wurst:lines", WurstShaderPipelines.LINES,
			OptionalDouble.of(2), true);
	
	/**
	 * Similar to {@link RenderType#lines()}, but with line width 2 and no
	 * depth test.
	 */
	public static final RenderType.CompositeRenderType ESP_LINES =
		create("wurst:esp_lines", WurstShaderPipelines.ESP_LINES,
			OptionalDouble.of(2), true);
	
	/**
	 * Similar to {@link RenderType#lines()}, but as a line strip with line
	 * width 2.
	 */
	public static final RenderType.CompositeRenderType LINE_STRIP =
		create("wurst:line_strip", WurstShaderPipelines.LINE_STRIP,
			OptionalDouble.of(2), true);
	
	/**
	 * Similar to {@link RenderType#lines()}, but as a line strip with line
	 * width 2 and no depth test.
	 */
	public static final RenderType.CompositeRenderType ESP_LINE_STRIP =
		create("wurst:esp_line_strip", WurstShaderPipelines.ESP_LINE_STRIP,
			OptionalDouble.of(2), true);
	
	/**
	 * Similar to {@link RenderType#debugQuads()}, but with culling enabled.
	 */
	public static final RenderType.CompositeRenderType QUADS =
		create("wurst:quads", WurstShaderPipelines.QUADS, null, false);
	
	/**
	 * Similar to {@link RenderType#debugQuads()}, but with culling enabled
	 * and no depth test.
	 */
	public static final RenderType.CompositeRenderType ESP_QUADS =
		create("wurst:esp_quads", WurstShaderPipelines.ESP_QUADS, null, false);
	
	/**
	 * Similar to {@link RenderType#debugQuads()}, but with no depth test.
	 */
	public static final RenderType.CompositeRenderType ESP_QUADS_NO_CULLING =
		create("wurst:esp_quads_no_culling",
			WurstShaderPipelines.ESP_QUADS_NO_CULLING, null, false);
	
	/**
	 * Position/colour triangles with no depth test and no culling.
	 */
	public static final RenderType.CompositeRenderType ESP_TRIANGLES =
		create("wurst:esp_triangles", WurstShaderPipelines.ESP_TRIANGLES, null,
			false);
	
	/**
	 * Position/colour triangle strip with no depth test and no culling.
	 */
	public static final RenderType.CompositeRenderType ESP_TRIANGLE_STRIP =
		create("wurst:esp_triangle_strip",
			WurstShaderPipelines.ESP_TRIANGLE_STRIP, null, false);
	
	/**
	 * Position/colour debug lines with no depth test and no culling.
	 */
	public static final RenderType.CompositeRenderType ESP_DEBUG_LINES =
		create("wurst:esp_debug_lines", WurstShaderPipelines.ESP_DEBUG_LINES,
			null, false);
	
	/**
	 * Position/colour debug line strip with no depth test and no culling.
	 */
	public static final RenderType.CompositeRenderType ESP_DEBUG_LINE_STRIP =
		create("wurst:esp_debug_line_strip",
			WurstShaderPipelines.ESP_DEBUG_LINE_STRIP, null, false);
	
	private static RenderType.CompositeRenderType create(String name,
		RenderPipeline pipeline, OptionalDouble lineWidth,
		boolean itemEntityTarget)
	{
		RenderType.CompositeState.CompositeStateBuilder builder =
			RenderType.CompositeState.builder();
		
		if(lineWidth != null)
			builder.setLineState(new LineStateShard(lineWidth))
				.setLayeringState(VIEW_OFFSET_Z_LAYERING);
		
		if(itemEntityTarget)
			builder.setOutputState(ITEM_ENTITY_TARGET);
		
		return RenderType.create(name, 1536, false, true, pipeline,
			builder.createCompositeState(false));
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
	 * Returns either {@link #LINE_STRIP} or {@link #ESP_LINE_STRIP} depending
	 * on the value of {@code depthTest}.
	 */
	public static RenderType getLineStrip(boolean depthTest)
	{
		return depthTest ? LINE_STRIP : ESP_LINE_STRIP;
	}
}
